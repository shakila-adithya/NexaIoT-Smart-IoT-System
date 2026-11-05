package com.nexaiot.server.provisioning;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexaiot.server.enrollment.BrokerActivationService;

/** Owner-authorized trigger for a broker-mediated activation challenge. */
@RestController
@RequestMapping("/api")
@ConditionalOnBean(BrokerActivationService.class)
public class BrokerActivationController {

    private final BrokerActivationService activationService;

    public BrokerActivationController(BrokerActivationService activationService) {
        this.activationService = activationService;
    }

    @PostMapping("/devices/{deviceId}/enrollment-operations/{operationId}/activation/challenge")
    public void issueChallenge(
            @PathVariable String deviceId,
            @PathVariable String operationId,
            Authentication authentication
    ) {
        activationService.issueChallenge(deviceId, operationId, authentication.getName());
    }
}
