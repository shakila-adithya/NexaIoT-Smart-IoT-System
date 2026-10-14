package com.nexaiot.server.energy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestAdapter;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceNotFoundException;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.sensor.SensorReading;
import com.nexaiot.server.sensor.SensorReadingRepository;

@Service
public class EnergyAnalyticsService {

    private static final long MAX_INTEGRATION_GAP_SECONDS = 15 * 60;

    private final DeviceRepository deviceRepository;
    private final SensorReadingRepository readingRepository;

    public EnergyAnalyticsService(
            DeviceRepository deviceRepository,
            SensorReadingRepository readingRepository
    ) {
        this.deviceRepository = deviceRepository;
        this.readingRepository = readingRepository;
    }

    public EnergyAnalyticsResponse analyze(String deviceId, String ownerEmail, String period) {
        Duration duration = duration(period);
        String owner = normalize(ownerEmail);
        Device device = deviceRepository.findByIdAndOwnerEmail(deviceId, owner)
                .orElseThrow(() -> new DeviceNotFoundException("Device not found"));
        Instant end = Instant.now();
        Instant start = end.minus(duration);
        List<SensorReading> readings = readingRepository
                .findByDeviceIdAndReceivedAtBetweenOrderByReceivedAtAsc(device.getId(), start, end);

        CapabilityManifest manifest = CapabilityManifestAdapter.effectiveManifest(device);
        List<CapabilityDefinition> definitions = manifest.getCapabilities() == null
                ? List.of()
                : manifest.getCapabilities().stream()
                .filter(this::isNumericNonControl)
                .toList();

        CapabilityDefinition direct = find(definitions, "energyConsumption", "kWh", "Wh");
        if (direct != null) {
            return directEnergy(deviceId, period, start, end, readings, direct);
        }

        CapabilityDefinition power = find(definitions, "power", "W");
        CapabilityDefinition voltage = find(definitions, "voltage", "V");
        CapabilityDefinition current = find(definitions, "current", "A");
        if (power != null) {
            return integratedPower(deviceId, period, start, end, readings,
                    reading -> valueFor("power", reading), "POWER_INTEGRATION");
        }
        if (voltage != null && current != null) {
            return integratedPower(deviceId, period, start, end, readings,
                    reading -> multiply(valueFor("voltage", reading), valueFor("current", reading)), "VOLTAGE_CURRENT");
        }

        return unavailable(deviceId, period);
    }

    private EnergyAnalyticsResponse directEnergy(
            String deviceId,
            String period,
            Instant start,
            Instant end,
            List<SensorReading> readings,
            CapabilityDefinition definition
    ) {
        List<Sample> samples = collapseDuplicateTimestamps(readings.stream()
                .sorted(Comparator.comparing(SensorReading::getReceivedAt))
                .map(reading -> new Sample(reading.getReceivedAt(), valueFor(definition.getKey(), reading)))
                .filter(sample -> sample.value() != null && Double.isFinite(sample.value()))
                .toList());
        if (samples.size() < 2) return unavailable(deviceId, period);

        double first = samples.get(0).value();
        double last = samples.get(samples.size() - 1).value();
        if (last < first) return unavailable(deviceId, period);
        double multiplier = "Wh".equalsIgnoreCase(definition.getUnit()) ? 0.001 : 1;
        double totalKWh = (last - first) * multiplier;
        List<EnergyTrendPoint> trend = bucket(samples, start, period, multiplier);
        return new EnergyAnalyticsResponse(deviceId, period, true, "DIRECT_ENERGY", "kWh",
                totalKWh, null, null, samples.size(), coverage(start, end, samples), "METER_SPAN", "kWh", trend);
    }

