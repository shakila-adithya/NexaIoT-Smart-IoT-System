package com.nexaiot.server;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;

import com.nexaiot.server.alert.AlertRepository;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.repository.UserRepository;
import com.nexaiot.server.sensor.SensorReadingRepository;

@TestConfiguration(proxyBeanMethods = false)
class TestRepositoryConfiguration {

    @Bean(name = "mqttOutboundChannel")
    MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    UserRepository userRepository() {
        return repositoryStub(UserRepository.class);
    }

    @Bean
    DeviceRepository deviceRepository() {
        return repositoryStub(DeviceRepository.class);
    }

    @Bean
    SensorReadingRepository sensorReadingRepository() {
        return repositoryStub(SensorReadingRepository.class);
    }

    @Bean
    AlertRepository alertRepository() {
        return repositoryStub(AlertRepository.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> T repositoryStub(Class<T> repositoryType) {
        return (T) Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[]{repositoryType},
                (proxy, method, args) -> {
                    if (method.getReturnType() == Optional.class) {
                        return Optional.empty();
                    }
                    if (method.getReturnType() == List.class) {
                        return List.of();
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    return null;
                }
        );
    }
}
