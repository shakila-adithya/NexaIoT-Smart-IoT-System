#include <Arduino.h>
#include <ArduinoJson.h>
#include <DHTesp.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <time.h>

#include "device_config.h"

namespace {

DHTesp dht;
WiFiClientSecure tlsClient;
PubSubClient mqtt(tlsClient);
bool powerOn = false;
unsigned long lastTelemetry = 0;
unsigned long nextWifiAttempt = 0;
unsigned long nextMqttAttempt = 0;
unsigned long wifiAttemptStarted = 0;
unsigned long wifiRetryDelay = 1000;
unsigned long mqttRetryDelay = 1000;
bool wifiConnecting = false;
bool wifiReady = false;
bool mqttReady = false;
bool clockReady = false;
bool clockWaitLogged = false;
bool configurationReady = true;

constexpr unsigned long WIFI_CONNECT_TIMEOUT_MS = 15000;
constexpr unsigned long WIFI_RETRY_MAX_MS = 30000;
constexpr unsigned long MQTT_RETRY_MAX_MS = 30000;
constexpr unsigned long TELEMETRY_INTERVAL_MS = 5000;

void logMessage(const char* message) {
    Serial.println(message);
}

void logRetry(const char* service, unsigned long delayMs) {
    Serial.print(service);
    Serial.print(" retry in ");
    Serial.print(delayMs);
    Serial.println(" ms");
}

String isoNow() {
    time_t current = time(nullptr);
    struct tm utc;
    gmtime_r(&current, &utc);
    char value[25];
    strftime(value, sizeof(value), "%Y-%m-%dT%H:%M:%SZ", &utc);
    return String(value);
}

bool configurationValid() {
    auto isDeviceKeyCharacter = [](char character) {
        return (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '-' || character == '_';
    };
    size_t deviceKeyLength = strlen(NEXA_DEVICE_KEY);
    if (deviceKeyLength == 0 || !isDeviceKeyCharacter(NEXA_DEVICE_KEY[0])
            || (NEXA_DEVICE_KEY[0] == '-' || NEXA_DEVICE_KEY[0] == '_')) {
        return false;
    }
    for (size_t index = 1; index < deviceKeyLength; index++) {
        char character = NEXA_DEVICE_KEY[index];
        if (!isDeviceKeyCharacter(character)) {
            return false;
        }
    }
    String expectedClientId = String("nexaiot-device-") + NEXA_DEVICE_KEY;
    return strlen(NEXA_WIFI_SSID) > 0
            && strlen(NEXA_WIFI_PASSWORD) > 0
            && strlen(NEXA_MQTT_HOST) > 0
            && strlen(NEXA_MQTT_USERNAME) > 0
            && strlen(NEXA_MQTT_PASSWORD) > 0
            && strlen(NEXA_MQTT_CLIENT_ID) > 0
            && expectedClientId == NEXA_MQTT_CLIENT_ID
            && strlen(NEXA_MQTT_ROOT_CA) > 0
            && NEXA_MQTT_PORT > 0;
}

String topic(const char* suffix) {
    return String("nexaiot/devices/") + NEXA_DEVICE_KEY + "/" + suffix;
}

void publishState() {
    if (!mqtt.connected()) {
        logMessage("MQTT state skipped: disconnected");
        return;
    }
    JsonDocument document;
    document["power"] = powerOn;
    String payload;
    serializeJson(document, payload);
    if (!mqtt.publish(topic("state").c_str(), payload.c_str(), true)) {
        logMessage("MQTT state publish failed");
    } else {
        logMessage("MQTT state published");
    }
}

void publishCommandResult(JsonDocument& command, const char* status, const char* reason) {
    const char* commandId = command["commandId"] | "";
    if (commandId[0] == '\0') {
        return;
    }
    JsonDocument result;
    result["commandId"] = commandId;
    const char* correlationId = command["correlationId"] | "";
    if (correlationId[0] != '\0') {
        result["correlationId"] = correlationId;
    }
    result["capabilityKey"] = "power";
    result["status"] = status;
    if (reason != nullptr) {
        result["reason"] = reason;
    }
    result["processedAt"] = isoNow();
    String payload;
    serializeJson(result, payload);
    if (!mqtt.publish(topic("command-results").c_str(), payload.c_str(), false)) {
        logMessage("MQTT command result publish failed");
    }
}

void onCommand(char*, uint8_t* bytes, unsigned int length) {
    Serial.print("MQTT command received, bytes=");
    Serial.println(length);
    JsonDocument command;
    if (deserializeJson(command, bytes, length)) {
        logMessage("MQTT command rejected: invalid JSON");
        return;
    }

    bool hasEnvelope = command["commandId"].is<const char*>();
    const char* capabilityKey = command["capabilityKey"] | "power";
    JsonVariant value = command["value"];
    bool requestedPower = false;
    bool valid = false;

    if (strcmp(capabilityKey, "power") == 0) {
        if (!value.isNull() && value.is<bool>()) {
            requestedPower = value.as<bool>();
            valid = true;
        } else if (command["power"].is<bool>()) {
            requestedPower = command["power"].as<bool>();
            valid = true;
        }
    }

    if (!valid) {
        logMessage("MQTT command rejected: unsupported power value");
        if (hasEnvelope) {
            publishCommandResult(command, "REJECTED", "Unsupported power command.");
        }
        return;
    }

    powerOn = requestedPower;
    digitalWrite(NEXA_RELAY_PIN, powerOn ? HIGH : LOW);
    Serial.print("Power command accepted, value=");
    Serial.println(powerOn ? "true" : "false");
    publishState();
    if (hasEnvelope) {
        publishCommandResult(command, "ACCEPTED", nullptr);
    }
}

void scheduleWifiRetry() {
    nextWifiAttempt = millis() + wifiRetryDelay;
    logRetry("Wi-Fi", wifiRetryDelay);
    wifiRetryDelay = min(wifiRetryDelay * 2, WIFI_RETRY_MAX_MS);
    wifiConnecting = false;
}

void startWifi() {
    if (wifiConnecting || WiFi.status() == WL_CONNECTED || millis() < nextWifiAttempt) {
        return;
    }
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.begin(NEXA_WIFI_SSID, NEXA_WIFI_PASSWORD);
    wifiAttemptStarted = millis();
    wifiConnecting = true;
    Serial.println("Wi-Fi connection started");
}

void serviceWifi() {
    wl_status_t status = WiFi.status();
    if (status == WL_CONNECTED) {
        if (!wifiReady) {
            wifiReady = true;
            wifiConnecting = false;
            wifiRetryDelay = 1000;
            Serial.print("Wi-Fi connected, IP=");
            Serial.println(WiFi.localIP());
            configTime(0, 0, "pool.ntp.org", "time.nist.gov");
        }
        return;
    }

    if (wifiReady) {
        wifiReady = false;
        mqttReady = false;
        mqtt.disconnect();
        logMessage("Wi-Fi disconnected");
    }

    if (wifiConnecting && millis() - wifiAttemptStarted >= WIFI_CONNECT_TIMEOUT_MS) {
        WiFi.disconnect(false, false);
        logMessage("Wi-Fi connection timeout");
        scheduleWifiRetry();
    }

    startWifi();
}

void scheduleMqttRetry() {
    nextMqttAttempt = millis() + mqttRetryDelay;
    logRetry("MQTT", mqttRetryDelay);
    mqttRetryDelay = min(mqttRetryDelay * 2, MQTT_RETRY_MAX_MS);
}

void serviceMqtt() {
    if (!wifiReady) {
        if (mqttReady || mqtt.connected()) {
            mqtt.disconnect();
            mqttReady = false;
            logMessage("MQTT disconnected because Wi-Fi is unavailable");
        }
        return;
    }

    if (time(nullptr) < 100000) {
        if (!clockWaitLogged) {
            logMessage("Waiting for valid SNTP time before TLS MQTT");
            clockWaitLogged = true;
        }
        return;
    }
    if (!clockReady) {
        clockReady = true;
        clockWaitLogged = false;
        logMessage("SNTP time valid for TLS MQTT");
    }

    if (mqtt.connected()) {
        if (!mqttReady) {
            mqttReady = true;
            mqttRetryDelay = 1000;
            logMessage("MQTT connected");
            if (!mqtt.subscribe(topic("commands").c_str(), 1)) {
                logMessage("MQTT command subscription failed");
            } else {
                logMessage("MQTT command subscription restored");
            }
            publishState();
        }
        mqtt.loop();
        return;
    }

    if (mqttReady) {
        mqttReady = false;
        logMessage("MQTT disconnected");
    }

    if (millis() < nextMqttAttempt) {
        return;
    }

    Serial.println("MQTT connection started");
    if (mqtt.connect(NEXA_MQTT_CLIENT_ID, NEXA_MQTT_USERNAME, NEXA_MQTT_PASSWORD)) {
        mqttReady = true;
        mqttRetryDelay = 1000;
        logMessage("MQTT connected");
        if (!mqtt.subscribe(topic("commands").c_str(), 1)) {
            logMessage("MQTT command subscription failed");
        } else {
            logMessage("MQTT command subscription restored");
        }
        publishState();
    } else {
        Serial.print("MQTT connection failed, state=");
        Serial.println(mqtt.state());
        scheduleMqttRetry();
    }
}

void publishTelemetry() {
    if (!mqtt.connected()) {
        logMessage("Telemetry skipped: MQTT disconnected");
        return;
    }
    TempAndHumidity reading = dht.getTempAndHumidity();
    if (isnan(reading.temperature) || isnan(reading.humidity)
            || !isfinite(reading.temperature) || !isfinite(reading.humidity)) {
        Serial.print("DHT22 reading invalid, status=");
        Serial.println(dht.getStatusString());
        return;
    }
    JsonDocument document;
    document["metrics"]["temperature"] = reading.temperature;
    document["timestamp"] = isoNow();
    String payload;
    serializeJson(document, payload);
    if (payload.length() == 0 || !mqtt.publish(topic("telemetry").c_str(), payload.c_str(), false)) {
        logMessage("MQTT telemetry publish failed");
    } else {
        logMessage("MQTT telemetry published");
    }
}

}

void setup() {
    Serial.begin(115200);
    delay(100);
    logMessage("NexaIoT Smart Fan boot");
    pinMode(NEXA_RELAY_PIN, OUTPUT);
    digitalWrite(NEXA_RELAY_PIN, LOW);
    logMessage("Output initialized LOW");
    if (!configurationValid()) {
        logMessage("Configuration invalid; firmware halted");
        configurationReady = false;
        return;
    }
    dht.setup(NEXA_DHT_PIN, DHTesp::DHT22);
    logMessage("DHT22 initialized on configured pin");
    tlsClient.setCACert(NEXA_MQTT_ROOT_CA);
    logMessage("TLS CA configured");
    mqtt.setServer(NEXA_MQTT_HOST, NEXA_MQTT_PORT);
    mqtt.setCallback(onCommand);
    mqtt.setBufferSize(1024);
    mqtt.setSocketTimeout(5);
    startWifi();
}

void loop() {
    if (!configurationReady) {
        yield();
        return;
    }
    serviceWifi();
    serviceMqtt();
    if (millis() - lastTelemetry >= TELEMETRY_INTERVAL_MS) {
        lastTelemetry = millis();
        publishTelemetry();
    }
}
