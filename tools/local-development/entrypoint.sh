#!/bin/sh
set -eu

mkdir -p /mosquitto/data
if [ ! -f /mosquitto/data/dynamic-security.json ] || [ ! -f /mosquitto/data/bootstrap-complete ]; then
    rm -f /mosquitto/data/dynamic-security.json /mosquitto/data/bootstrap-complete
    mosquitto_ctrl dynsec init /mosquitto/data/dynamic-security.json \
        "$NEXAIOT_TEST_ADMIN_USERNAME" "$NEXAIOT_TEST_ADMIN_PASSWORD"
    chmod 0700 /mosquitto/data/dynamic-security.json
    chown mosquitto:mosquitto /mosquitto/data/dynamic-security.json
    chmod 0600 /mosquitto/data/dynamic-security.json
fi

mosquitto -c /mosquitto/config/mosquitto.conf &
broker_pid=$!
trap 'kill "$broker_pid" 2>/dev/null || true' EXIT INT TERM

control() {
    mosquitto_ctrl \
        -u "$NEXAIOT_TEST_ADMIN_USERNAME" \
        -P "$NEXAIOT_TEST_ADMIN_PASSWORD" \
        -h localhost \
        -p 8883 \
        --cafile /mosquitto/certs/ca.crt \
        dynsec "$@"
}

ready=0
for attempt in $(seq 1 60); do
    if control getDefaultACLAccess >/dev/null 2>&1; then
        ready=1
        break
    fi
    if ! kill -0 "$broker_pid" 2>/dev/null; then
        exit 1
    fi
    sleep 1
done
if [ "$ready" -ne 1 ]; then
    exit 1
fi

if [ ! -f /mosquitto/data/bootstrap-complete ]; then
control setDefaultACLAccess publishClientSend deny
control setDefaultACLAccess publishClientReceive deny
control setDefaultACLAccess subscribe deny
control setDefaultACLAccess unsubscribe deny

control createRole nexaiot-backend-lab-role
control addRoleACL nexaiot-backend-lab-role subscribePattern 'nexaiot/devices/+/telemetry' allow 100
control addRoleACL nexaiot-backend-lab-role subscribePattern 'nexaiot/devices/+/state' allow 100
control addRoleACL nexaiot-backend-lab-role subscribePattern 'nexaiot/devices/+/command-results' allow 100
control addRoleACL nexaiot-backend-lab-role publishClientReceive 'nexaiot/devices/+/telemetry' allow 100
control addRoleACL nexaiot-backend-lab-role publishClientReceive 'nexaiot/devices/+/state' allow 100
control addRoleACL nexaiot-backend-lab-role publishClientReceive 'nexaiot/devices/+/command-results' allow 100
control addRoleACL nexaiot-backend-lab-role unsubscribePattern 'nexaiot/devices/+/telemetry' allow 100
control addRoleACL nexaiot-backend-lab-role unsubscribePattern 'nexaiot/devices/+/state' allow 100
control addRoleACL nexaiot-backend-lab-role unsubscribePattern 'nexaiot/devices/+/command-results' allow 100
control addRoleACL nexaiot-backend-lab-role publishClientSend 'nexaiot/devices/+/commands' allow 100
control addRoleACL nexaiot-backend-lab-role publishClientSend 'nexaiot/devices/+/activation-challenges' allow 100
control addRoleACL nexaiot-backend-lab-role subscribePattern 'nexaiot/devices/+/activation-responses' allow 100
control addRoleACL nexaiot-backend-lab-role publishClientReceive 'nexaiot/devices/+/activation-responses' allow 100
control addRoleACL nexaiot-backend-lab-role unsubscribePattern 'nexaiot/devices/+/activation-responses' allow 100
control createClient "$NEXAIOT_TEST_BACKEND_USERNAME"
control setClientId "$NEXAIOT_TEST_BACKEND_USERNAME" "$NEXAIOT_TEST_BACKEND_CLIENT_ID"
control setClientPassword "$NEXAIOT_TEST_BACKEND_USERNAME" "$NEXAIOT_TEST_BACKEND_PASSWORD"
control addClientRole "$NEXAIOT_TEST_BACKEND_USERNAME" nexaiot-backend-lab-role 100

control createRole nexaiot-local-management-role
control addRoleACL nexaiot-local-management-role publishClientSend '$CONTROL/dynamic-security/v1' allow 100
control addRoleACL nexaiot-local-management-role publishClientReceive '$CONTROL/dynamic-security/v1/response' allow 100
control addRoleACL nexaiot-local-management-role subscribeLiteral '$CONTROL/dynamic-security/v1/response' allow 100
control addRoleACL nexaiot-local-management-role unsubscribeLiteral '$CONTROL/dynamic-security/v1/response' allow 100
control addRoleACL nexaiot-local-management-role subscribePattern '$CONTROL/dynamic-security/v1/#' allow 100
control addRoleACL nexaiot-local-management-role unsubscribePattern '$CONTROL/dynamic-security/v1/#' allow 100
control createClient "$NEXAIOT_TEST_MANAGEMENT_USERNAME"
control setClientId "$NEXAIOT_TEST_MANAGEMENT_USERNAME" "$NEXAIOT_TEST_MANAGEMENT_CLIENT_ID"
control setClientPassword "$NEXAIOT_TEST_MANAGEMENT_USERNAME" "$NEXAIOT_TEST_MANAGEMENT_PASSWORD"
control addClientRole "$NEXAIOT_TEST_MANAGEMENT_USERNAME" nexaiot-local-management-role 100

touch /mosquitto/data/bootstrap-complete
fi
wait "$broker_pid"
