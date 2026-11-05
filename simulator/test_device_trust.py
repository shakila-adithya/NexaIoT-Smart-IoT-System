import base64
import importlib.util
import tempfile
import unittest
from pathlib import Path


AVAILABLE = importlib.util.find_spec("cryptography") is not None


@unittest.skipUnless(AVAILABLE, "cryptography dependency is required for trust-anchor tests")
class DeviceTrustTest(unittest.TestCase):
    def test_p256_key_storage_and_registration_payload(self):
        from device_trust import PROTOCOL, load_or_create_private_key, registration_payload

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "device-key.pem"
            first = load_or_create_private_key(path)
            second = load_or_create_private_key(path)
            self.assertEqual(first.private_numbers(), second.private_numbers())
            payload = registration_payload(
                first, "NEXA-TRUST-1", "operation-1", "owner@example.com", "challenge")

        self.assertEqual("nexaiot-device-NEXA-TRUST-1", payload["mqttClientId"])
        self.assertTrue(len(base64.b64decode(payload["publicKey"])) > 0)
        self.assertTrue(len(base64.b64decode(payload["signature"])) > 0)
        self.assertEqual("NexaIoT/DeviceTrustAnchor/v1", PROTOCOL)

    def test_message_changes_when_binding_fields_change(self):
        from device_trust import registration_message

        original = registration_message(
            "NEXA-1", "operation-1", "owner@example.com", "client-1", "challenge", "fp")
        changed = registration_message(
            "NEXA-2", "operation-1", "owner@example.com", "client-1", "challenge", "fp")
        self.assertNotEqual(original, changed)


if __name__ == "__main__":
    unittest.main()
