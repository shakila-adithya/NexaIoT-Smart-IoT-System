package com.nexaiot.server.mqtt.management;

public record BrokerDeviceProvisioningStatus(
        String username,
        String clientId,
        String roleName,
        int expectedAclCount,
        int verifiedAclCount,
        boolean noUnexpectedAllowRules,
        boolean verified
) {
}
