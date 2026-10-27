import importlib.util
import tempfile
import unittest
from pathlib import Path


AVAILABLE = importlib.util.find_spec("cryptography") is not None


@unittest.skipUnless(AVAILABLE, "cryptography dependency is required for delivery tests")
class DeviceDeliveryTest(unittest.TestCase):
    def test_signature_message_is_bound_to_delivery_context(self):
        from cryptography.hazmat.primitives.asymmetric import ec
        from device_delivery import delivery_message, delivery_signature

        key = ec.generate_private_key(ec.SECP256R1())
        signature = delivery_signature(
            key, "NEXA-1", "operation-1", "owner@example.com",
            "nexaiot-device-NEXA-1", "fingerprint", "challenge", "expires")
        self.assertTrue(signature)
        self.assertNotEqual(
            delivery_message("NEXA-1", "operation-1", "owner@example.com",
                             "nexaiot-device-NEXA-1", "fingerprint", "challenge", "expires"),
            delivery_message("NEXA-2", "operation-1", "owner@example.com",
                             "nexaiot-device-NEXA-1", "fingerprint", "challenge", "expires"))

    def test_credentials_are_stored_in_ignored_local_file(self):
        from device_delivery import store_credentials

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "device.json"
            store_credentials(path, {
                "operationId": "operation-1",
                "username": "nexaiot-device-NEXA-1",
                "mqttClientId": "nexaiot-device-NEXA-1",
                "password": "local-only-test-value",
            })
            self.assertIn("local-only-test-value", path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
