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

sensor_state = {
    "temperature": 27.5,
    "humidity": 60.0,
    "battery": 90,
    "rssi": -52
}


def update_sensor_value(current, center, minimum, maximum, max_step, decimals=1):
    mean_reversion = (center - current) * 0.04
    random_change = random.uniform(-max_step, max_step)
    change = max(-max_step, min(max_step, random_change + mean_reversion))

    return round(
        max(minimum, min(maximum, current + change)),
        decimals
    )


def update_battery():
    if sensor_state["battery"] > 0 and random.random() < 0.12:
        sensor_state["battery"] -= 1

    return sensor_state["battery"]


def create_telemetry():
    sensor_state["temperature"] = update_sensor_value(
        sensor_state["temperature"],
        center=28.0,
        minimum=24.0,
        maximum=32.0,
        max_step=0.25
    )
    sensor_state["humidity"] = update_sensor_value(
        sensor_state["humidity"],
        center=60.0,
        minimum=45.0,
        maximum=75.0,
        max_step=0.6
    )
    sensor_state["rssi"] = update_sensor_value(
        sensor_state["rssi"],
        center=-52,
        minimum=-75,
        maximum=-40,
        max_step=2,
        decimals=0
    )

    return {
        "metrics": {
            "temperature": sensor_state["temperature"],
            "humidity": sensor_state["humidity"]
        },
        "battery": update_battery(),
        "rssi": sensor_state["rssi"],
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
