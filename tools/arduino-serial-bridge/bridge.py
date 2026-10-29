"""Forward newline-delimited Arduino telemetry to NexaIoT over MQTT/TLS."""

import json
import os
import ssl
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

import paho.mqtt.client as mqtt


DEFAULT_SERIAL_PORT = "COM6"
DEFAULT_BAUDRATE = 9600
DEFAULT_BROKER_URL = "ssl://127.0.0.1:8885"
DEFAULT_CREDENTIALS_DIR = Path(__file__).resolve().parents[2] / "simulator" / ".device-credentials"
DEFAULT_MANIFEST = Path(__file__).resolve().parent / "arduino-test-counter-manifest.json"
MAX_CONNECT_ATTEMPTS = 5


def parse_serial_line(line):
    if isinstance(line, bytes):
        line = line.decode("utf-8", errors="strict")
    if not isinstance(line, str):
        raise ValueError("Serial input must be text.")
    value = json.loads(line)
    if not isinstance(value, dict) or isinstance(value.get("testCounter"), bool):
        raise ValueError("Serial JSON must be an object with numeric testCounter.")
    counter = value.get("testCounter")
    if not isinstance(counter, (int, float)):
        raise ValueError("testCounter must be numeric.")
    if not isinstance(counter, int) and not counter.is_integer() and not (counter == counter and abs(counter) != float("inf")):
        raise ValueError("testCounter must be finite.")
    return counter


