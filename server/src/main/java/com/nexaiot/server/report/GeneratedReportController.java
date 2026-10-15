package com.nexaiot.server.report;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.nexaiot.server.report.dto.GenerateReportRequest;
import com.nexaiot.server.report.dto.GeneratedReportResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.nexaiot.server.security.RateLimitProperties;
import com.nexaiot.server.security.RateLimitService;
@RestController
@RequestMapping("/api/reports")
public class GeneratedReportController {
    private final GeneratedReportService service;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    public GeneratedReportController(
            GeneratedReportService service,
            RateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties
    ) {
        this.service = service;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
    }
    @GetMapping public ResponseEntity<List<GeneratedReportResponse>> list(Authentication auth) { return ResponseEntity.ok(service.list(auth.getName())); }
    @GetMapping("/{id}") public ResponseEntity<GeneratedReportResponse> get(@PathVariable String id, Authentication auth) { return ResponseEntity.ok(service.get(id, auth.getName())); }
    @PostMapping public ResponseEntity<GeneratedReportResponse> generate(@Valid @RequestBody GenerateReportRequest request, Authentication auth, HttpServletRequest httpRequest) {
        rateLimitService.requireAllowed("report-user", auth.getName(), rateLimitProperties.getReportMax(), rateLimitProperties.getReportWindowSeconds());
        return ResponseEntity.ok(service.generate(request, auth.getName()));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) { service.delete(id, auth.getName()); return ResponseEntity.noContent().build(); }
}
