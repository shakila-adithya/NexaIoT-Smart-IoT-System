package com.nexaiot.server.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
@EnableIntegration
public class MqttConfig {

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.telemetry-topic}")
    private String telemetryTopic;

    @Value("${mqtt.state-topic}")
    private String stateTopic;

    @Value("${mqtt.qos}")
    private int qos;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory =
                new DefaultMqttPahoClientFactory();

        MqttConnectOptions options = new MqttConnectOptions();

        options.setServerURIs(new String[]{brokerUrl});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        factory.setConnectionOptions(options);

        return factory;
    }

    // =========================
    // MQTT INBOUND - TELEMETRY
    // =========================

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttInbound(
            MqttPahoClientFactory mqttClientFactory
    ) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        clientId,
                        mqttClientFactory,
                        telemetryTopic
                );

        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInputChannel());

        return adapter;
    }

    // =========================
    // MQTT INBOUND - DEVICE STATE
    // =========================

    @Bean
    public MessageChannel mqttStateInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttStateInbound(
            MqttPahoClientFactory mqttClientFactory
    ) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        clientId + "-state",
                        mqttClientFactory,
                        stateTopic
                );

        adapter.setQos(qos);
        adapter.setOutputChannel(mqttStateInputChannel());

        return adapter;
    }

    // =========================
    // MQTT OUTBOUND - COMMANDS
    // =========================

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound(
            MqttPahoClientFactory mqttClientFactory
    ) {
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(
                        clientId + "-outbound",
                        mqttClientFactory
                );

        handler.setAsync(true);
        handler.setDefaultQos(qos);

        return handler;
    }
}