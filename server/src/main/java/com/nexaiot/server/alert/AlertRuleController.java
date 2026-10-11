package com.nexaiot.server.alert;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexaiot.server.alert.dto.AlertRuleRequest;
import com.nexaiot.server.alert.dto.AlertRuleResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/alert-rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> getRules(Authentication authentication) {
        return ResponseEntity.ok(alertRuleService.getRules(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> getRule(@PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(alertRuleService.getRule(id, authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<AlertRuleResponse> createRule(
            @Valid @RequestBody AlertRuleRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(alertRuleService.createRule(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> updateRule(
            @PathVariable String id,
            @Valid @RequestBody AlertRuleRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(alertRuleService.updateRule(id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id, Authentication authentication) {
        alertRuleService.deleteRule(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
