import test from "node:test";
import assert from "node:assert/strict";
import {
  DEFAULT_USER_PREFERENCES,
  normalizeUserPreferences,
  validateUserPreferences,
} from "../src/config/userPreferences.js";

test("normalizes saved preferences and applies backend defaults", () => {
  assert.deepEqual(normalizeUserPreferences({
    temperatureUnit: "FAHRENHEIT",
    dateFormat: "YYYY-MM-DD",
    compactMode: true,
    liveChartAnimations: false,
    dashboardAutoRefresh: false,
  }), {
    temperatureUnit: "FAHRENHEIT",
    dateFormat: "YYYY-MM-DD",
    compactMode: true,
    liveChartAnimations: false,
    dashboardAutoRefresh: false,
  });
  assert.deepEqual(normalizeUserPreferences({}), DEFAULT_USER_PREFERENCES);
});

test("rejects unsupported preference values before submission", () => {
  assert.deepEqual(validateUserPreferences({
    temperatureUnit: "KELVIN",
    dateFormat: "DD-MM-YYYY",
  }), {
    temperatureUnit: "Select a supported temperature unit.",
    dateFormat: "Select a supported date format.",
  });
  assert.deepEqual(validateUserPreferences(DEFAULT_USER_PREFERENCES), {});
});
