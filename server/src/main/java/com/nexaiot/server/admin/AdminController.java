package com.nexaiot.server.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexaiot.server.admin.dto.AdminDeviceResponse;
import com.nexaiot.server.admin.dto.AdminStatsResponse;
import com.nexaiot.server.admin.dto.AdminUserResponse;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> users(@RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok(adminService.users(search));
    }

    @GetMapping("/devices")
    public ResponseEntity<List<AdminDeviceResponse>> devices(@RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok(adminService.devices(search));
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> stats() {
        return ResponseEntity.ok(adminService.stats());
    }
}
