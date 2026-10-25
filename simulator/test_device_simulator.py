import json
import random
import sys
import types
import unittest


class FakePublishInfo:
    def __init__(self, rc=0, published=True):
        self.rc = rc
        self.published = published

    def wait_for_publish(self, timeout=None):
        return None

    def is_published(self):
        return self.published


class FakeClient:
    def __init__(self, **kwargs):
        self.kwargs = kwargs
        self.subscriptions = []
        self.published = []
        self.connected = False
        self.publish_rc = 0
        self.publish_done = True
        self.reconnect_delays = None
        self.loop_started = False
        self.disconnect_called = False

    def reconnect_delay_set(self, minimum, maximum):
        self.reconnect_delays = (minimum, maximum)

    def username_pw_set(self, username, password):
        self.credentials = (username, password)

    def tls_set(self, ca_certs=None):
        self.ca_certs = ca_certs

    def tls_insecure_set(self, value):
        self.tls_insecure = value

    def subscribe(self, topic, qos=0):
        self.subscriptions.append((topic, qos))
        return 0, 1

    def publish(self, topic, payload, qos=0, retain=False):
        self.published.append((topic, payload, qos, retain))
        return FakePublishInfo(self.publish_rc, self.publish_done)

    def loop_start(self):
        self.loop_started = True

    def loop_stop(self):
        self.loop_started = False

    def connect_async(self, host, port, keepalive=60):
        self.connect_args = (host, port, keepalive)

    def disconnect(self):
        self.disconnect_called = True


fake_client_module = types.ModuleType("paho.mqtt.client")
fake_client_module.Client = FakeClient
fake_client_module.MQTT_ERR_SUCCESS = 0
fake_client_module.CallbackAPIVersion = types.SimpleNamespace(VERSION2=2)
fake_mqtt_package = types.ModuleType("paho.mqtt")
fake_mqtt_package.client = fake_client_module
fake_paho_package = types.ModuleType("paho")
fake_paho_package.mqtt = fake_mqtt_package
sys.modules["paho"] = fake_paho_package
sys.modules["paho.mqtt"] = fake_mqtt_package
sys.modules["paho.mqtt.client"] = fake_client_module

from device_simulator import DeviceSimulator, validate_device_key


def make_simulator(manifest=None):
    clients = []

    def factory():
        client = FakeClient()
        clients.append(client)
        return client

    simulator = DeviceSimulator(
        "NEXA-PY-01",
        manifest=manifest,
        mqtt_client_factory=factory,
        rng=random.Random(4),
    )
    return simulator, clients[0]


