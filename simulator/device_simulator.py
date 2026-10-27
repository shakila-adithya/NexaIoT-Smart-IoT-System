import json
import os
import random
import re
import threading
import time
from collections import OrderedDict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

import paho.mqtt.client as mqtt

from device_trust import activation_signature


DEVICE_KEY_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]*$")
MAX_PAYLOAD_BYTES = 8 * 1024
DEFAULT_TELEMETRY_INTERVAL = 5.0
DEFAULT_RECONNECT_MIN = 1
DEFAULT_RECONNECT_MAX = 30


DEFAULT_MANIFEST = {
    "version": 1,
    "source": "TEMPLATE",
    "templateId": "smart-fan",
    "capabilities": [
        {
            "key": "temperature",
            "category": "MEASUREMENT",
            "dataType": "NUMBER",
            "readOnly": True,
        },
        {
            "key": "power",
            "category": "CONTROL",
            "dataType": "BOOLEAN",
            "readOnly": False,
        },
    ],
}


def validate_device_key(device_key):
    if not isinstance(device_key, str) or not DEVICE_KEY_PATTERN.fullmatch(device_key):
        raise ValueError("Device key must match [A-Za-z0-9][A-Za-z0-9_-]*.")
    return device_key


def load_manifest(path=None):
    if not path:
        return json.loads(json.dumps(DEFAULT_MANIFEST))
    root = json.loads(Path(path).read_text(encoding="utf-8"))
    manifest = root.get("capabilityManifest", root)
    if not isinstance(manifest, dict) or not isinstance(manifest.get("capabilities"), list):
        raise ValueError("Capability manifest is invalid.")
    return manifest


def _capabilities(manifest):
    return {
        item.get("key"): item
        for item in manifest.get("capabilities", [])
        if isinstance(item, dict) and isinstance(item.get("key"), str)
    }


def _is_number_capability(capability):
    return (
        capability.get("category") == "MEASUREMENT"
        and capability.get("dataType") == "NUMBER"
    )


def _parse_timestamp(value):
    if not isinstance(value, str) or not value.strip():
        raise ValueError("Command timestamp is required.")
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        raise ValueError("Command timestamp must include a timezone.")
    return parsed


@dataclass(frozen=True)
class CommandOutcome:
    status: str
    command_id: str | None
    correlation_id: str | None
    capability_key: str | None
    reason: str | None = None


