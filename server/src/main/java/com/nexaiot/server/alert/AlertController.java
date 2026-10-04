package com.nexaiot.server.alert;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexaiot.server.alert.dto.AlertResponse;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAlerts(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                alertService.getAlerts(authentication.getName())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> getAlert(
            @PathVariable String id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                alertService.getAlert(
                        id,
                        authentication.getName()
                )
        );
    }

    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<AlertResponse> acknowledgeAlert(
            @PathVariable String id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                alertService.acknowledgeAlert(
                        id,
                        authentication.getName()
                )
        );
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<AlertResponse> resolveAlert(
            @PathVariable String id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                alertService.resolveAlert(
                        id,
                        authentication.getName()
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(
            @PathVariable String id,
            Authentication authentication
    ) {
        alertService.deleteAlert(
                id,
                authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }
}