    private EnergyAnalyticsResponse integratedPower(
            String deviceId,
            String period,
            Instant start,
            Instant end,
            List<SensorReading> readings,
            ValueExtractor extractor,
            String source
    ) {
        List<Sample> samples = collapseDuplicateTimestamps(readings.stream()
                .sorted(Comparator.comparing(SensorReading::getReceivedAt))
                .map(reading -> new Sample(reading.getReceivedAt(), extractor.value(reading)))
                .filter(sample -> sample.value() != null && Double.isFinite(sample.value()) && sample.value() >= 0)
                .toList());
        if (samples.isEmpty()) return unavailable(deviceId, period);

        double wattHours = 0;
        long coveredSeconds = 0;
        for (int index = 1; index < samples.size(); index++) {
            Sample previous = samples.get(index - 1);
            Sample current = samples.get(index);
            long gap = Duration.between(previous.timestamp(), current.timestamp()).getSeconds();
            if (gap > 0 && gap <= MAX_INTEGRATION_GAP_SECONDS) {
                wattHours += (previous.value() + current.value()) / 2 * (gap / 3600d);
                coveredSeconds += gap;
            }
        }
        double average = samples.stream().mapToDouble(Sample::value).average().orElse(Double.NaN);
        double peak = samples.stream().mapToDouble(Sample::value).max().orElse(Double.NaN);
        return new EnergyAnalyticsResponse(deviceId, period, true, source, "kWh",
                wattHours / 1000d, average, peak, samples.size(),
                Math.min(1d, coveredSeconds / (double) Math.max(1, Duration.between(start, end).getSeconds())),
                "INTERVAL_COVERAGE", "W", bucket(samples, start, period, 1));
    }

    private List<EnergyTrendPoint> bucket(List<Sample> samples, Instant start, String period, double multiplier) {
        long bucketSeconds = switch (period) {
            case "1h" -> 300;
            case "6h" -> 900;
            case "24h" -> 3600;
            case "7d" -> 21600;
            default -> throw new IllegalArgumentException("Unsupported energy period.");
        };
        Map<Instant, List<Double>> buckets = new LinkedHashMap<>();
        for (Sample sample : samples) {
            long elapsed = Math.max(0, Duration.between(start, sample.timestamp()).getSeconds());
            Instant bucketStart = start.plusSeconds((elapsed / bucketSeconds) * bucketSeconds);
            buckets.computeIfAbsent(bucketStart, ignored -> new ArrayList<>()).add(sample.value() * multiplier);
        }
        return buckets.entrySet().stream()
                .map(entry -> new EnergyTrendPoint(entry.getKey(), entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElseThrow()))
                .toList();
    }

    private double coverage(Instant start, Instant end, List<Sample> samples) {
        if (samples.size() < 2) return 0;
        long span = Duration.between(samples.get(0).timestamp(), samples.get(samples.size() - 1).timestamp()).getSeconds();
        return Math.min(1d, span / (double) Math.max(1, Duration.between(start, end).getSeconds()));
    }

    private List<Sample> collapseDuplicateTimestamps(List<Sample> samples) {
        List<Sample> collapsed = new ArrayList<>();
        for (Sample sample : samples) {
            if (!collapsed.isEmpty()
                    && collapsed.get(collapsed.size() - 1).timestamp().equals(sample.timestamp())) {
                Sample previous = collapsed.remove(collapsed.size() - 1);
                collapsed.add(new Sample(sample.timestamp(), (previous.value() + sample.value()) / 2d));
            } else {
                collapsed.add(sample);
            }
        }
        return collapsed;
    }

    private CapabilityDefinition find(List<CapabilityDefinition> definitions, String key, String... units) {
        return definitions.stream()
                .filter(definition -> key.equals(definition.getKey()) || key.equals(definition.getSemanticType()))
                .filter(definition -> units.length == 0 || java.util.Arrays.stream(units).anyMatch(unit -> unit.equalsIgnoreCase(definition.getUnit())))
                .findFirst().orElse(null);
    }

    private boolean isNumericNonControl(CapabilityDefinition definition) {
        return definition.getCategory() != CapabilityCategory.CONTROL
                && definition.getDataType() == CapabilityDataType.NUMBER;
    }

    private Double valueFor(String key, SensorReading reading) {
        if (reading.getMetrics() == null) return null;
        return reading.getMetrics().get(key);
    }

    private Double multiply(Double first, Double second) {
        return first == null || second == null ? null : first * second;
    }

    private EnergyAnalyticsResponse unavailable(String deviceId, String period) {
        return new EnergyAnalyticsResponse(deviceId, period, false, "UNAVAILABLE", "kWh",
                null, null, null, 0, 0, "UNAVAILABLE", "W", List.of());
    }

    private Duration duration(String period) {
        return switch (period) {
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            default -> throw new IllegalArgumentException("Unsupported energy period. Use 1h, 6h, 24h, or 7d.");
        };
    }

    private String normalize(String value) {
        return value.trim().toLowerCase();
    }

    private record Sample(Instant timestamp, Double value) { }

    @FunctionalInterface
    private interface ValueExtractor { Double value(SensorReading reading); }
}
