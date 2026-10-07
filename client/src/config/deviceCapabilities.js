export const CAPABILITY_GROUPS = [
  {
    key: "sensors",
    label: "Sensors",
    options: [
      ["TEMPERATURE", "Temperature"],
      ["HUMIDITY", "Humidity"],
      ["SOIL_MOISTURE", "Soil moisture"],
      ["PRESSURE", "Pressure"],
      ["LIGHT", "Light"],
      ["AIR_QUALITY", "Air quality"],
    ],
  },
  {
    key: "deviceMetrics",
    label: "Device metrics",
    options: [
      ["BATTERY", "Battery level"],
      ["RSSI", "Signal strength"],
    ],
  },
  {
    key: "controls",
    label: "Controls",
    options: [
      ["DIGITAL_OUTPUT", "Digital output"],
      ["TEMPERATURE_SETPOINT", "Temperature set point"],
      ["AUTO_MODE", "Auto mode"],
      ["ECO_SCHEDULE", "Eco schedule"],
    ],
  },
];

export function emptyCapabilities() {
  return {
    sensors: [],
    deviceMetrics: [],
    controls: [],
  };
}

export function normalizeCapabilities(capabilities) {
  return {
    sensors: Array.isArray(capabilities?.sensors)
      ? capabilities.sensors
      : [],
    deviceMetrics: Array.isArray(capabilities?.deviceMetrics)
      ? capabilities.deviceMetrics
      : [],
    controls: Array.isArray(capabilities?.controls)
      ? capabilities.controls
      : [],
  };
}
