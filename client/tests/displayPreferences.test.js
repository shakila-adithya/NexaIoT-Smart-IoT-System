import test from "node:test";
import assert from "node:assert/strict";
import {
  convertTemperature,
  formatDisplayDate,
  getDisplayMetricUnit,
  getDisplayMetricValue,
  isStaleReading,
} from "../src/config/displayPreferences.js";

test("converts temperature for display without changing raw telemetry", () => {
  const definition = { source: "metrics", key: "temperature", domain: "temperature", unit: "°C" };
  const reading = { metrics: { temperature: 20 } };
  assert.equal(getDisplayMetricValue(definition, reading, { temperatureUnit: "CELSIUS" }), 20);
  assert.equal(getDisplayMetricValue(definition, reading, { temperatureUnit: "FAHRENHEIT" }), 68);
  assert.equal(getDisplayMetricUnit(definition, { temperatureUnit: "FAHRENHEIT" }), "°F");
  assert.equal(reading.metrics.temperature, 20);
  assert.equal(convertTemperature(null, "FAHRENHEIT"), null);
});

test("formats configured date order and identifies stale readings", () => {
  const timestamp = "2026-01-02T03:04:05.000Z";
  assert.match(formatDisplayDate(timestamp, "YYYY-MM-DD", false), /^2026-01-02$/);
  assert.equal(isStaleReading(timestamp, 1000, Date.parse("2026-01-02T03:04:07.000Z")), true);
  assert.equal(isStaleReading(timestamp, 10000, Date.parse("2026-01-02T03:04:07.000Z")), false);
});
