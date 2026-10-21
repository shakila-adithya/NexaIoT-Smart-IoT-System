package com.nexaiot.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.nexaiot.server.controller.AuthController;
import com.nexaiot.server.device.DeviceService;
import com.nexaiot.server.device.DeviceController;
import com.nexaiot.server.device.dto.DeviceResponse;
import com.nexaiot.server.dto.AuthResponse;
import com.nexaiot.server.report.GeneratedReportController;
import com.nexaiot.server.report.GeneratedReportService;
import com.nexaiot.server.report.dto.GenerateReportRequest;
import com.nexaiot.server.report.dto.GeneratedReportResponse;
import com.nexaiot.server.mqtt.MqttCommandPublisher;
import com.nexaiot.server.dto.ForgotPasswordRequest;
import com.nexaiot.server.dto.LoginRequest;
import com.nexaiot.server.dto.RegisterRequest;
import com.nexaiot.server.dto.ResetPasswordRequest;
import com.nexaiot.server.dto.UserResponse;
import com.nexaiot.server.service.AuthService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitControllerTest {

    @Test
    void loginAndForgotPasswordAreThrottledWithoutChangingSafeResponses() {
        RateLimitProperties properties = properties(2, 1, 1, 60, 1, 60, 1, 60, 1, 60);
        AuthController controller = new AuthController(new StubAuthService(), new RateLimitService(), properties);
        MockHttpServletRequest request = request("10.0.0.1");

        LoginRequest login = new LoginRequest();
        login.setEmail("owner@example.com");
        login.setPassword("password");
        controller.login(login, request);
        controller.login(login, request);
        assertThrows(RateLimitExceededException.class, () -> controller.login(login, request));

        RegisterRequest register = new RegisterRequest();
        register.setFullName("Owner");
        register.setEmail("new@example.com");
        register.setPassword("password");
        controller.register(register, request);
        assertThrows(RateLimitExceededException.class, () -> controller.register(register, request));

        ForgotPasswordRequest forgot = new ForgotPasswordRequest();
        forgot.setEmail("unknown@example.com");
        assertEquals(
                AuthService.GENERIC_RESET_RESPONSE,
                controller.forgotPassword(forgot, request).get("message")
        );
        assertThrows(RateLimitExceededException.class, () -> controller.forgotPassword(forgot, request));
    }

    @Test
    void deviceCommandsAreLimitedAfterOwnerScopedDeviceLookup() {
        RateLimitProperties properties = properties(10, 60, 10, 60, 10, 60, 1, 60, 10, 60);
        DirectChannel commandChannel = new DirectChannel();
        commandChannel.subscribe(message -> { });
        DeviceController controller = new DeviceController(
                new StubDeviceService(),
                new MqttCommandPublisher(commandChannel),
                null,
                new RateLimitService(),
                properties
        );

        var authentication = new UsernamePasswordAuthenticationToken("owner@example.com", null);
        var control = new com.nexaiot.server.device.dto.ControlDeviceRequest(true);
        controller.controlDevice("device-1", control, authentication);

        assertThrows(
                RateLimitExceededException.class,
                () -> controller.controlDevice("device-1", control, authentication)
        );
    }

    @Test
    void reportGenerationIsLimitedPerAuthenticatedOwner() {
        RateLimitProperties properties = properties(10, 60, 10, 60, 10, 60, 10, 60, 1, 60);
        GeneratedReportController controller = new GeneratedReportController(
                new StubReportService(), new RateLimitService(), properties
        );
        var authentication = new UsernamePasswordAuthenticationToken("owner@example.com", null);
        GenerateReportRequest request = new GenerateReportRequest();
        request.setDeviceId("device-1");
        request.setRange("1h");

        controller.generate(request, authentication, request("10.0.0.1"));

        assertThrows(
                RateLimitExceededException.class,
                () -> controller.generate(request, authentication, request("10.0.0.1"))
        );
    }

    private RateLimitProperties properties(
            int loginMax, long loginWindow,
            int registerMax, long registerWindow,
            int resetMax, long resetWindow,
            int commandMax, long commandWindow,
            int reportMax, long reportWindow
    ) {
        return new RateLimitProperties(
                loginMax, loginWindow, registerMax, registerWindow,
                resetMax, resetWindow, commandMax, commandWindow,
                reportMax, reportWindow
        );
    }

    private MockHttpServletRequest request(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }

    private static final class StubAuthService extends AuthService {
        private StubAuthService() {
            super(null, null, null, null, null, 30);
        }

        @Override
        public AuthResponse login(LoginRequest request) {
            return null;
        }

        @Override
        public UserResponse register(RegisterRequest request) {
            return null;
        }

        @Override
        public String forgotPassword(ForgotPasswordRequest request) {
            return GENERIC_RESET_RESPONSE;
        }

        @Override
        public String resetPassword(ResetPasswordRequest request) {
            return "Password reset successfully.";
        }
    }

    private static final class StubDeviceService extends DeviceService {
        private StubDeviceService() {
            super(null, null);
        }

        @Override
        public DeviceResponse getControllableDevice(String deviceId, String ownerEmail) {
            return new DeviceResponse(
                    deviceId, "NEXA-123", "Device", "Switch", "Lab", "ONLINE",
                    false, null, null, null, null, null, null
            );
        }
    }

    private static final class StubReportService extends GeneratedReportService {
        private StubReportService() {
            super(null, null, null, null);
        }

        @Override
        public GeneratedReportResponse generate(GenerateReportRequest request, String ownerEmail) {
            return null;
        }
    }
}
