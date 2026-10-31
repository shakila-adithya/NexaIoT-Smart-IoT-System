"""Explicit host-side Arduino gateway enrollment and activation client."""

import argparse
import getpass
import hashlib
import json
import os
import ssl
import sys
import time
from datetime import datetime, timezone
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

import paho.mqtt.client as mqtt

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "simulator"))
from device_delivery import request_credentials, store_credentials
from device_trust import activation_signature, load_or_create_private_key, registration_payload
from bridge import load_manifest

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_KEY_DIR = ROOT / "tools/local-development/.runtime/arduino-device-keys"
DEFAULT_CREDENTIAL_DIR = ROOT / "tools/local-development/.runtime/arduino-device-credentials"
TERMINAL_OPERATION_STATES = {"FAILED", "CANCELLED", "RECONCILIATION_REQUIRED"}


def wait_for_operation_completion(fetch_status, timeout=30, sleep=time.sleep, clock=time.monotonic):
    """Poll an authenticated operation until backend verification is complete."""
    deadline = clock() + timeout
    while clock() < deadline:
        current = fetch_status()
        if current.get("state") == "COMPLETED" and current.get("deviceVerified") is True:
            return current
        if current.get("state") in TERMINAL_OPERATION_STATES:
            raise RuntimeError("Backend activation did not complete.")
        sleep(1)
    raise TimeoutError("Timed out waiting for backend activation completion.")


def api_call(base, path, method="GET", body=None, token=None, context=None):
    data = None if body is None else json.dumps(body, separators=(",", ":")).encode()
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(base.rstrip("/") + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, context=context, timeout=15) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        return error.code, None


def require(status, expected, label):
    if status not in expected:
        raise RuntimeError(f"{label} failed with HTTP {status}.")


def ensure_new_paths(key_path, credential_path):
    if Path(key_path).exists():
        raise FileExistsError("The gateway key already exists; review it instead of overwriting it.")
    if Path(credential_path).exists():
        raise FileExistsError("The gateway credential file already exists; review it instead of overwriting it.")


