# ESP32 smart-fan development target

This target uses an ESP32, a DHT22 temperature and humidity sensor, and an LED or relay on GPIO 25. It matches the NexaIoT `smart-fan` capability template.

The firmware is intentionally staged. It does not implement or bypass the NexaIoT pairing and credential-delivery workflow. The backend currently exposes pairing claim and owner-authorized enrollment operation endpoints, but does not expose a device credential handoff endpoint. A real device therefore remains blocked at credential delivery until that backend contract exists.

## Build preparation

Copy `include/device_config.h.example` to `include/device_config.h` and fill every value from an isolated development broker and test Wi-Fi network. Do not place this file under source control.

The firmware requires an `ssl://`-equivalent TLS broker endpoint, a CA certificate, a broker username, a broker password, and a client ID matching `nexaiot-device-<deviceKey>`. It never configures an insecure TLS client.

Run these commands from this directory after installing PlatformIO:

```powershell
Copy-Item include\device_config.h.example include\device_config.h
pio run
pio run -t upload
pio device monitor
```

## Wire-up

Connect the DHT22 data line to GPIO 4, with the sensor power and ground connected to the board and a 4.7 kΩ to 10 kΩ pull-up from data to 3.3 V unless the sensor module includes one. Connect a relay input or LED to GPIO 25. GPIO 25 is exposed by the generic ESP32 DevKit profile and avoids the GPIO 2 boot-strapping risk. Use a transistor, flyback diode, and isolated supply for a real relay module.

## NexaIoT contracts

Telemetry is published to `nexaiot/devices/<deviceKey>/telemetry` with a payload such as:

```json
{"metrics":{"temperature":23.4},"timestamp":"2026-10-22T00:00:00Z"}
```

The backend accepts `metrics`, optional `battery`, optional `rssi`, and optional ISO-8601 `timestamp`. Every metric must be declared by the device manifest and must be numeric and finite.

State is published to `nexaiot/devices/<deviceKey>/state`:

```json
{"power":true}
```

The existing web control path publishes the legacy command payload `{"power":true}` or `{"power":false}` to `nexaiot/devices/<deviceKey>/commands`. The simulator also supports the structured command envelope with `commandId`, `correlationId`, `capabilityKey`, `value`, and `issuedAt`. This firmware accepts both forms. Structured commands receive a result on `nexaiot/devices/<deviceKey>/command-results`.

## Development safety boundary

Keep production onboarding disabled. Register a test device through the protected web UI using the `smart-fan` template, and use only an isolated broker and test credentials for the first hardware exercise. Do not reuse production broker credentials or certificates.
