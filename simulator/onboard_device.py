import getpass
import hashlib
import json
import os
import ssl
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

from device_delivery import request_credentials, store_credentials
from device_simulator import DeviceSimulator
from device_trust import load_or_create_private_key, registration_payload


DEFAULT_BACKEND_URL = os.getenv("NEXAIOT_BACKEND_URL", "")
DEFAULT_MQTT_URL = os.getenv("NEXAIOT_MQTT_URL", "")
DEFAULT_CA = os.getenv("NEXAIOT_CA_CERTIFICATE", "")
POLL_INTERVAL = 2


def ask(label, default=""):
    suffix = f" [{default}]" if default else ""
    value = input(f"{label}{suffix}: ").strip()
    return value or default


def read_secret(prompt):
    if sys.stdin.isatty():
        return getpass.getpass(prompt)
    print(prompt, end="", flush=True)
    return sys.stdin.readline().rstrip("\r\n")


def api_call(base_url, path, method="GET", body=None, token=None, context=None):
    data = None if body is None else json.dumps(body, separators=(",", ":")).encode("utf-8")
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=data,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, context=context, timeout=15) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        return error.code, None


def require(status, expected, label):
    if status not in expected:
        raise RuntimeError(f"{label} failed with HTTP {status}.")


def require_https(value, label):
    parsed = urlparse(value)
    if parsed.scheme.lower() != "https" or not parsed.hostname or parsed.port is None:
        raise ValueError(f"{label} must be an HTTPS URL with an explicit port.")
    return parsed


def require_mqtt_tls(value):
    parsed = urlparse(value)
    if parsed.scheme.lower() != "ssl" or not parsed.hostname or parsed.port is None:
        raise ValueError("MQTT URL must use ssl://host:port.")
    return parsed


def select_device(devices, selector):
    if not isinstance(devices, list) or not devices:
        raise RuntimeError("The authenticated owner has no devices.")
    print("Available devices:")
    for device in devices:
        print(f"- {device.get('name', 'Unnamed')} | serial={device.get('deviceKey', 'unknown')} | id={device.get('id', 'unknown')}")
    value = selector.strip().casefold()
    matches = [
        device for device in devices
        if value in {
            str(device.get("id", "")).casefold(),
            str(device.get("deviceKey", "")).casefold(),
        }
    ]
    if len(matches) != 1:
        raise RuntimeError("Select exactly one device using its serial or device ID.")
    return matches[0]


def wait_for_operation(base_url, context, token, device_id, operation_id):
    deadline = time.monotonic() + 180
    last_state = None
    while time.monotonic() < deadline:
        status, operation = api_call(
            base_url,
            f"/api/devices/{device_id}/enrollment-operations/{operation_id}",
            token=token,
            context=context,
        )
        require(status, {200}, "Enrollment status")
        state = operation.get("state") if isinstance(operation, dict) else None
        if state != last_state:
            print(f"Enrollment state: {state or 'unknown'}", flush=True)
            last_state = state
        if state == "CREDENTIAL_DELIVERY_PENDING" and operation.get("brokerIdentityVerified"):
            return operation
        if state in {"FAILED", "CANCELLED", "RECONCILIATION_REQUIRED"}:
            raise RuntimeError(f"Enrollment stopped in state {state}.")
        time.sleep(POLL_INTERVAL)
    raise TimeoutError("Timed out waiting for broker enrollment readiness.")


def wait_for_connection(simulator):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if simulator.connected:
            return
        time.sleep(0.25)
    raise TimeoutError("Timed out waiting for MQTT TLS authentication.")


def wait_for_activation(base_url, context, token, device_id, operation_id, activation_event):
    if not activation_event.wait(90):
        raise TimeoutError("Timed out waiting for the device activation challenge.")
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        status, operation = api_call(
            base_url,
            f"/api/devices/{device_id}/enrollment-operations/{operation_id}",
            token=token,
            context=context,
        )
        require(status, {200}, "Activation status")
        if operation.get("state") == "COMPLETED" and operation.get("deviceVerified"):
            return
        time.sleep(1)
    raise TimeoutError("The signed activation response was not confirmed by the backend.")