def validate_activation_challenge(message, device_key, operation_id, client_id, fingerprint,
                                  topic=None, retained=False, now=None):
    if retained or topic != f"nexaiot/devices/{device_key}/activation-challenges":
        raise ValueError("Activation challenge topic is invalid.")
    if not isinstance(message, dict) or message.get("protocol") != "NexaIoT/Activation/v1":
        raise ValueError("Activation challenge protocol is invalid.")
    if message.get("deviceKey") != device_key or message.get("operationId") != operation_id:
        raise ValueError("Activation challenge identity does not match the selected device.")
    if message.get("mqttClientId") != client_id or message.get("trustAnchorFingerprint") != fingerprint:
        raise ValueError("Activation challenge credentials do not match the selected device.")
    if not isinstance(message.get("challenge"), str) or not isinstance(message.get("expiresAt"), str):
        raise ValueError("Activation challenge is incomplete.")
    try:
        expires_at = datetime.fromisoformat(message["expiresAt"].replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError("Activation challenge expiry is invalid.") from error
    if expires_at.tzinfo is None or expires_at <= (now or datetime.now(timezone.utc)):
        raise ValueError("Activation challenge has expired.")
    return message


def activation_response(private_key, challenge, device_key, operation_id, owner_email, client_id, fingerprint,
                        topic=None, retained=False, now=None):
    validate_activation_challenge(challenge, device_key, operation_id, client_id, fingerprint,
                                  topic, retained, now)
    return {
        "operationId": operation_id,
        "mqttClientId": client_id,
        "challenge": challenge["challenge"],
        "expiresAt": challenge["expiresAt"],
        "signature": activation_signature(private_key, device_key, operation_id, owner_email,
                                            client_id, fingerprint, challenge["challenge"],
                                            challenge["expiresAt"]),
    }


def run(args, dependencies=None):
    """Run enrollment and activation; dependencies is an offline-test seam."""
    dependencies = dependencies or {}
    call_api = dependencies.get("api_call", api_call)
    mqtt_factory = dependencies.get("mqtt_client_factory", mqtt.Client)
    sleep = dependencies.get("sleep", time.sleep)
    monotonic = dependencies.get("monotonic", time.monotonic)
    key_loader = dependencies.get("load_or_create_private_key", load_or_create_private_key)
    credential_requester = dependencies.get("request_credentials", request_credentials)
    credential_store = dependencies.get("store_credentials", store_credentials)
    ca = Path(args.ca).expanduser()
    if not ca.is_file():
        raise FileNotFoundError("The configured CA certificate was not found.")
    key_path = Path(args.key_dir) / f"{args.device_key}.pem"
    credential_path = Path(args.credential_dir) / f"{args.device_key}.json"
    ensure_new_paths(key_path, credential_path)
    context = ssl.create_default_context(cafile=str(ca))
    email = args.owner_email or input("Owner email: ").strip().lower()
    password = getpass.getpass("Owner password: ")
    status, login = call_api(args.backend, "/api/auth/login", "POST",
                             {"email": email, "password": password}, context=context)
    require(status, {200}, "Owner login")
    token = login.get("token")
    del password
    status, device = call_api(args.backend, f"/api/devices/{args.device_id}", token=token, context=context)
    require(status, {200}, "Device lookup")
    if device.get("deviceKey") != args.device_key:
        raise RuntimeError("Device ID and device key do not match.")
    local_manifest = load_manifest(args.manifest)
    if json.dumps(local_manifest, sort_keys=True) != json.dumps(device.get("capabilityManifest"), sort_keys=True):
        raise RuntimeError("The local manifest does not match the persisted device manifest.")

    private_key = key_loader(key_path)
    status, pairing = call_api(args.backend, f"/api/devices/{args.device_id}/pairing-sessions",
                               "POST", {}, token, context)
    require(status, {201}, "Pairing session")
    status, _ = call_api(args.backend, f"/api/provisioning/pairing-sessions/{pairing['sessionId']}/claim",
                         "POST", {"pairingToken": pairing["pairingToken"],
                                  "hardwareIdentityHash": hashlib.sha256(
                                      f"nexaiot-arduino-gateway:{args.device_key}".encode()).hexdigest()},
                         context=context)
    require(status, {200}, "Pairing claim")
    status, operation = call_api(args.backend, f"/api/devices/{args.device_id}/enrollment-operations",
                                 "POST", {"pairingSessionId": pairing["sessionId"]}, token, context)
    require(status, {201}, "Enrollment start")
    operation_id = operation["operationId"]
    deadline = monotonic() + 180
    while monotonic() < deadline:
        status, current = call_api(args.backend, f"/api/devices/{args.device_id}/enrollment-operations/{operation_id}",
                                   token=token, context=context)
        require(status, {200}, "Enrollment status")
        if current.get("state") == "CREDENTIAL_DELIVERY_PENDING" and current.get("brokerIdentityVerified"):
            break
        if current.get("state") in {"FAILED", "CANCELLED", "RECONCILIATION_REQUIRED"}:
            raise RuntimeError("Enrollment stopped before credential delivery.")
        sleep(2)
    else:
        raise TimeoutError("Timed out waiting for credential delivery readiness.")

    status, challenge = call_api(args.backend, f"/api/devices/{args.device_id}/enrollment-operations/{operation_id}/trust-anchor/challenge",
                                 "POST", {}, token, context)
    require(status, {200}, "Trust-anchor challenge")
    registration = registration_payload(private_key, args.device_key, operation_id, email, challenge["challenge"])
    status, registered = call_api(args.backend, f"/api/devices/{args.device_id}/enrollment-operations/{operation_id}/trust-anchor",
                                  "POST", registration, token, context)
    require(status, {200}, "Trust-anchor registration")
    fingerprint = registered["trustAnchorFingerprint"]
    status, delivery = call_api(args.backend, f"/api/devices/{args.device_id}/enrollment-operations/{operation_id}/credential-delivery/challenge",
                                "POST", {}, token, context)
    require(status, {200}, "Credential-delivery challenge")
    credentials = credential_requester(args.backend, args.device_id, args.device_key, operation_id, email,
                                      delivery["trustAnchorFingerprint"], delivery["challenge"],
                                      delivery["expiresAt"], private_key, str(ca))
    expected_client_id = f"nexaiot-device-{args.device_key}"
    if (credentials.get("operationId") != operation_id
            or credentials.get("username") != expected_client_id
            or credentials.get("mqttClientId") != expected_client_id
            or not credentials.get("password")):
        raise RuntimeError("Credential delivery response did not match the enrollment operation.")
    credential_store(credential_path, credentials)

    parsed = urlparse(args.mqtt_url)
    if parsed.scheme != "ssl" or not parsed.hostname or not parsed.port:
        raise ValueError("MQTT URL must use ssl://host:port.")
    client_id = f"nexaiot-device-{args.device_key}"
    client = mqtt_factory(client_id=client_id)
    client.username_pw_set(credentials["username"], credentials["password"])
    client.tls_set(ca_certs=str(ca), cert_reqs=ssl.CERT_REQUIRED)
    client.tls_insecure_set(False)
    received = []
    challenge_topic = f"nexaiot/devices/{args.device_key}/activation-challenges"
    response_topic = f"nexaiot/devices/{args.device_key}/activation-responses"

    def on_message(_client, _userdata, message):
        try:
            challenge_message = json.loads(message.payload.decode())
            response = activation_response(private_key, challenge_message, args.device_key, operation_id,
                                           email, client_id, fingerprint, message.topic,
                                           bool(getattr(message, "retain", False)))
            result = _client.publish(response_topic, json.dumps(response, separators=(",", ":")), qos=1)
            if result.rc != mqtt.MQTT_ERR_SUCCESS:
                raise RuntimeError("Activation response publication was rejected.")
            received.append(True)
        except (ValueError, KeyError, json.JSONDecodeError):
            return

    client.on_message = on_message
    client.connect(parsed.hostname, parsed.port, keepalive=60)
    client.subscribe(challenge_topic, qos=1)
    client.loop_start()
    try:
        status, _ = call_api(args.backend, f"/api/devices/{args.device_id}/enrollment-operations/{operation_id}/activation/challenge",
                             "POST", {}, token, context)
        require(status, {200}, "Activation challenge")
        deadline = time.monotonic() + 90
        while not received and time.monotonic() < deadline:
            sleep(1)
        if not received:
            raise TimeoutError("Timed out waiting for activation challenge.")
        wait_for_operation_completion(
            lambda: _fetch_operation_status_with(call_api, args, operation_id, token, context),
            timeout=30, sleep=sleep, clock=monotonic,
        )
        print("Activation completed and was verified by the backend.")
        return
    finally:
        client.loop_stop()
        client.disconnect()


def _fetch_operation_status(args, operation_id, token, context):
    status, current = api_call(args.backend,
                               f"/api/devices/{args.device_id}/enrollment-operations/{operation_id}",
                               token=token, context=context)
    require(status, {200}, "Activation status")
    return current


def _fetch_operation_status_with(call_api_function, args, operation_id, token, context):
    status, current = call_api_function(
        args.backend, f"/api/devices/{args.device_id}/enrollment-operations/{operation_id}",
        token=token, context=context)
    require(status, {200}, "Activation status")
    return current


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", default=os.getenv("NEXAIOT_BACKEND_URL", "https://127.0.0.1:8443"))
    parser.add_argument("--ca", default=os.getenv("NEXAIOT_CA_CERTIFICATE", ""))
    parser.add_argument("--mqtt-url", default=os.getenv("NEXAIOT_MQTT_URL", "ssl://127.0.0.1:8885"))
    parser.add_argument("--device-id", required=True)
    parser.add_argument("--device-key", required=True)
    parser.add_argument("--manifest", default=os.getenv("NEXAIOT_MANIFEST", str(ROOT / "tools/arduino-serial-bridge/arduino-test-counter-manifest.json")))
    parser.add_argument("--owner-email", default=os.getenv("NEXAIOT_OWNER_EMAIL"))
    parser.add_argument("--key-dir", default=str(DEFAULT_KEY_DIR))
    parser.add_argument("--credential-dir", default=str(DEFAULT_CREDENTIAL_DIR))
    args = parser.parse_args()
    if not args.ca:
        parser.error("--ca or NEXAIOT_CA_CERTIFICATE is required")
    run(args)


if __name__ == "__main__":
    main()
