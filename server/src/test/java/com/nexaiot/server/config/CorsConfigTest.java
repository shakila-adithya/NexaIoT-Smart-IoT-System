package com.nexaiot.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class CorsConfigTest {

    @Test
    void trimsAndKeepsMultipleConfiguredOrigins() {
        CorsConfig config = new CorsConfig(" http://localhost:5173, https://app.example.com ");

        assertEquals(
                List.of("http://localhost:5173", "https://app.example.com"),
                config.allowedOrigins()
        );
        assertEquals("http://localhost:5173", config.corsConfiguration().checkOrigin("http://localhost:5173"));
        assertEquals("https://app.example.com", config.corsConfiguration().checkOrigin("https://app.example.com"));
        assertEquals(null, config.corsConfiguration().checkOrigin("https://other.example.com"));
        assertEquals(false, config.corsConfiguration().getAllowCredentials());
        assertEquals(true, config.corsConfiguration().getAllowedHeaders().contains("Authorization"));
    }

    @Test
    void ignoresBlankAndWildcardOrigins() {
        CorsConfig config = new CorsConfig(" ,*, https://app.example.com,, ");

        assertEquals(List.of("https://app.example.com"), config.allowedOrigins());
    }
}