def main():
    print("NexaIoT secure device simulator onboarding")
    base_url = ask("Backend HTTPS URL", DEFAULT_BACKEND_URL)
    broker_url = ask("MQTT TLS URL", DEFAULT_MQTT_URL)
    ca_certificate = ask("CA certificate path", DEFAULT_CA)
    email = input("Owner email: ").strip().lower()
    password = read_secret("Owner password: ")
    if not email or not password:
        raise ValueError("Owner email and password are required.")

    require_https(base_url, "Backend URL")
    broker = require_mqtt_tls(broker_url)
    ca_path = Path(ca_certificate).expanduser()
    if not ca_path.is_file():
        raise FileNotFoundError("The CA certificate file was not found.")
    context = ssl.create_default_context(cafile=str(ca_path))

    status, login = api_call(base_url, "/api/auth/login", "POST", {
        "email": email,
        "password": password,
    }, context=context)
    require(status, {200}, "Owner login")
    token = login.get("token") if isinstance(login, dict) else None
    if not token:
        raise RuntimeError("Owner login did not return a session token.")
    del password
    print("Owner authenticated.")

    status, devices = api_call(base_url, "/api/devices", token=token, context=context)
    require(status, {200}, "Device lookup")
    selector = ask("Device serial or ID")
    device = select_device(devices, selector)
    device_id = device.get("id")
    device_key = device.get("deviceKey")
    manifest = device.get("capabilityManifest")
    if not device_id or not device_key or not isinstance(manifest, dict):
        raise RuntimeError("The selected device does not expose a complete capability manifest.")
    print(f"Selected {device.get('name', 'device')} ({device_key}).")

    status, metadata = api_call(
        base_url, f"/api/devices/{device_id}/provisioning", token=token, context=context)
    require(status, {200}, "Provisioning status")
    if (not isinstance(metadata, dict)
            or metadata.get("deviceId") != device_id
            or metadata.get("deviceKey") != device_key):
        raise RuntimeError("The provisioning metadata does not belong to the selected device.")
    session_id = metadata.get("activePairingSessionId") if isinstance(metadata, dict) else None
    if not session_id:
        raise RuntimeError("No active owner pairing session was found for this device.")

    status, session = api_call(
        base_url,
        f"/api/devices/{device_id}/pairing-sessions/{session_id}",
        token=token,
        context=context,
    )
    require(status, {200}, "Pairing session lookup")
    if (session.get("deviceId") != device_id
            or session.get("status") != "ACTIVE"):
        raise RuntimeError("The active pairing session does not belong to the selected device.")
    pairing_token = read_secret("Fresh pairing token: ")
    if not pairing_token:
        raise ValueError("Fresh pairing token is required.")

    key_path = Path(__file__).resolve().parent / ".device-keys" / f"{device_key}.pem"
    credentials_path = Path(__file__).resolve().parent / ".device-credentials" / f"{device_key}.json"
    private_key = load_or_create_private_key(key_path)
    hardware_hash = hashlib.sha256(f"nexaiot-python:{device_key}".encode()).hexdigest()

    status, claim = api_call(
        base_url,
        f"/api/provisioning/pairing-sessions/{session_id}/claim",
        "POST",
        {"pairingToken": pairing_token, "hardwareIdentityHash": hardware_hash},
        context=context,
    )
    require(status, {200}, "Pairing claim")
    if not isinstance(claim, dict) or claim.get("status") != "CLAIMED":
        raise RuntimeError("The pairing token was not accepted.")
    del pairing_token
    print("Pairing claimed.")

    status, operation = api_call(
        base_url,
        f"/api/devices/{device_id}/enrollment-operations",
        "POST",
        {"pairingSessionId": session_id},
        token,
        context,
    )
    require(status, {201}, "Enrollment start")
    operation_id = operation.get("operationId")
    if not operation_id:
        raise RuntimeError("Enrollment did not return an operation identifier.")
    print(f"Enrollment operation started: {operation_id}.")
    operation = wait_for_operation(base_url, context, token, device_id, operation_id)

    status, challenge = api_call(
        base_url,
        f"/api/devices/{device_id}/enrollment-operations/{operation_id}/trust-anchor/challenge",
        "POST",
        {},
        token,
        context,
    )
    require(status, {200}, "Trust-anchor challenge")
    registration = registration_payload(
        private_key, device_key, operation_id, email, challenge["challenge"])
    status, _ = api_call(
        base_url,
        f"/api/devices/{device_id}/enrollment-operations/{operation_id}/trust-anchor",
        "POST",
        registration,
        token,
        context,
    )
    require(status, {200}, "Trust-anchor registration")
    print("Trust anchor registered; private key remains local.")

    status, delivery_challenge = api_call(
        base_url,
        f"/api/devices/{device_id}/enrollment-operations/{operation_id}/credential-delivery/challenge",
        "POST",
        {},
        token,
        context,
    )
    require(status, {200}, "Credential-delivery challenge")
    credentials = request_credentials(
        base_url, device_id, device_key, operation_id, email,
        delivery_challenge["trustAnchorFingerprint"], delivery_challenge["challenge"],
        delivery_challenge["expiresAt"], private_key, str(ca_path))
    store_credentials(credentials_path, credentials)
    print("One-time MQTT credentials delivered and stored locally.")

    activation_event = threading.Event()
    simulator = DeviceSimulator(
        device_key,
        broker_url=broker_url,
        username=credentials["username"],
        password=credentials["password"],
        tls_required=True,
        ca_certificate=str(ca_path),
        manifest=manifest,
        activation_context={
            "operation_id": operation_id,
            "owner_email": email,
            "fingerprint": delivery_challenge["trustAnchorFingerprint"],
            "private_key": private_key,
        },
        activation_callback=activation_event.set,
    )
    simulator.start()
    try:
        wait_for_connection(simulator)
        print(f"MQTT TLS connected to {broker.hostname}:{broker.port}.")
        input("Click 'Start device activation' in NexaIoT, then press Enter here: ")
        print("Waiting for signed activation confirmation.")
        wait_for_activation(base_url, context, token, device_id, operation_id, activation_event)
        print("Device activation confirmed.")
        print("Telemetry is running; use NexaIoT device controls to test commands. Press Enter to stop.")
        input()
    finally:
        simulator.stop()
        del credentials
        print("Simulator stopped safely.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("Onboarding cancelled.", file=sys.stderr)
        raise SystemExit(130)
    except Exception as error:
        print(f"Onboarding failed: {error}", file=sys.stderr)
        raise SystemExit(1)
