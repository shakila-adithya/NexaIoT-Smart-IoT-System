package com.nexaiot.server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.nexaiot.server.device.dto.CreateDeviceRequest;
import com.nexaiot.server.device.dto.DeviceResponse;
import com.nexaiot.server.device.dto.UpdateDeviceRequest;
import com.nexaiot.server.device.template.DeviceTemplateService;

class DeviceConnectivityServiceTest {

    @Test
    void createAndRetrievePersistWifiConnectivityWithMqttProtocol() {
        DeviceRepository repository = repository();
        DeviceService service = service(repository);
        CreateDeviceRequest request = request();
        request.setConnectivity(connectivity(ConnectivityType.WIFI));

        DeviceResponse created = service.createDevice(request, "owner@example.com");
        DeviceResponse retrieved = service.getDevice(created.getId(), "owner@example.com");

        assertEquals(ConnectivityType.WIFI, retrieved.getConnectivity().getType());
        assertEquals(ConnectivityProtocol.MQTT, retrieved.getConnectivity().getProtocol());
    }

    @Test
    void updateConnectivityFromWifiToEthernet() {
        DeviceRepository repository = repository();
        DeviceService service = service(repository);
        CreateDeviceRequest createRequest = request();
        createRequest.setConnectivity(connectivity(ConnectivityType.WIFI));
        createRequest.setCapabilityManifest(capabilityManifest());
        DeviceResponse created = service.createDevice(createRequest, "owner@example.com");

        UpdateDeviceRequest updateRequest = new UpdateDeviceRequest();
        updateRequest.setConnectivity(connectivity(ConnectivityType.ETHERNET));
        DeviceResponse updated = service.updateDevice(
                created.getId(), updateRequest, "owner@example.com");

        assertEquals(ConnectivityType.ETHERNET, updated.getConnectivity().getType());
        assertEquals(ConnectivityProtocol.MQTT, updated.getConnectivity().getProtocol());
        assertEquals("temperature", updated.getCapabilityManifest().getCapabilities().get(0).getKey());
        assertEquals("OFFLINE", updated.getStatus());
    }

    @Test
    void unsupportedConnectivityValueIsRejectedDuringJsonDeserialization() {
        String json = "{\"connectivity\":{\"type\":\"SATELLITE\",\"protocol\":\"MQTT\"}}";

        assertThrows(Exception.class,
                () -> new ObjectMapper().readValue(json, CreateDeviceRequest.class));
    }

    @Test
    void nullConnectivityTypeIsRejected() {
        DeviceService service = service(repository());
        CreateDeviceRequest request = request();
        request.setConnectivity(new DeviceConnectivity());

        assertThrows(IllegalArgumentException.class,
                () -> service.createDevice(request, "owner@example.com"));
    }

    @Test
    void foreignDeviceCannotBeUpdated() {
        DeviceRepository repository = repository();
        DeviceService service = service(repository);
        CreateDeviceRequest request = request();
        request.setConnectivity(connectivity(ConnectivityType.WIFI));
        DeviceResponse created = service.createDevice(request, "owner@example.com");

        UpdateDeviceRequest updateRequest = new UpdateDeviceRequest();
        updateRequest.setConnectivity(connectivity(ConnectivityType.CELLULAR));

        assertThrows(DeviceNotFoundException.class,
                () -> service.updateDevice(created.getId(), updateRequest, "other@example.com"));
    }

    @Test
    void legacyDeviceWithoutConnectivityRemainsReadableAndStatusIsNotInferred() {
        DeviceService service = service(repository());

        DeviceResponse response = service.createDevice(request(), "owner@example.com");

        assertNull(response.getConnectivity());
        assertEquals("OFFLINE", response.getStatus());
    }

    private DeviceService service(DeviceRepository repository) {
        return new DeviceService(repository, (DeviceTemplateService) null);
    }

    private DeviceRepository repository() {
        Device[] stored = {new Device()};
        stored[0].setId("device-1");

        return (DeviceRepository) Proxy.newProxyInstance(
                DeviceRepository.class.getClassLoader(),
                new Class[]{DeviceRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByDeviceKey" -> false;
                    case "save" -> {
                        Device input = (Device) args[0];
                        if (input.getId() == null) input.setId(stored[0].getId());
                        stored[0] = input;
                        yield stored[0];
                    }
                    case "findByIdAndOwnerEmail" -> stored[0].getOwnerEmail() != null
                            && stored[0].getOwnerEmail().equals(args[1])
                            ? Optional.of(stored[0]) : Optional.empty();
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private CreateDeviceRequest request() {
        CreateDeviceRequest request = new CreateDeviceRequest();
        request.setName("Test device");
        request.setType("Sensor");
        request.setLocation("Lab");
        return request;
    }

    private CapabilityManifest capabilityManifest() {
        CapabilityDefinition temperature = new CapabilityDefinition();
        temperature.setKey("temperature");
        temperature.setName("Temperature");
        temperature.setCategory(CapabilityCategory.MEASUREMENT);
        temperature.setDataType(CapabilityDataType.NUMBER);

        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        manifest.setCapabilities(java.util.List.of(temperature));
        return manifest;
    }

    private DeviceConnectivity connectivity(ConnectivityType type) {
        DeviceConnectivity connectivity = new DeviceConnectivity();
        connectivity.setType(type);
        connectivity.setProtocol(ConnectivityProtocol.MQTT);
        return connectivity;
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == Optional.class) return Optional.empty();
        return null;
    }
}
