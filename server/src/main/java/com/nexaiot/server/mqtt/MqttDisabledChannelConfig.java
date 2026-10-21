package com.nexaiot.server.mqtt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageChannel;
import org.springframework.integration.channel.DirectChannel;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mqtt.startup.enabled", havingValue = "false")
public class MqttDisabledChannelConfig {

    @Bean(name = "mqttOutboundChannel")
    @ConditionalOnMissingBean(name = "mqttOutboundChannel")
    MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }
}
