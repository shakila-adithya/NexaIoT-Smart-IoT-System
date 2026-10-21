package com.nexaiot.server;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.dao.support.PersistenceExceptionTranslator;

import com.nexaiot.server.alert.AlertRepository;
import com.nexaiot.server.alert.AlertRuleRepository;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.enrollment.DeviceEnrollmentOperationRepository;
import com.nexaiot.server.provisioning.ProvisioningSessionRepository;
import com.nexaiot.server.repository.UserRepository;
import com.nexaiot.server.repository.PasswordResetTokenRepository;
import com.nexaiot.server.sensor.SensorReadingRepository;
import com.nexaiot.server.task.MaintenanceTaskRepository;
import com.nexaiot.server.report.GeneratedReportRepository;

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
    ProvisioningSessionRepository provisioningSessionRepository() {
        return repositoryStub(ProvisioningSessionRepository.class);
    }

    @Bean
    DeviceEnrollmentOperationRepository deviceEnrollmentOperationRepository() {
        return repositoryStub(DeviceEnrollmentOperationRepository.class);
    }

    @Bean
    MongoTemplate mongoTemplate() {
        MongoDatabaseFactory factory = (MongoDatabaseFactory) Proxy.newProxyInstance(
                MongoDatabaseFactory.class.getClassLoader(),
                new Class<?>[]{MongoDatabaseFactory.class},
                (proxy, method, args) -> {
                    if (method.getReturnType() == PersistenceExceptionTranslator.class) {
                        return (PersistenceExceptionTranslator) exception -> null;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    return null;
                });
        MongoMappingContext mappingContext = new MongoMappingContext();
        MappingMongoConverter converter = new MappingMongoConverter(
                new DefaultDbRefResolver(factory), mappingContext);
        return new MongoTemplate(factory, converter);
    }

    @Bean
    SensorReadingRepository sensorReadingRepository() {
        return repositoryStub(SensorReadingRepository.class);
    }

    @Bean
    AlertRepository alertRepository() {
        return repositoryStub(AlertRepository.class);
    }

    @Bean
    AlertRuleRepository alertRuleRepository() {
        return repositoryStub(AlertRuleRepository.class);
    }

    @Bean
    MaintenanceTaskRepository maintenanceTaskRepository() {
        return repositoryStub(MaintenanceTaskRepository.class);
    }

    @Bean
    GeneratedReportRepository generatedReportRepository() {
        return repositoryStub(GeneratedReportRepository.class);
    }

    @Bean
    PasswordResetTokenRepository passwordResetTokenRepository() {
        return repositoryStub(PasswordResetTokenRepository.class);
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