class DeviceSimulator:
    def __init__(
        self,
        device_key,
        broker_url="tcp://localhost:1883",
        username="",
        password="",
        tls_required=False,
        ca_certificate=None,
        manifest=None,
        telemetry_interval=DEFAULT_TELEMETRY_INTERVAL,
        mqtt_client_factory=None,
        rng=None,
        activation_context=None,
        activation_callback=None,
    ):
        self.device_key = validate_device_key(device_key)
        self.client_id = f"nexaiot-device-{self.device_key}"
        parsed = urlparse(broker_url)
        if parsed.scheme.lower() not in {"tcp", "ssl"} or not parsed.hostname:
            raise ValueError("Broker URL must be tcp://host:port or ssl://host:port.")
        if tls_required and parsed.scheme.lower() != "ssl":
            raise ValueError("MQTT TLS is required for this deployment.")
        if bool(username) != bool(password):
            raise ValueError("MQTT username and password must be configured together.")
        if username and username != self.client_id:
            raise ValueError("MQTT username must match the device client identity.")
        if telemetry_interval <= 0:
            raise ValueError("Telemetry interval must be positive.")

        self.broker_url = broker_url
        self.broker_host = parsed.hostname
        self.broker_port = parsed.port or (8883 if parsed.scheme.lower() == "ssl" else 1883)
        self.broker_tls = parsed.scheme.lower() == "ssl"
        self.username = username
        self.password = password
        self.ca_certificate = ca_certificate
        self.manifest = manifest or load_manifest()
        self.capability_map = _capabilities(self.manifest)
        self.telemetry_interval = float(telemetry_interval)
        self.rng = rng or random.Random()
        self.state = {"power": False}
        for key, capability in self.capability_map.items():
            if capability.get("category") == "CONTROL" and key != "power":
                if capability.get("dataType") == "NUMBER":
                    constraints = capability.get("constraints") or {}
                    self.state[key] = constraints.get("min", 0)
                elif capability.get("dataType") == "ENUM":
                    options = capability.get("options") or []
                    if options:
                        self.state[key] = options[0].get("value")
        self.sensor_state = {
            "temperature": 27.5,
            "humidity": 60.0,
            "battery": 90,
            "rssi": -52,
        }
        self.connected = False
        self.stop_event = threading.Event()
        self.processed_commands = OrderedDict()
        self.mqtt_client_factory = mqtt_client_factory or self._create_mqtt_client
        self.client = self.mqtt_client_factory()
        self.activation_context = activation_context
        self.activation_callback = activation_callback
        if activation_context is not None:
            required = {"operation_id", "owner_email", "fingerprint", "private_key"}
            if not required.issubset(activation_context):
                raise ValueError("Activation context is incomplete.")
        self._configure_client()

    @property
    def topics(self):
        prefix = f"nexaiot/devices/{self.device_key}"
        return {
            "telemetry": f"{prefix}/telemetry",
            "state": f"{prefix}/state",
            "commands": f"{prefix}/commands",
            "command_results": f"{prefix}/command-results",
            "activation_challenges": f"{prefix}/activation-challenges",
            "activation_responses": f"{prefix}/activation-responses",
        }

    def _create_mqtt_client(self):
        client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=self.client_id,
        )
        client.reconnect_delay_set(DEFAULT_RECONNECT_MIN, DEFAULT_RECONNECT_MAX)
        return client

    def _configure_client(self):
        self.client.reconnect_delay_set(DEFAULT_RECONNECT_MIN, DEFAULT_RECONNECT_MAX)
        if self.username:
            self.client.username_pw_set(self.username, self.password)
        if self.broker_tls:
            self.client.tls_set(ca_certs=self.ca_certificate)
            self.client.tls_insecure_set(False)
        self.client.on_connect = self.on_connect
        self.client.on_disconnect = self.on_disconnect
        self.client.on_message = self.on_message

    def _log(self, message):
        print(f"[{self.device_key}] {message}", flush=True)

    def on_connect(self, client, userdata, flags, reason_code, properties=None):
        failed = reason_code.is_failure if hasattr(reason_code, "is_failure") else int(reason_code) != 0
        if failed:
            self.connected = False
            self._log(f"MQTT connection rejected: {reason_code}")
            return
        self.connected = True
        result, _ = client.subscribe(self.topics["commands"], qos=1)
        if result != mqtt.MQTT_ERR_SUCCESS:
            self._log(f"Command subscription failed: {result}")
            return
        if self.activation_context is not None:
            result, _ = client.subscribe(self.topics["activation_challenges"], qos=1)
            if result != mqtt.MQTT_ERR_SUCCESS:
                self._log("Activation subscription failed")
                return
        self._log(f"MQTT connected as {self.client_id}")
        self._log(f"Subscribed: {self.topics['commands']}")
        if self.activation_context is not None:
            self._log(f"Subscribed: {self.topics['activation_challenges']}")
        self.publish_state()

    def on_disconnect(self, client, userdata, *args):
        self.connected = False
        reason_code = args[-2] if len(args) >= 2 else args[-1] if args else "unknown"
        self._log(f"MQTT disconnected: {reason_code}; retry backoff is bounded")

    def _publish(self, topic, payload, retain=False):
        if not self.connected:
            self._log(f"Publish skipped while disconnected: {topic}")
            return False
        encoded = payload.encode("utf-8") if isinstance(payload, str) else payload
        if not encoded or len(encoded) > MAX_PAYLOAD_BYTES:
            self._log(f"Publish rejected due to payload size: {topic}")
            return False
        info = self.client.publish(topic, encoded, qos=1, retain=retain)
        if info.rc != mqtt.MQTT_ERR_SUCCESS:
            self._log(f"Publish failed: {topic}, rc={info.rc}")
            return False
        return True

    def publish_state(self):
        payload = json.dumps({
            key: value for key, value in self.state.items()
            if isinstance(self.capability_map.get(key), dict)
            and self.capability_map[key].get("category") in {"STATE", "CONTROL"}
        }, separators=(",", ":"), sort_keys=True)
        if payload == "{}":
            return False
        return self._publish(self.topics["state"], payload, retain=True)

    def publish_command_result(self, outcome):
        if not outcome.command_id:
            return False
        payload = {"commandId": outcome.command_id}
        if outcome.correlation_id:
            payload["correlationId"] = outcome.correlation_id
        if outcome.capability_key:
            payload["capabilityKey"] = outcome.capability_key
        payload["status"] = outcome.status
        if outcome.reason:
            payload["reason"] = outcome.reason
        payload["processedAt"] = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        return self._publish(
            self.topics["command_results"],
            json.dumps(payload, separators=(",", ":")),
        )

    def _reject_structured(self, payload, reason):
        command_id = payload.get("commandId") if isinstance(payload.get("commandId"), str) else None
        correlation_id = payload.get("correlationId") if isinstance(payload.get("correlationId"), str) else None
        capability_key = payload.get("capabilityKey") if isinstance(payload.get("capabilityKey"), str) else None
        return CommandOutcome("REJECTED", command_id, correlation_id, capability_key, reason)

    def _power_capability_supported(self):
        capability = self.capability_map.get("power")
        return (
            isinstance(capability, dict)
            and capability.get("category") == "CONTROL"
            and capability.get("dataType") == "BOOLEAN"
            and capability.get("readOnly") is not True
        )

    def process_command(self, payload):
        if isinstance(payload, (bytes, bytearray)):
            if len(payload) > MAX_PAYLOAD_BYTES:
                return CommandOutcome("REJECTED", None, None, None, "Payload is too large.")
            payload = payload.decode("utf-8")
        try:
            command = json.loads(payload)
        except (TypeError, UnicodeDecodeError, json.JSONDecodeError):
            return CommandOutcome("REJECTED", None, None, None, "Command payload is invalid.")
        if not isinstance(command, dict):
            return CommandOutcome("REJECTED", None, None, None, "Command payload must be an object.")

        if "commandId" not in command and "capabilityKey" not in command and "value" not in command:
            if not self._power_capability_supported():
                return CommandOutcome("REJECTED", None, None, "power", "Power capability is unavailable.")
            if type(command.get("power")) is not bool:
                return CommandOutcome("REJECTED", None, None, "power", "Power must be boolean.")
            self.state["power"] = command["power"]
            return CommandOutcome("ACCEPTED", None, None, "power")

        command_id = command.get("commandId")
        correlation_id = command.get("correlationId")
        if not isinstance(command_id, str) or not command_id.strip():
            return self._reject_structured(command, "Command identifier is required.")
        if correlation_id is not None and not isinstance(correlation_id, str):
            return self._reject_structured(command, "Correlation identifier must be text.")
        if command_id in self.processed_commands:
            previous = self.processed_commands[command_id]
            return CommandOutcome(
                "DUPLICATE", command_id, correlation_id, previous.capability_key,
                "Command was already processed.",
            )
        try:
            _parse_timestamp(command.get("issuedAt"))
        except ValueError as error:
            return self._reject_structured(command, str(error))
        capability_key = command.get("capabilityKey")
        capability = self.capability_map.get(capability_key)
        if not isinstance(capability, dict) or capability.get("category") != "CONTROL" or capability.get("readOnly") is True:
            return self._reject_structured(command, "Unsupported capability.")
        value = command.get("value")
        data_type = capability.get("dataType")
        if data_type == "BOOLEAN" and type(value) is not bool:
            return self._reject_structured(command, "Control value must be boolean.")
        if data_type == "NUMBER":
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                return self._reject_structured(command, "Control value must be numeric.")
            constraints = capability.get("constraints") or {}
            if constraints.get("min") is not None and value < constraints["min"]:
                return self._reject_structured(command, "Control value is below the minimum.")
            if constraints.get("max") is not None and value > constraints["max"]:
                return self._reject_structured(command, "Control value is above the maximum.")
            if constraints.get("step") and constraints.get("min") is not None:
                steps = (value - constraints["min"]) / constraints["step"]
                if abs(steps - round(steps)) > 1e-9:
                    return self._reject_structured(command, "Control value does not match the step.")
        if data_type in {"ENUM", "STRING"} and not isinstance(value, str):
            return self._reject_structured(command, "Control value must be text.")
        if data_type == "ENUM" and value not in {option.get("value") for option in capability.get("options", [])}:
            return self._reject_structured(command, "Control value is not allowed.")
        self.state[capability_key] = value
        outcome = CommandOutcome("ACCEPTED", command_id, correlation_id, capability_key)
        self.processed_commands[command_id] = outcome
        while len(self.processed_commands) > 1024:
            self.processed_commands.popitem(last=False)
        return outcome

    def on_message(self, client, userdata, message):
        if message.topic == self.topics["activation_challenges"]:
            self._handle_activation_challenge(message.payload)
            return
        if message.topic != self.topics["commands"]:
            self._log(f"Ignored message on unexpected topic: {message.topic}")
            return
        outcome = self.process_command(message.payload)
        if outcome.status == "ACCEPTED":
            self._log(f"Control confirmed: {outcome.capability_key}")
            self.publish_state()
        elif outcome.status == "DUPLICATE":
            self._log(f"Duplicate command ignored: {outcome.command_id}")
        else:
            self._log(f"Command rejected: {outcome.reason}")
        if outcome.command_id:
            self.publish_command_result(outcome)

    def _handle_activation_challenge(self, payload):
        if self.activation_context is None:
            return
        try:
            challenge = json.loads(payload.decode("utf-8"))
            if not isinstance(challenge, dict):
                return
            if challenge.get("operationId") != self.activation_context["operation_id"]:
                return
            if challenge.get("deviceKey") != self.device_key:
                return
            if challenge.get("mqttClientId") != self.client_id:
                return
            if challenge.get("trustAnchorFingerprint") != self.activation_context["fingerprint"]:
                return
            expires_at = challenge.get("expiresAt")
            value = challenge.get("challenge")
            if not isinstance(value, str) or not isinstance(expires_at, str):
                return
            signature = activation_signature(
                self.activation_context["private_key"], self.device_key,
                self.activation_context["operation_id"],
                self.activation_context["owner_email"], self.client_id,
                self.activation_context["fingerprint"], value, expires_at)
            response = {
                "operationId": self.activation_context["operation_id"],
                "mqttClientId": self.client_id,
                "challenge": value,
                "expiresAt": expires_at,
                "signature": signature,
            }
            if self._publish(self.topics["activation_responses"],
                             json.dumps(response, separators=(",", ":"))):
                if self.activation_callback is not None:
                    self.activation_callback()
                self._log("Activation proof published")
        except (ValueError, UnicodeDecodeError, KeyError):
            self._log("Activation challenge rejected")

    def _update_sensor_value(self, key, center, minimum, maximum, max_step, decimals=1):
        current = self.sensor_state[key]
        change = self.rng.uniform(-max_step, max_step) + (center - current) * 0.04
        self.sensor_state[key] = round(max(minimum, min(maximum, current + change)), decimals)
        return self.sensor_state[key]

    def create_telemetry(self):
        values = {}
        if "temperature" in self.capability_map and _is_number_capability(self.capability_map["temperature"]):
            values["temperature"] = self._update_sensor_value("temperature", 28.0, 24.0, 32.0, 0.25)
        if "humidity" in self.capability_map and _is_number_capability(self.capability_map["humidity"]):
            values["humidity"] = self._update_sensor_value("humidity", 60.0, 45.0, 75.0, 0.6)
        if "battery" in self.capability_map and _is_number_capability(self.capability_map["battery"]):
            if self.sensor_state["battery"] > 0 and self.rng.random() < 0.12:
                self.sensor_state["battery"] -= 1
            values["battery"] = self.sensor_state["battery"]
        if "rssi" in self.capability_map and _is_number_capability(self.capability_map["rssi"]):
            values["rssi"] = int(self._update_sensor_value("rssi", -52, -75, -40, 2, 0))
        if "motorSpeed" in self.capability_map and _is_number_capability(self.capability_map["motorSpeed"]):
            target = float(self.state.get("targetSpeed", 0) or 0)
            current = float(self.sensor_state.get("motorSpeed", 0))
            desired = target if self.state.get("power", False) else 0
            current += (desired - current) * 0.35
            self.sensor_state["motorSpeed"] = round(current, 1)
            values["motorSpeed"] = self.sensor_state["motorSpeed"]

        payload = {"metrics": {}, "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")}
        for key, value in values.items():
            constraints = self.capability_map[key].get("constraints", {})
            if isinstance(constraints, dict):
                if constraints.get("min") is not None:
                    value = max(value, constraints["min"])
                if constraints.get("max") is not None:
                    value = min(value, constraints["max"])
            if key in {"battery", "rssi"}:
                payload[key] = value
            else:
                payload["metrics"][key] = value
        return payload

    def publish_telemetry(self):
        telemetry = self.create_telemetry()
        payload = json.dumps(telemetry, separators=(",", ":"), allow_nan=False)
        if self._publish(self.topics["telemetry"], payload):
            self._log(f"Telemetry published: {payload}")
            return True
        return False

    def start(self):
        self.client.loop_start()
        self._log(f"Connecting to {self.broker_url} as {self.client_id}")
        self.client.connect_async(self.broker_host, self.broker_port, keepalive=60)

    def stop(self):
        self.stop_event.set()
        self.connected = False
        try:
            self.client.disconnect()
        finally:
            self.client.loop_stop()
        self._log("Simulator stopped")

    def run(self):
        self.start()
        next_telemetry = time.monotonic()
        try:
            while not self.stop_event.wait(0.25):
                if self.connected and time.monotonic() >= next_telemetry:
                    self.publish_telemetry()
                    next_telemetry = time.monotonic() + self.telemetry_interval
        except KeyboardInterrupt:
            self._log("Shutdown requested")
        finally:
            self.stop()


def _env_bool(name, default=False):
    return os.getenv(name, str(default)).lower() == "true"


def create_from_environment():
    return DeviceSimulator(
        device_key=os.getenv("NEXAIOT_DEVICE_KEY", "NEXA-5802182413C5"),
        broker_url=os.getenv("MQTT_BROKER_URL", "tcp://localhost:1883"),
        username=os.getenv("MQTT_USERNAME", ""),
        password=os.getenv("MQTT_PASSWORD", ""),
        tls_required=_env_bool("MQTT_TLS_REQUIRED"),
        ca_certificate=os.getenv("MQTT_CA_CERTIFICATE") or None,
        manifest=load_manifest(os.getenv("NEXAIOT_MANIFEST")),
        telemetry_interval=float(os.getenv("NEXAIOT_TELEMETRY_INTERVAL", "5")),
    )


def main():
    simulator = create_from_environment()
    simulator.run()


if __name__ == "__main__":
    main()
