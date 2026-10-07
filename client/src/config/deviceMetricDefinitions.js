import {
  BatteryMedium,
  Droplets,
  Gauge,
  Sprout,
  Sun,
  Thermometer,
  Wind,
  Wifi,
} from "lucide-react";

export const DEVICE_METRIC_DEFINITIONS = {
  TEMPERATURE: {
    source: "metrics",
    key: "temperature",
    label: "Temperature",
    unit: "°C",
    icon: Thermometer,
    tone: "cyan",
    color: "#08a9c4",
    domain: "temperature",
  },
  HUMIDITY: {
    source: "metrics",
    key: "humidity",
    label: "Humidity",
    unit: "%",
    icon: Droplets,
    tone: "blue",
    color: "#7568e8",
    domain: "percentage",
  },
  SOIL_MOISTURE: {
    source: "metrics",
    key: "soilMoisture",
    label: "Soil Moisture",
    unit: "%",
    icon: Sprout,
    tone: "green",
    color: "#16a57a",
    domain: "percentage",
  },
  PRESSURE: {
    source: "metrics",
    key: "pressure",
    label: "Pressure",
    unit: "hPa",
    icon: Gauge,
    tone: "violet",
    color: "#7d68df",
    domain: "dynamic",
  },
  LIGHT: {
    source: "metrics",
    key: "light",
    label: "Light",
    unit: "lux",
    icon: Sun,
    tone: "amber",
    color: "#e99a12",
    domain: "zeroBased",
  },
  AIR_QUALITY: {
    source: "metrics",
    key: "airQuality",
    label: "Air Quality",
    unit: "AQI",
    icon: Wind,
    tone: "blue",
    color: "#4d7ee8",
    domain: "zeroBased",
  },
  BATTERY: {
    source: "reading",
    key: "battery",
    label: "Battery",
    unit: "%",
    icon: BatteryMedium,
    tone: "violet",
    color: "#7d68df",
    domain: "percentage",
  },
  RSSI: {
    source: "reading",
    key: "rssi",
    label: "Signal Strength",
    unit: "dBm",
    icon: Wifi,
    tone: "green",
    color: "#16a57a",
    domain: "rssi",
  },
};

export function getMetricValue(definition, reading) {
  if (!definition || !reading) return null;

  return definition.source === "metrics"
    ? reading.metrics?.[definition.key] ?? null
    : reading[definition.key] ?? null;
}

export function getMetricDomain(definition, values) {
  const validValues = values.filter((value) => Number.isFinite(value));

  if (definition?.domain === "percentage") return [0, 100];
  if (definition?.domain === "rssi") return [-100, -30];
  if (validValues.length === 0) return [0, 100];

  const minimum = Math.min(...validValues);
  const maximum = Math.max(...validValues);

  if (definition?.domain === "zeroBased") {
    return [0, Math.max(1, Math.ceil(maximum * 1.1))];
  }

  const range = maximum - minimum;
  if (range < 4) {
    const center = Math.round((minimum + maximum) / 2);
    return [center - 2, center + 2];
  }

  const padding = Math.max(0.5, range * 0.1);
  return [Math.floor(minimum - padding), Math.ceil(maximum + padding)];
}
