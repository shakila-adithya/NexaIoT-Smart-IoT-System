export function validateAutomationRule(form, metrics) {
  if (!form.deviceId || !form.name?.trim()) return "Choose a device and enter a rule name.";
  if (form.ruleType === "METRIC_THRESHOLD") {
    const thresholdText = String(form.threshold ?? "").trim();
    const threshold = Number(thresholdText);
    if (!metrics.some((metric) => metric.key === form.metricKey)) return "Choose a measurable numeric capability for this device.";
    if (!form.operator || !form.severity || !thresholdText || !Number.isFinite(threshold)) return "Enter a valid threshold value.";
  } else {
    const durationText = String(form.offlineDurationMinutes ?? "").trim();
    const duration = Number(durationText);
    if (!durationText || !Number.isInteger(duration) || duration < 1 || duration > 10080) return "Offline duration must be between 1 minute and 7 days.";
  }
  return "";
}
