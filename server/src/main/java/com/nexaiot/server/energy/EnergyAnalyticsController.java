package com.nexaiot.server.energy;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices/{deviceId}/energy")
public class EnergyAnalyticsController {

    private final EnergyAnalyticsService energyAnalyticsService;

    public EnergyAnalyticsController(EnergyAnalyticsService energyAnalyticsService) {
        this.energyAnalyticsService = energyAnalyticsService;
    }

    @GetMapping
    public EnergyAnalyticsResponse getEnergy(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "24h") String period,
            Authentication authentication
    ) {
        return energyAnalyticsService.analyze(deviceId, authentication.getName(), period);
    }
}
