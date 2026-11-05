package com.nexaiot.server.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RateLimitProperties {

    private final int loginMax;
    private final long loginWindowSeconds;
    private final int registerMax;
    private final long registerWindowSeconds;
    private final int passwordResetMax;
    private final long passwordResetWindowSeconds;
    private final int deviceCommandMax;
    private final long deviceCommandWindowSeconds;
    private final int reportMax;
    private final long reportWindowSeconds;
    private final int pairingClaimMax;
    private final long pairingClaimWindowSeconds;

    @Autowired
    public RateLimitProperties(
            @Value("${rate-limit.login.max:8}") int loginMax,
            @Value("${rate-limit.login.window-seconds:60}") long loginWindowSeconds,
            @Value("${rate-limit.register.max:5}") int registerMax,
            @Value("${rate-limit.register.window-seconds:3600}") long registerWindowSeconds,
            @Value("${rate-limit.password-reset.max:5}") int passwordResetMax,
            @Value("${rate-limit.password-reset.window-seconds:900}") long passwordResetWindowSeconds,
            @Value("${rate-limit.device-command.max:30}") int deviceCommandMax,
            @Value("${rate-limit.device-command.window-seconds:60}") long deviceCommandWindowSeconds,
            @Value("${rate-limit.report.max:5}") int reportMax,
            @Value("${rate-limit.report.window-seconds:300}") long reportWindowSeconds,
            @Value("${rate-limit.pairing-claim.max:10}") int pairingClaimMax,
            @Value("${rate-limit.pairing-claim.window-seconds:60}") long pairingClaimWindowSeconds
    ) {
        this.loginMax = loginMax;
        this.loginWindowSeconds = loginWindowSeconds;
        this.registerMax = registerMax;
        this.registerWindowSeconds = registerWindowSeconds;
        this.passwordResetMax = passwordResetMax;
        this.passwordResetWindowSeconds = passwordResetWindowSeconds;
        this.deviceCommandMax = deviceCommandMax;
        this.deviceCommandWindowSeconds = deviceCommandWindowSeconds;
        this.reportMax = reportMax;
        this.reportWindowSeconds = reportWindowSeconds;
        this.pairingClaimMax = pairingClaimMax;
        this.pairingClaimWindowSeconds = pairingClaimWindowSeconds;
    }

    /** Compatibility constructor for focused unit tests and existing callers. */
    public RateLimitProperties(
            int loginMax, long loginWindowSeconds,
            int registerMax, long registerWindowSeconds,
            int passwordResetMax, long passwordResetWindowSeconds,
            int deviceCommandMax, long deviceCommandWindowSeconds,
            int reportMax, long reportWindowSeconds
    ) {
        this(loginMax, loginWindowSeconds, registerMax, registerWindowSeconds,
                passwordResetMax, passwordResetWindowSeconds, deviceCommandMax,
                deviceCommandWindowSeconds, reportMax, reportWindowSeconds, 10, 60);
    }

    public int getLoginMax() { return loginMax; }
    public long getLoginWindowSeconds() { return loginWindowSeconds; }
    public int getRegisterMax() { return registerMax; }
    public long getRegisterWindowSeconds() { return registerWindowSeconds; }
    public int getPasswordResetMax() { return passwordResetMax; }
    public long getPasswordResetWindowSeconds() { return passwordResetWindowSeconds; }
    public int getDeviceCommandMax() { return deviceCommandMax; }
    public long getDeviceCommandWindowSeconds() { return deviceCommandWindowSeconds; }
    public int getReportMax() { return reportMax; }
    public long getReportWindowSeconds() { return reportWindowSeconds; }
    public int getPairingClaimMax() { return pairingClaimMax; }
    public long getPairingClaimWindowSeconds() { return pairingClaimWindowSeconds; }
}
