package com.nexaiot.server.admin;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexaiot.server.TestRepositoryConfiguration;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;
import com.nexaiot.server.security.JwtService;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration",
        "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRepositoryConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminApiSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private DeviceRepository deviceRepository;

    @BeforeEach
    void setUp() {
        when(deviceRepository.findAll()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of());
        when(userRepository.count()).thenReturn(0L);
    }

    @Test
    void adminInventoryRejectsAnonymousAndOrdinaryUsers() throws Exception {
        mockMvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());

        User ordinary = user("ordinary@example.com", "USER");
        when(userRepository.findByEmail(ordinary.getEmail())).thenReturn(Optional.of(ordinary));
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + jwtService.generateToken(ordinary)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUserCanReadInventory() throws Exception {
        User admin = user("admin@example.com", "ADMIN");
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + jwtService.generateToken(admin)))
                .andExpect(status().isOk());
    }

    private User user(String email, String role) {
        User user = new User("Test User", email, "hash", role);
        user.setTokenVersion(0);
        return user;
    }
}
