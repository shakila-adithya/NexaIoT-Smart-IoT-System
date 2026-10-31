import json
import os
import sys
from pathlib import Path
from urllib.parse import urlparse

import msvcrt

from device_simulator import DeviceSimulator


DEFAULT_DEVICE_KEY = "NEXA-69A5B9235056"
DEFAULT_BROKER_URL = "ssl://127.0.0.1:8885"
DEFAULT_CA = ""
GREENHOUSE_MANIFEST = {
    "version": 1,
    "source": "TEMPLATE",
    "templateId": "temperature-humidity-sensor",
    "capabilities": [
        {"key": "temperature", "category": "MEASUREMENT", "dataType": "NUMBER", "readOnly": True},
        {"key": "humidity", "category": "MEASUREMENT", "dataType": "NUMBER", "readOnly": True},
    ],
}


def required_url(value):
    parsed = urlparse(value)
    if parsed.scheme.lower() != "ssl" or not parsed.hostname or parsed.port is None:
        raise ValueError("MQTT URL must use ssl://host:port.")
    return value


def load_credentials(device_key):
    path = Path(__file__).resolve().parent / ".device-credentials" / f"{device_key}.json"
    if not path.is_file():
        raise FileNotFoundError("Stored device credentials were not found. Complete onboarding first.")
    credentials = json.loads(path.read_text(encoding="utf-8"))
    expected_client_id = f"nexaiot-device-{device_key}"
    if credentials.get("username") != expected_client_id \
            or credentials.get("mqttClientId") != expected_client_id \
            or not credentials.get("password"):
        raise ValueError("Stored device credentials do not match the selected device.")
    return credentials


def load_manifest(device_key):
    configured = os.getenv("NEXAIOT_MANIFEST", "").strip()
    if configured:
        path = Path(configured).expanduser()
        if not path.is_file():
            raise FileNotFoundError("The configured device manifest was not found.")
        value = json.loads(path.read_text(encoding="utf-8"))
        manifest = value.get("capabilityManifest", value) if isinstance(value, dict) else None
        if not isinstance(manifest, dict) or not isinstance(manifest.get("capabilities"), list):
            raise ValueError("The configured device manifest is invalid.")
        return manifest
    if device_key == DEFAULT_DEVICE_KEY:
        return GREENHOUSE_MANIFEST
    raise ValueError("Set NEXAIOT_MANIFEST for this device before resuming telemetry.")


def acquire_lock(device_key):
    path = Path(__file__).resolve().parent / ".device-credentials" / f"{device_key}.resume.lock"
    handle = path.open("a+")
    try:
        handle.seek(0)
        handle.write("0")
        handle.flush()
        handle.seek(0)
        msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
    except OSError as error:
        handle.close()
        raise RuntimeError("Another local simulator is already running for this device.") from error
    return handle


def main():
    device_key = os.getenv("NEXAIOT_DEVICE_KEY", DEFAULT_DEVICE_KEY).strip()
    broker_url = required_url(os.getenv("NEXAIOT_MQTT_URL", DEFAULT_BROKER_URL).strip())
    ca_certificate = os.getenv("NEXAIOT_CA_CERTIFICATE", DEFAULT_CA).strip()
    if not ca_certificate or not Path(ca_certificate).expanduser().is_file():
        raise FileNotFoundError("Set NEXAIOT_CA_CERTIFICATE to the local trusted CA certificate.")
    credentials = load_credentials(device_key)
    lock = acquire_lock(device_key)
    simulator = DeviceSimulator(
        device_key,
        broker_url=broker_url,
        username=credentials["username"],
        password=credentials["password"],
        tls_required=True,
        ca_certificate=ca_certificate,
        manifest=load_manifest(device_key),
        telemetry_interval=float(os.getenv("NEXAIOT_TELEMETRY_INTERVAL", "5")),
    )
    print(f"Resuming activated device {device_key} over MQTT TLS.", flush=True)
    try:
        simulator.run()
    finally:
        lock.close()
        del credentials
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("Simulator stopped safely.", flush=True)
        raise SystemExit(130)
    except Exception as error:
        print(f"Resume failed: {error}", file=sys.stderr)
        raise SystemExit(1)
