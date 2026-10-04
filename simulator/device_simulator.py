import json
import random
import time
import os
from datetime import datetime, timezone

import paho.mqtt.client as mqtt


BROKER_HOST = "localhost"
BROKER_PORT = 1883

DEVICE_KEY = os.getenv(
    "NEXAIOT_DEVICE_KEY",
    "NEXA-5802182413C5"
)

TELEMETRY_TOPIC = f"nexaiot/devices/{DEVICE_KEY}/telemetry"
COMMAND_TOPIC = f"nexaiot/devices/{DEVICE_KEY}/commands"
STATE_TOPIC = f"nexaiot/devices/{DEVICE_KEY}/state"

device_state = {
    "power": False
}


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


def on_connect(client, userdata, flags, reason_code, properties):
    print(f"Connected to MQTT broker: {reason_code}")

    client.subscribe(
        COMMAND_TOPIC,
        qos=1
    )

    print(f"Listening for commands: {COMMAND_TOPIC}")


def on_message(client, userdata, message):
    try:
        payload = json.loads(
            message.payload.decode("utf-8")
        )

        if "power" in payload:
            device_state["power"] = bool(payload["power"])

            state_text = (
                "ON"
                if device_state["power"]
                else "OFF"
            )

            print(f"\nDEVICE POWER → {state_text}")

            state_payload = json.dumps({
                "power": device_state["power"]
            })

            result = client.publish(
                STATE_TOPIC,
                state_payload,
                qos=1
            )

            print(
                f"State confirmed → {state_payload}"
            )

    except Exception as error:
        print(f"Invalid command: {error}")


client = mqtt.Client(
    callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
    client_id=f"simulator-{DEVICE_KEY}"
)

client.on_connect = on_connect
client.on_message = on_message

client.connect(
    BROKER_HOST,
    BROKER_PORT,
    60
)

client.loop_start()

time.sleep(1)

result, message_id = client.subscribe(
    COMMAND_TOPIC,
    qos=1
)

print(f"Listening for commands: {COMMAND_TOPIC}")
print(f"Subscribe result: {result}")

try:
    while True:
        telemetry = create_telemetry()

        payload = json.dumps(telemetry)

        result = client.publish(
            TELEMETRY_TOPIC,
            payload,
            qos=1
        )

        result.wait_for_publish()

        print(
            f"Telemetry sent | "
            f"Temperature: {telemetry['metrics']['temperature']} °C | "
            f"Humidity: {telemetry['metrics']['humidity']} % | "
            f"Power: {'ON' if device_state['power'] else 'OFF'}"
        )

        time.sleep(5)

except KeyboardInterrupt:
    print("\nSimulator stopped.")

finally:
    client.loop_stop()
    client.disconnect()