def telemetry_payload(counter, now=None):
    if isinstance(counter, bool) or not isinstance(counter, (int, float)):
        raise ValueError("testCounter must be numeric.")
    if isinstance(counter, float) and (counter != counter or abs(counter) == float("inf")):
        raise ValueError("testCounter must be finite.")
    captured = now or datetime.now(timezone.utc)
    return {"metrics": {"testCounter": counter}, "timestamp": captured.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")}


def telemetry_topic(device_key):
    if not isinstance(device_key, str) or not device_key or any(char not in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-" for char in device_key):
        raise ValueError("Invalid device key.")
    return f"nexaiot/devices/{device_key}/telemetry"


def load_manifest(path):
    root = json.loads(Path(path).read_text(encoding="utf-8"))
    manifest = root.get("capabilityManifest", root)
    capabilities = manifest.get("capabilities") if isinstance(manifest, dict) else None
    matches = [item for item in capabilities or [] if isinstance(item, dict) and item.get("key") == "testCounter"]
    if len(matches) != 1 or matches[0].get("category") != "MEASUREMENT" or matches[0].get("dataType") != "NUMBER":
        raise ValueError("Manifest must declare testCounter as a numeric measurement.")
    return manifest


def load_credentials(device_key, directory=DEFAULT_CREDENTIALS_DIR):
    path = Path(directory) / f"{device_key}.json"
    credentials = json.loads(path.read_text(encoding="utf-8"))
    expected = f"nexaiot-device-{device_key}"
    if credentials.get("username") != expected or credentials.get("mqttClientId") != expected or not credentials.get("password"):
        raise ValueError("Stored credentials do not match the selected device.")
    return credentials


def mqtt_client(device_key, broker_url, ca_certificate, credentials, client_factory=mqtt.Client):
    parsed = urlparse(broker_url)
    if parsed.scheme.lower() != "ssl" or not parsed.hostname or not parsed.port:
        raise ValueError("Broker URL must use ssl://host:port.")
    client = client_factory(client_id=f"nexaiot-device-{device_key}")
    client.username_pw_set(credentials["username"], credentials["password"])
    client.tls_set(ca_certs=str(ca_certificate), cert_reqs=ssl.CERT_REQUIRED)
    client.tls_insecure_set(False)
    client.reconnect_delay_set(min_delay=1, max_delay=30)
    return client, parsed.hostname, parsed.port


def connect_with_retry(client, host, port, attempts=MAX_CONNECT_ATTEMPTS):
    for attempt in range(attempts):
        try:
            result = client.connect(host, port, keepalive=60)
            if result == mqtt.MQTT_ERR_SUCCESS:
                return
            permanent = {mqtt.MQTT_ERR_AUTH, mqtt.MQTT_ERR_ACL_DENIED,
                         mqtt.MQTT_ERR_TLS, mqtt.MQTT_ERR_INVAL,
                         mqtt.MQTT_ERR_PROTOCOL}
            if result in permanent:
                raise RuntimeError(f"MQTT connection was rejected (code {result}).")
            if attempt + 1 == attempts:
                raise RuntimeError(f"MQTT connection failed after bounded retries (code {result}).")
            time.sleep(2 ** attempt)
        except (ssl.SSLError, ValueError):
            raise
        except OSError as error:
            if attempt + 1 == attempts:
                raise RuntimeError("MQTT connection failed after bounded retries.") from error
            time.sleep(2 ** attempt)


def publish_telemetry(client, topic, payload, timeout=10):
    result = client.publish(topic, json.dumps(payload, separators=(",", ":")), qos=1)
    if result.rc != mqtt.MQTT_ERR_SUCCESS:
        raise RuntimeError("MQTT telemetry publication was rejected.")
    if hasattr(result, "wait_for_publish"):
        result.wait_for_publish(timeout=timeout)
        if hasattr(result, "is_published") and not result.is_published():
            raise TimeoutError("MQTT telemetry publication timed out.")
    return "acknowledged"


def process_serial_line(client, topic, raw, now=None, publish=publish_telemetry):
    """Validate one serial record and publish it; invalid records never publish."""
    payload = telemetry_payload(parse_serial_line(raw), now)
    return publish(client, topic, payload)


def run(serial_port, baudrate, device_key, broker_url, ca_certificate, manifest_path,
        credentials_dir=DEFAULT_CREDENTIALS_DIR, serial_factory=None, stop_event=None):
    load_manifest(manifest_path)
    credentials = load_credentials(device_key, credentials_dir)
    client, host, port = mqtt_client(device_key, broker_url, ca_certificate, credentials)
    serial_factory = serial_factory or __import__("serial").Serial
    serial = None
    topic = telemetry_topic(device_key)
    connect_with_retry(client, host, port)
    client.loop_start()
    try:
        while True:
            if stop_event is not None and stop_event.is_set():
                break
            if serial is None:
                try:
                    serial = serial_factory(serial_port, baudrate=baudrate, timeout=1)
                    print("Serial connected.", flush=True)
                except OSError as error:
                    print(f"Serial connection unavailable; retrying: {error}", flush=True)
                    time.sleep(1)
                    continue
            try:
                raw = serial.readline()
            except OSError as error:
                print(f"Serial connection interrupted; retrying: {error}", flush=True)
                serial.close()
                serial = None
                continue
            if stop_event is not None and stop_event.is_set():
                break
            if not raw:
                continue
            try:
                process_serial_line(client, topic, raw)
                print("Telemetry acknowledged.", flush=True)
            except (ValueError, UnicodeError, json.JSONDecodeError) as error:
                print(f"Ignored serial record: {error}", flush=True)
            except (OSError, RuntimeError, TimeoutError) as error:
                print(f"Telemetry publication failed: {error}", flush=True)
    finally:
        try:
            client.loop_stop()
        except Exception as error:
            print(f"MQTT loop cleanup failed: {error}", flush=True)
        try:
            client.disconnect()
        except Exception as error:
            print(f"MQTT disconnect cleanup failed: {error}", flush=True)
        if serial is not None:
            try:
                serial.close()
            except Exception as error:
                print(f"Serial cleanup failed: {error}", flush=True)


def main():
    run(
        os.getenv("NEXAIOT_ARDUINO_SERIAL_PORT", DEFAULT_SERIAL_PORT),
        int(os.getenv("NEXAIOT_ARDUINO_BAUDRATE", str(DEFAULT_BAUDRATE))),
        os.environ["NEXAIOT_DEVICE_KEY"],
        os.getenv("NEXAIOT_MQTT_URL", DEFAULT_BROKER_URL),
        os.environ["NEXAIOT_CA_CERTIFICATE"],
        os.getenv("NEXAIOT_MANIFEST", str(DEFAULT_MANIFEST)),
        credentials_dir=os.getenv("NEXAIOT_CREDENTIALS_DIR", str(DEFAULT_CREDENTIALS_DIR)),
    )


if __name__ == "__main__":
    main()
