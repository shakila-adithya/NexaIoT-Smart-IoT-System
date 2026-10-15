package com.nexaiot.server.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
@Profile("!test")
public class MqttConfig {

    public MqttConfig() {
    }

    MqttConfig(
            String brokerUrl,
            String clientId,
            String telemetryTopic,
            String stateTopic,
            int qos,
            String username,
            String password,
            boolean authRequired,
            boolean tlsRequired
    ) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.telemetryTopic = telemetryTopic;
        this.stateTopic = stateTopic;
        this.qos = qos;
        this.username = username;
        this.password = password;
        this.authRequired = authRequired;
        this.tlsRequired = tlsRequired;
    }

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

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Value("${mqtt.auth-required:false}")
    private boolean authRequired;

    @Value("${mqtt.tls-required:false}")
    private boolean tlsRequired;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory =
                new DefaultMqttPahoClientFactory();

        MqttConnectOptions options = createConnectOptions();

        factory.setConnectionOptions(options);

        return factory;
    }

    MqttConnectOptions createConnectOptions() {
        MqttConfigurationValidator.validate(
                brokerUrl,
                username,
                password,
                authRequired,
                tlsRequired
        );

        MqttConnectOptions options = new MqttConnectOptions();

        options.setServerURIs(new String[]{brokerUrl});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        if (username != null && !username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }

        return options;
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
