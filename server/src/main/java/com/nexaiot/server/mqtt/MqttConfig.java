package com.nexaiot.server.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.Mqttv3ClientManager;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexaiot.server.mqtt.management.MqttManagementTls;
import com.nexaiot.server.enrollment.BrokerActivationService;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableIntegration
@Profile("!test")
@ConditionalOnProperty(name = "mqtt.startup.enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

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
        this(
                brokerUrl, clientId, telemetryTopic, stateTopic, qos, username,
                password, authRequired, tlsRequired, "");
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
            boolean tlsRequired,
            String caCertificatePath
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
        this.caCertificatePath = caCertificatePath;
    }

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.telemetry-topic}")
    private String telemetryTopic;

    @Value("${mqtt.state-topic}")
    private String stateTopic;

    @Value("${mqtt.command-result-topic:nexaiot/devices/+/command-results}")
    private String commandResultTopic = "nexaiot/devices/+/command-results";

    @Value("${mqtt.command-result-max-age-seconds:30}")
    private long commandResultMaxAgeSeconds = 30;

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

    @Value("${mqtt.ca-certificate:}")
    private String caCertificatePath;

    @Bean
    public Mqttv3ClientManager mqttClientManager() {
        return new Mqttv3ClientManager(createConnectOptions(), clientId);
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

        if (MqttConfigurationValidator.usesTls(brokerUrl)) {
            if (caCertificatePath != null && !caCertificatePath.isBlank()) {
                options.setSocketFactory(MqttManagementTls.socketFactory(caCertificatePath));
            }
            options.setHttpsHostnameVerificationEnabled(true);
        }

        return options;
    }

    String commandResultTopic() {
        return commandResultTopic;
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
            Mqttv3ClientManager mqttClientManager
    ) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        mqttClientManager,
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
            Mqttv3ClientManager mqttClientManager
    ) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        mqttClientManager,
                        stateTopic
                );

        adapter.setQos(qos);
        adapter.setOutputChannel(mqttStateInputChannel());

        return adapter;
    }

    @Bean
    public MessageChannel mqttCommandResultInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttCommandResultInbound(
            Mqttv3ClientManager mqttClientManager
    ) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        mqttClientManager,
                        commandResultTopic
                );

        adapter.setQos(qos);
        adapter.setOutputChannel(mqttCommandResultInputChannel());

        return adapter;
    }

    @Bean
    public MessageChannel mqttActivationInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttActivationInbound(
            Mqttv3ClientManager mqttClientManager
    ) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        mqttClientManager,
                        MqttTopicSecurity.activationResponseSubscription());
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttActivationInputChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttActivationInputChannel")
    public MessageHandler mqttActivationHandler(BrokerActivationService activationService) {
        return message -> {
            Object topicHeader = message.getHeaders().get("mqtt_receivedTopic");
            Object retainedHeader = message.getHeaders().get("mqtt_receivedRetained");
            if (!(topicHeader instanceof String topic)) {
                log.warn("Rejected activation response without a valid topic.");
                return;
            }
            boolean retained = Boolean.TRUE.equals(retainedHeader)
                    || "true".equalsIgnoreCase(String.valueOf(retainedHeader));
            Object payload = message.getPayload();
            byte[] bytes = payload instanceof byte[] raw
                    ? raw : payload.toString().getBytes(StandardCharsets.UTF_8);
            if (!activationService.consumeResponse(topic, bytes, retained)) {
                log.warn("Rejected MQTT activation response.");
            }
        };
    }

    @Bean
    public MqttCommandResultConsumer mqttCommandResultConsumer(
            MqttCommandStatusStore statusStore
    ) {
        return new MqttCommandResultConsumer(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                statusStore,
                java.time.Duration.ofSeconds(commandResultMaxAgeSeconds)
        );
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttCommandResultInputChannel")
    public MessageHandler mqttCommandResultHandler(
            MqttCommandResultConsumer consumer
    ) {
        return message -> {
            Object topicHeader = message.getHeaders().get("mqtt_receivedTopic");
            if (!(topicHeader instanceof String topic)) {
                log.warn("Rejected MQTT command result without a valid topic.");
                return;
            }
            Object payload = message.getPayload();
            byte[] bytes = payload instanceof byte[] raw
                    ? raw
                    : payload.toString().getBytes(StandardCharsets.UTF_8);
            if (!consumer.consume(topic, bytes)) {
                log.warn("Rejected MQTT command result.");
            }
        };
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
            Mqttv3ClientManager mqttClientManager
    ) {
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(
                        mqttClientManager
                );

        handler.setAsync(true);
        handler.setDefaultQos(qos);

        return handler;
    }
}
