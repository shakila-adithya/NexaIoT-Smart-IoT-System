package com.nexaiot.server.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexaiot.server.admin.dto.AdminDeviceResponse;
import com.nexaiot.server.admin.dto.AdminStatsResponse;
import com.nexaiot.server.admin.dto.AdminUserResponse;
import com.nexaiot.server.service.AccountDeletionService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final AccountDeletionService accountDeletionService;

    public AdminController(AdminService adminService, AccountDeletionService accountDeletionService) {
        this.adminService = adminService;
        this.accountDeletionService = accountDeletionService;
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

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id, org.springframework.security.core.Authentication authentication) {
        accountDeletionService.deleteUser(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable String id) {
        accountDeletionService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }
}
