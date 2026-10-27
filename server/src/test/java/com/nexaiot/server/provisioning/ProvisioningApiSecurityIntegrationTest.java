package com.nexaiot.server.provisioning;

import com.nexaiot.server.TestRepositoryConfiguration;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.enrollment.DeviceEnrollmentOperation;
import com.nexaiot.server.enrollment.DeviceEnrollmentOperationRepository;
import com.nexaiot.server.enrollment.CredentialDeliveryService;
import com.nexaiot.server.enrollment.EnrollmentRecoveryService;
import com.nexaiot.server.enrollment.EnrollmentState;
import com.nexaiot.server.enrollment.RecoveryAssessment;
import com.nexaiot.server.enrollment.ReconciliationOutcome;
import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;
import com.nexaiot.server.security.JwtService;
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

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration",
        "spring.task.scheduling.enabled=false",
        "rate-limit.pairing-claim.max=1",
        "rate-limit.pairing-claim.window-seconds=60"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRepositoryConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProvisioningApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private DeviceRepository deviceRepository;
    @MockitoBean
    private DeviceEnrollmentOperationRepository operationRepository;
    @MockitoBean
    private EnrollmentRecoveryService recoveryService;
    @MockitoBean
    private CredentialDeliveryService credentialDeliveryService;

    private User owner;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        owner = new User("Owner", "owner@example.com", "hash", "USER");
        owner.setTokenVersion(0);
        ownerToken = jwtService.generateToken(owner);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
    }

    @Test
    void protectedEnrollmentStatusRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/devices/device-1/enrollment-operations/operation-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void credentialDeliveryRejectsPlainHttpBeforeProofProcessing() throws Exception {
        mockMvc.perform(post("/api/devices/device-1/enrollment-operations/operation-1/credential-delivery")
                        .secure(false)
                        .contentType("application/json")
                        .content("{\"mqttClientId\":\"nexaiot-device-NEXA-1\",\"challenge\":\"c\",\"signature\":\"s\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exactPublicClaimRouteIsAllowedButSiblingRouteIsProtected() throws Exception {
        mockMvc.perform(post("/api/provisioning/pairing-sessions/session-1/claim")
                        .contentType("application/json")
                        .content("{\"pairingToken\":\"candidate\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INVALID")));

        mockMvc.perform(post("/api/provisioning/pairing-sessions/session-1/claim/extra")
                        .contentType("application/json")
                        .content("{\"pairingToken\":\"candidate\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedPublicClaimIsRejectedWithoutSensitiveResponse() throws Exception {
        mockMvc.perform(post("/api/provisioning/pairing-sessions/session-1/claim")
                        .contentType("application/json")
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("tokenHash"))));
    }

    @Test
    void authenticatedOwnerGetsSafeStatusAndCrossUserCannotReadIt() throws Exception {
        DeviceEnrollmentOperation operation = operation("owner@example.com");
        operation.setBrokerUsername("secret-client-identity");
        operation.setCredentialReference("secret-reference");
        when(operationRepository.findById("operation-1")).thenReturn(Optional.of(operation));

        mockMvc.perform(get("/api/devices/device-1/enrollment-operations/operation-1")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret-client-identity"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("credentialReference"))));

        User other = new User("Other", "other@example.com", "hash", "USER");
        other.setTokenVersion(0);
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(other));
        String otherToken = jwtService.generateToken(other);

        mockMvc.perform(get("/api/devices/device-1/enrollment-operations/operation-1")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("owner@example.com"))));
    }

    @Test
    void explicitReconciliationIsPostOnlyAndOwnerScoped() throws Exception {
        DeviceEnrollmentOperation operation = operation("owner@example.com");
        when(operationRepository.findById("operation-1")).thenReturn(Optional.of(operation));
        when(recoveryService.reconcile("operation-1", "owner@example.com"))
                .thenReturn(new RecoveryAssessment(
                        ReconciliationOutcome.RETRYABLE, "safe", true, false));

        mockMvc.perform(post("/api/devices/device-1/enrollment-operations/operation-1/reconcile")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        verify(recoveryService, times(1)).reconcile("operation-1", "owner@example.com");

        mockMvc.perform(get("/api/devices/device-1/enrollment-operations/operation-1?reconcile=true")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        verify(recoveryService, times(1)).reconcile("operation-1", "owner@example.com");
    }

    @Test
    void publicClaimRateLimitIsEnforced() throws Exception {
        String body = "{\"pairingToken\":\"candidate\"}";
        mockMvc.perform(post("/api/provisioning/pairing-sessions/session-a/claim")
                        .contentType("application/json").content(body).with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        }))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/provisioning/pairing-sessions/session-b/claim")
                        .contentType("application/json").content(body).with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests());
    }

    private DeviceEnrollmentOperation operation(String ownerEmail) {
        DeviceEnrollmentOperation operation = new DeviceEnrollmentOperation();
        operation.setId("operation-1");
        operation.setDeviceId("device-1");
        operation.setOwnerEmail(ownerEmail);
        operation.setState(EnrollmentState.REQUESTED);
        operation.setVersion(0);
        return operation;
    }
}
