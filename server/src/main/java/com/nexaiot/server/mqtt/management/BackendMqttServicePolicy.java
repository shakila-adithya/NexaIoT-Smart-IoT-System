package com.nexaiot.server.mqtt.management;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class BackendMqttServicePolicy {

    public static final String USERNAME = "nexaiot-backend-lab";
    public static final String CLIENT_ID = "nexaiot-backend-lab";
    public static final String ROLE_NAME = "nexaiot-backend-lab-role";
    public static final String TELEMETRY_TOPIC = "nexaiot/devices/+/telemetry";
    public static final String STATE_TOPIC = "nexaiot/devices/+/state";
    public static final String COMMAND_RESULT_TOPIC = "nexaiot/devices/+/command-results";
    public static final String COMMAND_TOPIC = "nexaiot/devices/+/commands";

    private BackendMqttServicePolicy() {
    }

    public static List<ObjectNode> roleCommands(ObjectMapper mapper) {
        List<ObjectNode> commands = new ArrayList<>();
        ObjectNode role = mapper.createObjectNode();
        role.put("command", "createRole");
        role.put("rolename", ROLE_NAME);
        commands.add(role);
        commands.add(acl(mapper, "subscribePattern", TELEMETRY_TOPIC));
        commands.add(acl(mapper, "subscribePattern", STATE_TOPIC));
        commands.add(acl(mapper, "subscribePattern", COMMAND_RESULT_TOPIC));
        commands.add(acl(mapper, "publishClientReceive", TELEMETRY_TOPIC));
        commands.add(acl(mapper, "publishClientReceive", STATE_TOPIC));
        commands.add(acl(mapper, "publishClientReceive", COMMAND_RESULT_TOPIC));
        commands.add(acl(mapper, "unsubscribePattern", TELEMETRY_TOPIC));
        commands.add(acl(mapper, "unsubscribePattern", STATE_TOPIC));
        commands.add(acl(mapper, "unsubscribePattern", COMMAND_RESULT_TOPIC));
        commands.add(acl(mapper, "publishClientSend", COMMAND_TOPIC));
        return commands;
    }

    private static ObjectNode acl(ObjectMapper mapper, String aclType, String topic) {
        ObjectNode acl = mapper.createObjectNode();
        acl.put("command", "addRoleACL");
        acl.put("rolename", ROLE_NAME);
        acl.put("acltype", aclType);
        acl.put("topic", topic);
        acl.put("allow", true);
        acl.put("priority", 100);
        return acl;
    }
}
