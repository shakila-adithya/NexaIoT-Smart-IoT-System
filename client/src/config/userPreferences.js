export const DEFAULT_USER_PREFERENCES = {
  temperatureUnit: "CELSIUS",
  dateFormat: "DD/MM/YYYY",
  compactMode: false,
  liveChartAnimations: true,
  dashboardAutoRefresh: true,
};

export const TEMPERATURE_UNITS = ["CELSIUS", "FAHRENHEIT"];
export const DATE_FORMATS = ["DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD"];

export function normalizeUserPreferences(user = {}) {
  return {
    temperatureUnit: TEMPERATURE_UNITS.includes(user.temperatureUnit)
      ? user.temperatureUnit
      : DEFAULT_USER_PREFERENCES.temperatureUnit,
    dateFormat: DATE_FORMATS.includes(user.dateFormat)
      ? user.dateFormat
      : DEFAULT_USER_PREFERENCES.dateFormat,
    compactMode: user.compactMode === true,
    liveChartAnimations: user.liveChartAnimations !== false,
    dashboardAutoRefresh: user.dashboardAutoRefresh !== false,
  };
}

export function validateUserPreferences(preferences) {
  const errors = {};
  if (!TEMPERATURE_UNITS.includes(preferences.temperatureUnit)) {
    errors.temperatureUnit = "Select a supported temperature unit.";
  }
  if (!DATE_FORMATS.includes(preferences.dateFormat)) {
    errors.dateFormat = "Select a supported date format.";
  }
  return errors;
}
