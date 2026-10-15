package com.nexaiot.server.task;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.nexaiot.server.task.dto.MaintenanceTaskRequest;
import com.nexaiot.server.task.dto.MaintenanceTaskResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tasks")
public class MaintenanceTaskController {
    private final MaintenanceTaskService service;
    public MaintenanceTaskController(MaintenanceTaskService service) { this.service = service; }
    @GetMapping public ResponseEntity<List<MaintenanceTaskResponse>> list(Authentication auth) { return ResponseEntity.ok(service.list(auth.getName())); }
    @GetMapping("/{id}") public ResponseEntity<MaintenanceTaskResponse> get(@PathVariable String id, Authentication auth) { return ResponseEntity.ok(service.get(id, auth.getName())); }
    @PostMapping public ResponseEntity<MaintenanceTaskResponse> create(@Valid @RequestBody MaintenanceTaskRequest request, Authentication auth) { return ResponseEntity.ok(service.create(request, auth.getName())); }
    @PutMapping("/{id}") public ResponseEntity<MaintenanceTaskResponse> update(@PathVariable String id, @Valid @RequestBody MaintenanceTaskRequest request, Authentication auth) { return ResponseEntity.ok(service.update(id, request, auth.getName())); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) { service.delete(id, auth.getName()); return ResponseEntity.noContent().build(); }
}
