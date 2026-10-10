const legacyDefinitions = {
  TEMPERATURE: { key: "temperature", name: "Temperature", category: "MEASUREMENT", dataType: "NUMBER", unit: "°C", semanticType: "temperature", valueKind: "GAUGE", readOnly: true, chartable: true },
  HUMIDITY: { key: "humidity", name: "Humidity", category: "MEASUREMENT", dataType: "NUMBER", unit: "%", semanticType: "humidity", valueKind: "GAUGE", readOnly: true, chartable: true },
  SOIL_MOISTURE: { key: "soilMoisture", name: "Soil Moisture", category: "MEASUREMENT", dataType: "NUMBER", unit: "%", semanticType: "soilMoisture", valueKind: "GAUGE", readOnly: true, chartable: true },
  PRESSURE: { key: "pressure", name: "Pressure", category: "MEASUREMENT", dataType: "NUMBER", unit: "hPa", semanticType: "pressure", valueKind: "GAUGE", readOnly: true, chartable: true },
  LIGHT: { key: "light", name: "Light", category: "MEASUREMENT", dataType: "NUMBER", unit: "lux", semanticType: "light", valueKind: "GAUGE", readOnly: true, chartable: true },
  AIR_QUALITY: { key: "airQuality", name: "Air Quality", category: "MEASUREMENT", dataType: "NUMBER", unit: "AQI", semanticType: "airQuality", valueKind: "GAUGE", readOnly: true, chartable: true },
  BATTERY: { key: "battery", name: "Battery", category: "DEVICE_METRIC", dataType: "NUMBER", unit: "%", semanticType: "battery", valueKind: "GAUGE", readOnly: true, chartable: true },
  RSSI: { key: "rssi", name: "Signal Strength", category: "DEVICE_METRIC", dataType: "NUMBER", unit: "dBm", semanticType: "rssi", valueKind: "GAUGE", readOnly: true, chartable: true },
  ENERGY_CONSUMPTION: { key: "energyConsumption", name: "Energy Consumption", category: "MEASUREMENT", dataType: "NUMBER", unit: "kWh", semanticType: "energyConsumption", valueKind: "COUNTER", readOnly: true, chartable: true },
  DIGITAL_OUTPUT: { key: "digitalOutput", name: "Digital Output", category: "CONTROL", dataType: "BOOLEAN", semanticType: "digitalOutput", readOnly: false, chartable: false, control: { type: "SWITCH" } },
  TEMPERATURE_SETPOINT: { key: "temperatureSetpoint", name: "Temperature Set Point", category: "CONTROL", dataType: "NUMBER", unit: "°C", semanticType: "temperatureSetpoint", readOnly: false, chartable: false, control: { type: "SLIDER" } },
  AUTO_MODE: { key: "autoMode", name: "Auto Mode", category: "CONTROL", dataType: "BOOLEAN", semanticType: "autoMode", readOnly: false, chartable: false, control: { type: "SWITCH" } },
  ECO_SCHEDULE: { key: "ecoSchedule", name: "Eco Schedule", category: "CONTROL", dataType: "STRING", semanticType: "ecoSchedule", readOnly: false, chartable: false, control: { type: "SELECT" } },
};

export function normalizeCapabilityManifest(device) {
  const manifest = device?.capabilityManifest;
  if (manifest?.version === 1 && Array.isArray(manifest.capabilities)) {
    return manifest.capabilities;
  }

  const legacy = device?.capabilities;
  const values = [
    ...(Array.isArray(legacy?.sensors) ? legacy.sensors : []),
    ...(Array.isArray(legacy?.deviceMetrics) ? legacy.deviceMetrics : []),
    ...(Array.isArray(legacy?.controls) ? legacy.controls : []),
  ];

  return values.map((value) => legacyDefinitions[value]).filter(Boolean);
}

export function isManifestDevice(device) {
  return device?.capabilityManifest?.version === 1
    && Array.isArray(device.capabilityManifest.capabilities);
}
