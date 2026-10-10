package com.nexaiot.server.device;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nexaiot.server.device.template.DeviceTemplateService;

import tools.jackson.databind.json.JsonMapper;

class DeviceControlCapabilityTest {

    private DeviceRepository repository;
    private DeviceService deviceService;
    private Device currentDevice;

    @BeforeEach
    void setUp() {
        repository = (DeviceRepository) Proxy.newProxyInstance(
                DeviceRepository.class.getClassLoader(),
                new Class<?>[]{DeviceRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByIdAndOwnerEmail")) {
                        return Optional.of(currentDevice);
                    }
                    return null;
                }
        );

        DeviceTemplateService templateService = new DeviceTemplateService(
                JsonMapper.builder().build()
        );
        deviceService = new DeviceService(repository, templateService);
    }

    @Test
    void rejectsPowerControlWhenEffectiveManifestHasNoPowerControl() {
        Device device = manifestDevice(measurement("temperature"));

        assertThrows(
                IllegalArgumentException.class,
                () -> deviceService.getControllableDevice(device.getId(), "owner@example.com")
        );
    }

    @Test
    void allowsPowerControlForHybridManifest() {
        Device device = manifestDevice(
                measurement("temperature"),
                control("power")
        );

        assertDoesNotThrow(
                () -> deviceService.getControllableDevice(device.getId(), "owner@example.com")
        );
    }

    private Device manifestDevice(CapabilityDefinition... definitions) {
        Device device = new Device();
        device.setId("device-id");
        device.setOwnerEmail("owner@example.com");

        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        manifest.setCapabilities(java.util.List.of(definitions));
        device.setCapabilityManifest(manifest);
        currentDevice = device;
        return device;
    }

    private CapabilityDefinition measurement(String key) {
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setKey(key);
        definition.setName(key);
        definition.setCategory(CapabilityCategory.MEASUREMENT);
        definition.setDataType(CapabilityDataType.NUMBER);
        return definition;
    }

    private CapabilityDefinition control(String key) {
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setKey(key);
        definition.setName(key);
        definition.setCategory(CapabilityCategory.CONTROL);
        definition.setDataType(CapabilityDataType.BOOLEAN);
        definition.setControl(new CapabilityControl(CapabilityControlType.SWITCH));
        return definition;
    }
}
