package com.nexaiot.server.energy;

import java.time.Instant;

public record EnergyTrendPoint(Instant bucketStart, Double value) {
}
