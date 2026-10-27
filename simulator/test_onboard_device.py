import unittest

from onboard_device import select_device


class OnboardSelectionTest(unittest.TestCase):
    devices = [
        {"id": "device-1", "deviceKey": "NEXA-ONE", "name": "Greenhouse Sensor"},
        {"id": "device-2", "deviceKey": "NEXA-TWO", "name": "Greenhouse Sensor"},
    ]

    def test_selects_by_serial(self):
        self.assertEqual(select_device(self.devices, "NEXA-TWO")["id"], "device-2")

    def test_selects_by_id(self):
        self.assertEqual(select_device(self.devices, "device-1")["deviceKey"], "NEXA-ONE")

    def test_name_does_not_select_a_duplicate(self):
        with self.assertRaisesRegex(RuntimeError, "serial or device ID"):
            select_device(self.devices, "Greenhouse Sensor")


if __name__ == "__main__":
    unittest.main()
