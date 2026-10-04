package com.nexaiot.server.mqtt;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class MqttCommandPublisher {

    private final MessageChannel mqttOutboundChannel;

    public MqttCommandPublisher(
            @Qualifier("mqttOutboundChannel")
            MessageChannel mqttOutboundChannel
    ) {
        this.mqttOutboundChannel = mqttOutboundChannel;
    }

    public void publishCommand(String deviceKey, String payload) {

        String topic =
                "nexaiot/devices/"
                + deviceKey
                + "/commands";

        Message<String> message =
                MessageBuilder
                        .withPayload(payload)
                        .setHeader(MqttHeaders.TOPIC, topic)
                        .build();

        mqttOutboundChannel.send(message);
    }
}