package com.nexaiot.server.report;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.nexaiot.server.report.dto.GenerateReportRequest;
import com.nexaiot.server.report.dto.GeneratedReportResponse;
@RestController
@RequestMapping("/api/reports")
public class GeneratedReportController {
    private final GeneratedReportService service;
    public GeneratedReportController(GeneratedReportService service) { this.service = service; }
    @GetMapping public ResponseEntity<List<GeneratedReportResponse>> list(Authentication auth) { return ResponseEntity.ok(service.list(auth.getName())); }
    @GetMapping("/{id}") public ResponseEntity<GeneratedReportResponse> get(@PathVariable String id, Authentication auth) { return ResponseEntity.ok(service.get(id, auth.getName())); }
    @PostMapping public ResponseEntity<GeneratedReportResponse> generate(@RequestBody GenerateReportRequest request, Authentication auth) { return ResponseEntity.ok(service.generate(request, auth.getName())); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) { service.delete(id, auth.getName()); return ResponseEntity.noContent().build(); }
}
