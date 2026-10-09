package com.nexaiot.server.device.template;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/device-templates")
public class DeviceTemplateController {

    private final DeviceTemplateService deviceTemplateService;

    public DeviceTemplateController(DeviceTemplateService deviceTemplateService) {
        this.deviceTemplateService = deviceTemplateService;
    }

    @GetMapping
    public ResponseEntity<List<DeviceTemplate>> getTemplates() {
        return ResponseEntity.ok(deviceTemplateService.getEnabledTemplates());
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<DeviceTemplate> getTemplate(@PathVariable String templateId) {
        return ResponseEntity.ok(deviceTemplateService.getTemplate(templateId));
    }
}
