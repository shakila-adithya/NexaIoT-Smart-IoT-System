package com.nexaiot.server.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.test.context.ActiveProfiles;

import com.nexaiot.server.TestRepositoryConfiguration;

@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "mqtt.startup.enabled=false",
        "admin.bootstrap.enabled=false",
        "spring.task.scheduling.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration"
})
@ActiveProfiles("local")
@Import(TestRepositoryConfiguration.class)
class LocalAdminBootstrapStartupTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void startsWithoutServletSecurityOrMqttClients() {
        assertFalse(applicationContext.containsBean("securityFilterChain"));
        assertTrue(applicationContext.containsBean("mqttOutboundChannel"));
        assertTrue(applicationContext.getBeansOfType(MqttPahoClientFactory.class).isEmpty());
        assertTrue(applicationContext.getBeansOfType(MessageProducer.class).isEmpty());
    }
}
