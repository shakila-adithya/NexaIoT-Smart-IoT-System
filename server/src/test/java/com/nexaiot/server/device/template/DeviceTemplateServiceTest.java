package com.nexaiot.server.device.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nexaiot.server.device.CapabilityManifestSource;
import com.nexaiot.server.device.DeviceCapabilities;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.device.DeviceService;
import com.nexaiot.server.device.DeviceMetricCapability;
import com.nexaiot.server.device.SensorCapability;
import com.nexaiot.server.device.dto.CreateDeviceRequest;
import com.nexaiot.server.device.dto.DeviceResponse;

import tools.jackson.databind.json.JsonMapper;

class DeviceTemplateServiceTest {

    private DeviceTemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new DeviceTemplateService(JsonMapper.builder().build());
        templateService.loadTemplates();
    }

    @Test
    void loadsAllEightEnabledTemplates() {
        assertEquals(8, templateService.getEnabledTemplates().size());
        assertEquals(
                List.of(
                        "temperature-sensor",
                        "temperature-humidity-sensor",
                        "soil-moisture-sensor",
                        "smart-switch",
                        "smart-fan",
                        "energy-meter",
                        "weather-station",
                        "dc-motor-controller"
                ),
                templateService.getEnabledTemplates().stream()
                        .map(DeviceTemplate::getTemplateId)
                        .toList()
        );
    }

    @Test
    void unknownTemplateReturnsNotFound() {
        assertThrows(
                DeviceTemplateNotFoundException.class,
                () -> templateService.getTemplate("does-not-exist")
        );
    }

    @Test
    void smartFanHasExpectedControls() {
        DeviceTemplate template = templateService.getTemplate("smart-fan");

        assertEquals(
                List.of("power", "fanSpeed", "mode"),
                template.getCapabilityManifest().getCapabilities().stream()
                        .map(definition -> definition.getKey())
                        .toList()
        );
        assertEquals(3, template.getCapabilityManifest().getCapabilities().size());
        assertEquals(3, template.getCapabilityManifest().getCapabilities().get(2).getOptions().size());
    }

    @Test
    void energyMeterUsesCounterForEnergyConsumption() {
        DeviceTemplate template = templateService.getTemplate("energy-meter");

        var energy = template.getCapabilityManifest().getCapabilities().stream()
                .filter(definition -> definition.getKey().equals("energyConsumption"))
                .findFirst()
                .orElseThrow();

        assertEquals("COUNTER", energy.getValueKind().name());
    }

    @Test
    void templateCreationSnapshotsManifestAndLegacyCreationStillWorks() {
        DeviceRepository repository = repositoryStub();

        DeviceTemplate template = templateService.getTemplate("smart-fan");
        DeviceService deviceService = new DeviceService(repository, templateService);

        CreateDeviceRequest templatedRequest = request("smart-fan");
        DeviceResponse templatedResponse = deviceService.createDevice(templatedRequest, "owner@example.com");

        assertEquals(CapabilityManifestSource.TEMPLATE, templatedResponse.getCapabilityManifest().getSource());
        assertNotSame(template.getCapabilityManifest(), templatedResponse.getCapabilityManifest());
        assertEquals("smart-fan", templatedResponse.getCapabilityManifest().getTemplateId());

        template.getCapabilityManifest().getCapabilities().get(0).setName("Changed Template Power");
        assertEquals("Power", templatedResponse.getCapabilityManifest().getCapabilities().get(0).getName());

        CreateDeviceRequest legacyRequest = request(null);
        DeviceCapabilities legacyCapabilities = new DeviceCapabilities();
        legacyCapabilities.setSensors(new LinkedHashSet<>(List.of(SensorCapability.TEMPERATURE)));
        legacyCapabilities.setDeviceMetrics(new LinkedHashSet<>(List.of(DeviceMetricCapability.BATTERY)));
        legacyRequest.setCapabilities(legacyCapabilities);

        DeviceResponse legacyResponse = deviceService.createDevice(legacyRequest, "owner@example.com");

        assertEquals(CapabilityManifestSource.LEGACY_ADAPTER,
                legacyResponse.getCapabilityManifest().getSource());
        assertTrue(legacyResponse.getCapabilityManifest().getCapabilities().stream()
                .anyMatch(definition -> definition.getKey().equals("temperature")));
    }

    private CreateDeviceRequest request(String templateId) {
        CreateDeviceRequest request = new CreateDeviceRequest();
        request.setName("Test Device");
        request.setType("Sensor");
        request.setLocation("Lab");
        request.setTemplateId(templateId);
        return request;
    }

    private DeviceRepository repositoryStub() {
        return (DeviceRepository) Proxy.newProxyInstance(
                DeviceRepository.class.getClassLoader(),
                new Class<?>[]{DeviceRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("existsByDeviceKey")) {
                        return false;
                    }
                    if (method.getName().equals("save")) {
                        return args[0];
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().equals(long.class)) {
                        return 0L;
                    }
                    return null;
                }
        );
    }
}
