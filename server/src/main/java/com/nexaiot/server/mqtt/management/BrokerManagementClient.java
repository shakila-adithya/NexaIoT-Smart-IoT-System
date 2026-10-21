package com.nexaiot.server.mqtt.management;

public interface BrokerManagementClient {

    BrokerDeviceIdentity createDeviceIdentity(
            String username,
            String clientId,
            String password
    );

    BrokerOperationResult assignDevicePermissions(String username, String deviceKey);

    BrokerOperationResult createDeviceRolePermissions(String deviceKey);

    BrokerOperationResult assignDeviceRole(String username, String deviceKey);

    BrokerDeviceProvisioningStatus verifyDeviceProvisioning(String username, String deviceKey);

    BrokerDeviceIdentityStatus verifyDeviceIdentity(String username);

    BrokerOperationResult disableDeviceIdentity(String username);

    BrokerOperationResult revokeDeviceIdentity(String username);

    BrokerRoleStatus inspectRole(String roleName);

    BrokerOperationResult deleteRole(String roleName);

    BrokerDeviceIdentityStatus inspectEnrollmentStatus(String username);
}
