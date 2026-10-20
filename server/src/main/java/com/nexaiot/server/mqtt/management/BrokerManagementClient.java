package com.nexaiot.server.mqtt.management;

public interface BrokerManagementClient {

    BrokerDeviceIdentity createDeviceIdentity(
            String username,
            String clientId,
            String password
    );

    BrokerOperationResult assignDevicePermissions(String username, String deviceKey);

    BrokerDeviceIdentityStatus verifyDeviceIdentity(String username);

    BrokerOperationResult disableDeviceIdentity(String username);

    BrokerOperationResult revokeDeviceIdentity(String username);

    BrokerDeviceIdentityStatus inspectEnrollmentStatus(String username);
}