class DeviceSimulatorTest(unittest.TestCase):
    def test_identity_and_topics_match_backend(self):
        simulator, client = make_simulator()
        self.assertEqual("nexaiot-device-NEXA-PY-01", simulator.client_id)
        self.assertEqual(
            "nexaiot/devices/NEXA-PY-01/telemetry",
            simulator.topics["telemetry"],
        )
        self.assertEqual(
            "nexaiot/devices/NEXA-PY-01/state",
            simulator.topics["state"],
        )
        self.assertEqual(
            "nexaiot/devices/NEXA-PY-01/commands",
            simulator.topics["commands"],
        )
        self.assertEqual(
            "nexaiot/devices/NEXA-PY-01/command-results",
            simulator.topics["command_results"],
        )
        self.assertEqual((1, 30), client.reconnect_delays)

    def test_device_key_and_tls_validation(self):
        with self.assertRaises(ValueError):
            validate_device_key("NEXA/OTHER")
        with self.assertRaises(ValueError):
            DeviceSimulator("NEXA-01", broker_url="tcp://localhost:1883", tls_required=True,
                             mqtt_client_factory=FakeClient)
        with self.assertRaises(ValueError):
            DeviceSimulator("NEXA-01", username="wrong-id", password="secret",
                             mqtt_client_factory=FakeClient)
        simulator = DeviceSimulator(
            "NEXA-01",
            broker_url="ssl://localhost:8883",
            ca_certificate="local-ca.crt",
            mqtt_client_factory=FakeClient,
        )
        self.assertEqual("local-ca.crt", simulator.client.ca_certs)
        self.assertFalse(simulator.client.tls_insecure)

    def test_default_telemetry_follows_smart_fan_manifest(self):
        simulator, _ = make_simulator()
        payload = simulator.create_telemetry()
        self.assertIn("temperature", payload["metrics"])
        self.assertNotIn("humidity", payload["metrics"])
        self.assertNotIn("battery", payload)
        self.assertNotIn("rssi", payload)
        self.assertIsInstance(payload["metrics"]["temperature"], float)
        self.assertTrue(payload["timestamp"].endswith("Z"))

    def test_optional_measurements_are_emitted_only_when_declared(self):
        manifest = {
            "capabilities": [
                {"key": "temperature", "category": "MEASUREMENT", "dataType": "NUMBER"},
                {"key": "humidity", "category": "MEASUREMENT", "dataType": "NUMBER"},
                {"key": "battery", "category": "MEASUREMENT", "dataType": "NUMBER"},
                {"key": "rssi", "category": "MEASUREMENT", "dataType": "NUMBER"},
            ]
        }
        simulator, _ = make_simulator(manifest)
        payload = simulator.create_telemetry()
        self.assertIn("humidity", payload["metrics"])
        self.assertIn("battery", payload)
        self.assertIn("rssi", payload)
        self.assertGreaterEqual(payload["battery"], 0)
        self.assertLessEqual(payload["battery"], 100)

    def test_legacy_power_command_requires_boolean(self):
        simulator, _ = make_simulator()
        accepted = simulator.process_command('{"power":true}')
        rejected = simulator.process_command('{"power":1}')
        self.assertEqual("ACCEPTED", accepted.status)
        self.assertTrue(simulator.state["power"])
        self.assertEqual("REJECTED", rejected.status)
        self.assertTrue(simulator.state["power"])

    def test_structured_command_result_and_duplicate(self):
        simulator, _ = make_simulator()
        command = json.dumps({
            "commandId": "cmd-1",
            "correlationId": "corr-1",
            "capabilityKey": "power",
            "value": True,
            "issuedAt": "2026-10-22T00:00:00Z",
        })
        accepted = simulator.process_command(command)
        duplicate = simulator.process_command(command)
        self.assertEqual("ACCEPTED", accepted.status)
        self.assertEqual("DUPLICATE", duplicate.status)
        self.assertTrue(simulator.state["power"])
        self.assertEqual("corr-1", accepted.correlation_id)

    def test_structured_invalid_command_is_rejected(self):
        simulator, _ = make_simulator()
        outcome = simulator.process_command(json.dumps({
            "commandId": "cmd-2",
            "correlationId": "corr-2",
            "capabilityKey": "power",
            "value": 1,
            "issuedAt": "2026-10-22T00:00:00Z",
        }))
        self.assertEqual("REJECTED", outcome.status)
        self.assertEqual("cmd-2", outcome.command_id)
        self.assertFalse(simulator.state["power"])

    def test_commands_require_declared_writable_power_capability(self):
        manifest = {
            "capabilities": [
                {"key": "temperature", "category": "MEASUREMENT", "dataType": "NUMBER"},
            ]
        }
        simulator, _ = make_simulator(manifest)
        outcome = simulator.process_command('{"power":true}')
        self.assertEqual("REJECTED", outcome.status)
        self.assertFalse(simulator.state["power"])

    def test_callbacks_restore_subscription_and_publish_results(self):
        simulator, client = make_simulator()
        simulator.on_connect(client, None, {}, 0, None)
        self.assertTrue(simulator.connected)
        self.assertEqual((simulator.topics["commands"], 1), client.subscriptions[-1])
        message = types.SimpleNamespace(
            topic=simulator.topics["commands"],
            payload=json.dumps({
                "commandId": "cmd-3",
                "capabilityKey": "power",
                "value": False,
                "issuedAt": "2026-10-22T00:00:00Z",
            }).encode(),
        )
        simulator.on_message(client, None, message)
        self.assertFalse(simulator.state["power"])
        result_payloads = [json.loads(item[1]) for item in client.published]
        self.assertTrue(any(item.get("status") == "ACCEPTED" for item in result_payloads))

    def test_publish_failure_and_disconnect_are_safe(self):
        simulator, client = make_simulator()
        self.assertFalse(simulator.publish_telemetry())
        simulator.connected = True
        client.publish_rc = 4
        self.assertFalse(simulator.publish_state())
        simulator.on_disconnect(client, None, {}, 7, None)
        self.assertFalse(simulator.connected)
        simulator.stop()
        self.assertTrue(client.disconnect_called)


if __name__ == "__main__":
    unittest.main()
