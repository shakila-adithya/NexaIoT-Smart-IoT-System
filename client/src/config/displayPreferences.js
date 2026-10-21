export function convertTemperature(value, temperatureUnit) {
  if (!Number.isFinite(value) || temperatureUnit !== "FAHRENHEIT") return value;
  return (value * 9) / 5 + 32;
}

export function getDisplayMetricValue(definition, reading, preferences) {
  const value = definition?.source === "metrics"
    ? reading?.metrics?.[definition.key]
    : reading?.[definition?.key];
  return definition?.domain === "temperature"
    ? convertTemperature(value, preferences?.temperatureUnit)
    : value ?? null;
}

export function getDisplayMetricUnit(definition, preferences) {
  if (definition?.domain === "temperature" && preferences?.temperatureUnit === "FAHRENHEIT") return "°F";
  return definition?.unit || "";
}

export function formatDisplayDate(value, dateFormat = "DD/MM/YYYY", includeTime = true) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";

  const day = String(date.getDate()).padStart(2, "0");
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const year = String(date.getFullYear());
  const datePart = dateFormat === "MM/DD/YYYY"
    ? `${month}/${day}/${year}`
    : dateFormat === "YYYY-MM-DD"
      ? `${year}-${month}-${day}`
      : `${day}/${month}/${year}`;

  if (!includeTime) return datePart;
  return `${datePart} ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
}

export function isStaleReading(value, thresholdMs = 5 * 60 * 1000, now = Date.now()) {
  const timestamp = new Date(value || 0).getTime();
  return !Number.isFinite(timestamp) || now - timestamp > thresholdMs;
}
