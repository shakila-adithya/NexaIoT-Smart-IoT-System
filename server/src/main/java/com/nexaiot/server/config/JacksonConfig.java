package com.nexaiot.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The MQTT management adapter uses Jackson 2's ObjectMapper API.
 *
 * Spring Boot 4 configures Jackson 3 for the web stack, so it does not
 * automatically expose a com.fasterxml.jackson.databind.ObjectMapper bean.
 */
@Configuration
public class JacksonConfig {

    @Bean
    ObjectMapper mqttManagementObjectMapper() {
        return new ObjectMapper();
    }
}
