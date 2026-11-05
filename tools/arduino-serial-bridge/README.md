# Arduino serial bridge

This bridge reads newline-delimited JSON from an Arduino Uno and publishes only telemetry to the existing NexaIoT MQTT/TLS protocol. It does not subscribe to command topics.

Register a new device through the existing authenticated NexaIoT UI/API with the custom manifest in this directory. Then run the explicit host gateway enrollment/activation command below. Do not use `simulator/onboard_device.py` for the physical Arduino.

Required environment variables after provisioning:

- `NEXAIOT_DEVICE_KEY` — the separately provisioned Arduino device key.
- `NEXAIOT_CA_CERTIFICATE` — trusted local CA path, normally `tools/local-development/.runtime/certs/ca.crt`.
- `NEXAIOT_MANIFEST` — the custom manifest path, if not using the bundled manifest.
- `NEXAIOT_MQTT_URL` — normally `ssl://127.0.0.1:8885`.
- `NEXAIOT_ARDUINO_SERIAL_PORT` — normally `COM6`.
- `NEXAIOT_ARDUINO_BAUDRATE` — normally `9600`.
- `NEXAIOT_CREDENTIALS_DIR` — normally the ignored gateway directory below.

The bridge validates the stored username/client ID convention and never prints the password.

The gateway stores its private key and delivered credentials separately under:

```text
tools/local-development/.runtime/arduino-device-keys/{deviceKey}.pem
tools/local-development/.runtime/arduino-device-credentials/{deviceKey}.json
```

After registering the device, run this explicit activation operation:

```powershell
& .\simulator\.venv\bin\python.exe .\tools\arduino-serial-bridge\activation_gateway.py `
  --ca .\tools\local-development\.runtime\certs\ca.crt `
  --device-id '<new-device-id>' `
  --device-key '<new-device-key>'
```

Run only after provisioning and activation approval:

```powershell
& .\simulator\.venv\bin\python.exe .\tools\arduino-serial-bridge\bridge.py
```
