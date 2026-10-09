package com.nexaiot.server.device.template;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestValidator;

import jakarta.annotation.PostConstruct;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DeviceTemplateService {

    private static final List<String> TEMPLATE_RESOURCES = List.of(
            "device-templates/temperature-sensor.json",
            "device-templates/temperature-humidity-sensor.json",
            "device-templates/soil-moisture-sensor.json",
            "device-templates/smart-switch.json",
            "device-templates/smart-fan.json",
            "device-templates/energy-meter.json",
            "device-templates/weather-station.json",
            "device-templates/dc-motor-controller.json"
    );

    private final JsonMapper jsonMapper;
    private Map<String, DeviceTemplate> templates = Map.of();

    public DeviceTemplateService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @PostConstruct
    public void loadTemplates() {
        Map<String, DeviceTemplate> loaded = new LinkedHashMap<>();

        for (String resourcePath : TEMPLATE_RESOURCES) {
            DeviceTemplate template = readTemplate(resourcePath);
            validateTemplate(template, resourcePath);

            if (loaded.putIfAbsent(template.getTemplateId(), template) != null) {
                throw new IllegalStateException(
                        "Duplicate device templateId: " + template.getTemplateId()
                );
            }
        }

        templates = Collections.unmodifiableMap(loaded);
    }

    public List<DeviceTemplate> getEnabledTemplates() {
        return templates.values().stream()
                .filter(DeviceTemplate::isEnabled)
                .toList();
    }

    public DeviceTemplate getTemplate(String templateId) {
        DeviceTemplate template = templates.get(templateId);

        if (template == null || !template.isEnabled()) {
            throw new DeviceTemplateNotFoundException(
                    "Device template not found: " + templateId
            );
        }

        return template;
    }

    private DeviceTemplate readTemplate(String resourcePath) {
        try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            return jsonMapper.readValue(inputStream, DeviceTemplate.class);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Unable to load device template resource: " + resourcePath,
                    ex
            );
        }
    }

    private void validateTemplate(DeviceTemplate template, String resourcePath) {
        if (template == null
                || template.getTemplateId() == null
                || template.getTemplateId().isBlank()
                || template.getVersion() == null
                || template.getVersion() <= 0
                || template.getName() == null
                || template.getName().isBlank()
                || template.getCapabilityManifest() == null) {
            throw new IllegalStateException("Invalid device template: " + resourcePath);
        }

        CapabilityManifest manifest = template.getCapabilityManifest();
        if (manifest.getSource() != com.nexaiot.server.device.CapabilityManifestSource.TEMPLATE
                || !template.getTemplateId().equals(manifest.getTemplateId())
                || !CapabilityManifestValidator.isValid(manifest)) {
            throw new IllegalStateException("Invalid capability manifest in template: " + resourcePath);
        }
    }
}
