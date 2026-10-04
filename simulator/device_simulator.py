import json
import random
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt


BROKER_HOST = "localhost"
BROKER_PORT = 1883

DEVICE_KEY = "NEXA-5802182413C5"

TOPIC = f"nexaiot/devices/{DEVICE_KEY}/telemetry"


def create_telemetry():
    return {
        "metrics": {
            "temperature": round(random.uniform(26.0, 32.0), 1),
            "humidity": round(random.uniform(55.0, 75.0), 1)
        },
        "battery": random.randint(80, 100),
        "rssi": random.randint(-65, -40),
        "timestamp": datetime.now(timezone.utc).isoformat()
    }


client = mqtt.Client(
    callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
    client_id=f"simulator-{DEVICE_KEY}"
)

print("Connecting to MQTT broker...")

client.connect(BROKER_HOST, BROKER_PORT, 60)
client.loop_start()

print("Connected.")
print(f"Device: {DEVICE_KEY}")
print(f"Topic: {TOPIC}")
print("Sending telemetry every 5 seconds...")
print("Press Ctrl+C to stop.\n")

try:
    while True:
        telemetry = create_telemetry()
        payload = json.dumps(telemetry)

        result = client.publish(
            TOPIC,
            payload,
            qos=1
        )

        result.wait_for_publish()

        print(
            f"Published | "
            f"Temperature: {telemetry['metrics']['temperature']} °C | "
            f"Humidity: {telemetry['metrics']['humidity']} % | "
            f"Battery: {telemetry['battery']} % | "
            f"RSSI: {telemetry['rssi']} dBm"
        )

        time.sleep(5)

except KeyboardInterrupt:
    print("\nSimulator stopped.")

finally:
    client.loop_stop()
    client.disconnect()