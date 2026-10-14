package com.nexaiot.server.energy;

import java.util.List;

public record EnergyAnalyticsResponse(
        String deviceId,
        String period,
        boolean available,
        String source,
        String unit,
        Double totalEnergyKWh,
        Double averagePowerW,
        Double peakPowerW,
        int sampleCount,
        double coverage,
        String coverageBasis,
        String trendUnit,
        List<EnergyTrendPoint> trend
) {
}
