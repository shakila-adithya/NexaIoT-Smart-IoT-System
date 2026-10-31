package com.nexaiot.server.firmware;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nexaiot.server.firmware.dto.FirmwarePackageResponse;

@RestController
@RequestMapping("/api/admin/firmware")
public class FirmwarePackageController {
    private final FirmwarePackageService service;

    public FirmwarePackageController(FirmwarePackageService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<FirmwarePackageResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FirmwarePackageResponse> upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam("version") String version,
            @RequestParam("compatibleDeviceTypes") String compatibleDeviceTypes
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(authentication.getName(), file, version, compatibleDeviceTypes));
    }
}
