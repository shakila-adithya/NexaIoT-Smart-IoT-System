package com.nexaiot.server.mqtt;

import java.util.regex.Pattern;

/** Central topic validation and construction for the NexaIoT MQTT namespace. */
public final class MqttTopicSecurity {

    private static final String ROOT = "nexaiot";
    private static final String DEVICES = "devices";
    private static final Pattern SAFE_DEVICE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

    private MqttTopicSecurity() {
    }

    public static String parseInboundDeviceKey(String topic, String expectedType) {
        if (topic == null || topic.isBlank() || expectedType == null || expectedType.isBlank()) {
            throw new IllegalArgumentException("MQTT topic is invalid.");
        }

        String[] parts = topic.split("/", -1);
        if (parts.length != 4
                || !ROOT.equals(parts[0])
                || !DEVICES.equals(parts[1])
                || !expectedType.equals(parts[3])) {
            throw new IllegalArgumentException("MQTT topic is invalid.");
        }

        validateDeviceKey(parts[2]);
        return parts[2];
    }

    public static String commandTopic(String deviceKey) {
        validateDeviceKey(deviceKey);
        return ROOT + "/" + DEVICES + "/" + deviceKey + "/commands";
    }

    public static String activationChallengeTopic(String deviceKey) {
        validateDeviceKey(deviceKey);
        return ROOT + "/" + DEVICES + "/" + deviceKey + "/activation-challenges";
    }

    public static String activationResponseTopic(String deviceKey) {
        validateDeviceKey(deviceKey);
        return ROOT + "/" + DEVICES + "/" + deviceKey + "/activation-responses";
    }

    public static String activationResponseSubscription() {
        return ROOT + "/" + DEVICES + "/+/activation-responses";
    }

    public static void validateDeviceKey(String deviceKey) {
        if (deviceKey == null || !SAFE_DEVICE_KEY.matcher(deviceKey).matches()) {
            throw new IllegalArgumentException("MQTT device key is invalid.");
        }
    }
}
