import json
import argparse
import ssl
import sys
import unittest
from types import SimpleNamespace
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).parent))
import bridge
import activation_gateway
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec


class BridgeTest(unittest.TestCase):
    def test_valid_serial_json(self):
        self.assertEqual(bridge.parse_serial_line(b'{"testCounter":2}\n'), 2)

    def test_invalid_serial_json_is_rejected(self):
        for value in (b"not-json", b'{"testCounter":"2"}', b'{"testCounter":true}'):
            with self.assertRaises(ValueError):
                bridge.parse_serial_line(value)

    def test_envelope_and_topic(self):
        payload = bridge.telemetry_payload(3, datetime(2026, 1, 1, tzinfo=timezone.utc))
        self.assertEqual(payload, {"metrics": {"testCounter": 3}, "timestamp": "2026-01-01T00:00:00Z"})
        self.assertEqual(bridge.telemetry_topic("NEXA-ARDUINO-1"), "nexaiot/devices/NEXA-ARDUINO-1/telemetry")

    def test_manifest_validation(self):
        manifest = bridge.load_manifest(Path(__file__).parent / "arduino-test-counter-manifest.json")
        self.assertEqual(manifest["capabilities"][0]["key"], "testCounter")

    def test_tls_configuration_requires_certificate_verification(self):
        client = Mock()
        factory = Mock(return_value=client)
        credentials = {"username": "nexaiot-device-NEXA-ARDUINO-1", "password": "hidden"}
        configured, host, port = bridge.mqtt_client("NEXA-ARDUINO-1", "ssl://127.0.0.1:8885", "ca.crt", credentials, factory)
        self.assertIs(configured, client)
        self.assertEqual((host, port), ("127.0.0.1", 8885))
        client.tls_set.assert_called_once_with(ca_certs="ca.crt", cert_reqs=ssl.CERT_REQUIRED)
        client.tls_insecure_set.assert_called_once_with(False)
        client.reconnect_delay_set.assert_called_once_with(min_delay=1, max_delay=30)

    def test_mqtt_connection_retries_are_bounded(self):
        client = Mock()
        client.connect.side_effect = OSError("connection refused")
        with self.assertRaises(RuntimeError):
            bridge.connect_with_retry(client, "host", 8885, attempts=3)
        self.assertEqual(client.connect.call_count, 3)

    def test_mqtt_auth_return_code_is_not_retried(self):
        client = Mock()
        client.connect.return_value = bridge.mqtt.MQTT_ERR_AUTH
        with self.assertRaises(RuntimeError):
            bridge.connect_with_retry(client, "host", 8885, attempts=3)
        self.assertEqual(client.connect.call_count, 1)

    def test_publish_rejected_and_timeout_are_not_acknowledged(self):
        rejected = Mock(rc=bridge.mqtt.MQTT_ERR_NO_CONN)
        client = Mock()
        client.publish.return_value = rejected
        with self.assertRaises(RuntimeError):
            bridge.publish_telemetry(client, "topic", {"metrics": {"testCounter": 1}})
        timed_out = Mock(rc=bridge.mqtt.MQTT_ERR_SUCCESS, is_published=Mock(return_value=False))
        client.publish.return_value = timed_out
        with self.assertRaises(TimeoutError):
            bridge.publish_telemetry(client, "topic", {"metrics": {"testCounter": 1}})

    def test_publish_acknowledgement_is_reported(self):
        result = Mock(rc=bridge.mqtt.MQTT_ERR_SUCCESS, is_published=Mock(return_value=True))
        client = Mock()
        client.publish.return_value = result
        self.assertEqual("acknowledged", bridge.publish_telemetry(client, "topic", {"metrics": {"testCounter": 1}}))

    def test_serial_record_after_reconnect_is_published_only_when_valid(self):
        client = Mock()
        published = []
        publish = lambda _client, _topic, payload: published.append(payload) or "acknowledged"
        now = datetime(2026, 1, 1, tzinfo=timezone.utc)
        bridge.process_serial_line(client, "topic", b'{"testCounter":4}', now, publish)
        with self.assertRaises(ValueError):
            bridge.process_serial_line(client, "topic", b'{"testCounter":NaN}', now, publish)
        bridge.process_serial_line(client, "topic", b'{"testCounter":5}', now, publish)
        self.assertEqual([4, 5], [item["metrics"]["testCounter"] for item in published])

    def _run_with_fake_serial(self, serial, stop_event, publish=None):
        client = Mock()
        client_factory = Mock(return_value=client)
        publish = publish or (lambda *_args: "acknowledged")
        with patch.object(bridge, "load_manifest"), \
             patch.object(bridge, "load_credentials", return_value={"username": "u", "password": "p", "mqttClientId": "nexaiot-device-D"}), \
             patch.object(bridge, "mqtt_client", return_value=(client, "host", 8885)), \
             patch.object(bridge, "connect_with_retry"), \
             patch.object(bridge, "process_serial_line", side_effect=publish):
            bridge.run("COM6", 9600, "D", "ssl://host:8885", "ca", "manifest.json",
                       serial_factory=lambda *_args, **_kwargs: serial, stop_event=stop_event)
        return client

    def test_serial_loop_shuts_down_while_waiting_for_input(self):
        stop = Mock()
        stop.is_set.side_effect = [False, False, True]
        serial = Mock()
        serial.readline.return_value = b""
        client = self._run_with_fake_serial(serial, stop)
        serial.close.assert_called_once_with()
        client.loop_stop.assert_called_once_with()
        client.disconnect.assert_called_once_with()

    def test_serial_loop_shuts_down_after_disconnect(self):
        class Disconnected:
            def __init__(self): self.closed = False
            def readline(self): raise OSError("serial disconnected")
            def close(self): self.closed = True
        stop = Mock()
        stop.is_set.side_effect = [False, False, True]
        serial = Disconnected()
        self._run_with_fake_serial(serial, stop)
        self.assertTrue(serial.closed)

    def test_serial_loop_shuts_down_after_publication_failure(self):
        stop = Mock()
        stop.is_set.side_effect = [False, False, True]
        serial = Mock()
        serial.readline.return_value = b'{"testCounter":1}'
        def fail_publish(*_args):
            raise RuntimeError("publication failed")
        client = self._run_with_fake_serial(serial, stop, fail_publish)
        serial.close.assert_called_once_with()
        client.disconnect.assert_called_once_with()

    def test_shutdown_requested_before_read_does_not_publish(self):
        stop = Mock()
        stop.is_set.return_value = True
        serial = Mock()
        client = self._run_with_fake_serial(serial, stop)
        serial.readline.assert_not_called()
        client.disconnect.assert_called_once_with()

    def test_serial_disconnect_is_safe_to_reopen(self):
        serial = Mock()
        serial.readline.side_effect = [OSError("disconnected"), b'{"testCounter":6}']
        self.assertRaises(OSError, serial.readline)
        self.assertEqual(6, bridge.parse_serial_line(serial.readline()))
        serial.close.assert_not_called()

    def test_activation_response_binds_challenge_identity_and_signature(self):
        key = ec.generate_private_key(ec.SECP256R1())
        challenge = {
            "protocol": "NexaIoT/Activation/v1",
            "deviceKey": "NEXA-ARDUINO-1",
            "operationId": "operation-1",
            "mqttClientId": "nexaiot-device-NEXA-ARDUINO-1",
            "trustAnchorFingerprint": "fingerprint",
            "challenge": "challenge",
            "expiresAt": "2099-01-01T00:00:00Z",
        }
        response = activation_gateway.activation_response(
            key, challenge, "NEXA-ARDUINO-1", "operation-1", "owner@example.com",
            "nexaiot-device-NEXA-ARDUINO-1", "fingerprint",
            "nexaiot/devices/NEXA-ARDUINO-1/activation-challenges")
        public = key.public_key()
        public.verify(
            __import__("base64").b64decode(response["signature"]),
            __import__("device_trust").activation_message(
                "NEXA-ARDUINO-1", "operation-1", "owner@example.com",
                "nexaiot-device-NEXA-ARDUINO-1", "fingerprint", "challenge",
                "2099-01-01T00:00:00Z"),
            ec.ECDSA(hashes.SHA256()))

    def test_activation_rejects_wrong_device(self):
        with self.assertRaises(ValueError):
            activation_gateway.validate_activation_challenge(
                {"protocol": "NexaIoT/Activation/v1", "deviceKey": "OTHER"},
                "NEXA-ARDUINO-1", "operation-1", "client", "fingerprint",
                "nexaiot/devices/NEXA-ARDUINO-1/activation-challenges")

    def test_activation_rejects_existing_key_or_credential(self):
        key = Path("key.pem")
        credential = Path("credential.json")
        with patch.object(Path, "exists", side_effect=[True]):
            with self.assertRaises(FileExistsError):
                activation_gateway.ensure_new_paths(key, credential)
        with patch.object(Path, "exists", side_effect=[False, True]):
            with self.assertRaises(FileExistsError):
                activation_gateway.ensure_new_paths(key, credential)

    def test_activation_rejects_expired_and_retained_challenges(self):
        challenge = {"protocol": "NexaIoT/Activation/v1", "deviceKey": "NEXA-ARDUINO-1",
                     "operationId": "operation-1", "mqttClientId": "client",
                     "trustAnchorFingerprint": "fingerprint", "challenge": "c",
                     "expiresAt": "2000-01-01T00:00:00Z"}
        topic = "nexaiot/devices/NEXA-ARDUINO-1/activation-challenges"
        with self.assertRaises(ValueError):
            activation_gateway.validate_activation_challenge(challenge, "NEXA-ARDUINO-1", "operation-1", "client", "fingerprint", topic)
        challenge["expiresAt"] = "2099-01-01T00:00:00Z"
        with self.assertRaises(ValueError):
            activation_gateway.validate_activation_challenge(challenge, "NEXA-ARDUINO-1", "operation-1", "client", "fingerprint", topic, True)

    def test_operation_completion_requires_verified_completed_state(self):
        states = iter([
            {"state": "CREDENTIAL_DELIVERY_PENDING", "deviceVerified": False},
            {"state": "COMPLETED", "deviceVerified": True},
        ])
        clock_values = iter([0, 0, 1, 1])
        result = activation_gateway.wait_for_operation_completion(
            lambda: next(states), timeout=5, sleep=lambda _seconds: None,
            clock=lambda: next(clock_values),
        )
        self.assertTrue(result["deviceVerified"])

    def test_operation_completion_rejects_failed_and_unverified_states(self):
        with self.assertRaises(RuntimeError):
            activation_gateway.wait_for_operation_completion(
                lambda: {"state": "RECONCILIATION_REQUIRED"}, sleep=lambda _seconds: None,
                clock=lambda: 0,
            )

    def test_mocked_activation_workflow_completes_only_after_verified_status(self):
        key = ec.generate_private_key(ec.SECP256R1())
        device_key = "NEXA-ARDUINO-1"
        client_id = f"nexaiot-device-{device_key}"
        challenge = {"protocol": "NexaIoT/Activation/v1", "deviceKey": device_key,
                     "operationId": "op-1", "mqttClientId": client_id,
                     "trustAnchorFingerprint": "fp", "challenge": "c",
                     "expiresAt": "2099-01-01T00:00:00Z"}
        statuses = iter([
            {"state": "CREDENTIAL_DELIVERY_PENDING", "brokerIdentityVerified": True},
            {"state": "COMPLETED", "deviceVerified": True},
        ])
        calls = []

        class FakeClient:
            def __init__(self, client_id): self.client_id = client_id
            def username_pw_set(self, *_): pass
            def tls_set(self, **_): pass
            def tls_insecure_set(self, *_): pass
            def connect(self, *_args, **_kwargs): return 0
            def subscribe(self, *_args, **_kwargs):
                self.on_message(self, None, SimpleNamespace(
                    topic=f"nexaiot/devices/{device_key}/activation-challenges",
                    retain=False, payload=json.dumps(challenge).encode()))
                return (0, 1)
            def publish(self, *_args, **_kwargs): return SimpleNamespace(rc=0)
            def loop_start(self): pass
            def loop_stop(self): pass
            def disconnect(self): pass

        def fake_api(_base, path, method="GET", body=None, token=None, context=None):
            calls.append((method, path))
            if path.endswith("/auth/login"): return 200, {"token": "token"}
            if path.endswith("/devices/device-1"): return 200, {"deviceKey": device_key, "capabilityManifest": {"capabilities": []}}
            if path.endswith("pairing-sessions"): return 201, {"sessionId": "pair-1", "pairingToken": "pair-token"}
            if "/claim" in path: return 200, {}
            if path.endswith("enrollment-operations"): return 201, {"operationId": "op-1"}
            if path.endswith("/trust-anchor/challenge"): return 200, {"challenge": "trust-c"}
            if path.endswith("/trust-anchor"): return 200, {"trustAnchorFingerprint": "fp"}
            if path.endswith("/credential-delivery/challenge"): return 200, {"challenge": "delivery-c", "expiresAt": "2099-01-01T00:00:00Z", "trustAnchorFingerprint": "fp"}
            if path.endswith("/activation/challenge"): return 200, {}
            if "/enrollment-operations/op-1" in path: return 200, next(statuses)
            raise AssertionError(path)

        args = SimpleNamespace(backend="https://backend", ca="ca.crt", key_dir="key-dir",
                               credential_dir="credential-dir", owner_email="owner@example.com",
                               device_id="device-1", device_key=device_key,
                               manifest="manifest.json", mqtt_url="ssl://broker:8885")
        stored = []
        with patch.object(activation_gateway, "getpass", return_value="password"), \
             patch.object(activation_gateway, "ssl") as tls, \
             patch.object(activation_gateway, "ensure_new_paths"), \
             patch.object(activation_gateway, "load_manifest", return_value={"capabilities": []}), \
             patch.object(activation_gateway, "registration_payload", return_value={"publicKey": "key", "signature": "sig"}), \
             patch.object(activation_gateway, "activation_signature", return_value="sig"), \
             patch.object(activation_gateway, "Path") as path_type:
            path_type.return_value.is_file.return_value = True
            tls.create_default_context.return_value = object()
            activation_gateway.run(args, {"api_call": fake_api, "mqtt_client_factory": FakeClient,
                                          "load_or_create_private_key": lambda _path: key,
                                          "request_credentials": lambda *a: {"operationId": "op-1", "username": client_id, "mqttClientId": client_id, "password": "pw"},
                                          "store_credentials": lambda path, value: stored.append((path, value)),
                                          "sleep": lambda _seconds: None, "monotonic": iter([0, 0, 0, 1, 1]).__next__})
        self.assertTrue(stored)
        self.assertTrue(any(path.endswith("/activation/challenge") for _, path in calls))

    def test_mocked_activation_rejects_credential_operation_mismatch(self):
        credentials = {"operationId": "other", "username": "nexaiot-device-NEXA-ARDUINO-1",
                       "mqttClientId": "nexaiot-device-NEXA-ARDUINO-1", "password": "pw"}
        self.assertNotEqual(credentials["operationId"], "op-1")
        states = iter([{"state": "COMPLETED", "deviceVerified": False}])
        with self.assertRaises(TimeoutError):
            activation_gateway.wait_for_operation_completion(
                lambda: next(states), timeout=0, sleep=lambda _seconds: None,
                clock=lambda: 0,
            )


if __name__ == "__main__":
    unittest.main()
