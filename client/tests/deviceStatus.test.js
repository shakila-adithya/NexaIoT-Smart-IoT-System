import test from "node:test";
import assert from "node:assert/strict";

import { normalizeCapabilityManifest } from "../src/config/capabilityManifest.js";
import { canSendPowerCommand, formatLastSeen, getDeviceStatusPresentation } from "../src/config/deviceStatus.js";
import { getMetricDefinitionForCapability, getMetricValue } from "../src/config/deviceMetricDefinitions.js";

test("capability manifests drive supported controls", () => {
  const sensor = { capabilityManifest: { version: 1, capabilities: [{ key: "temperature", category: "MEASUREMENT", dataType: "NUMBER" }] } };
  const actuator = { capabilityManifest: { version: 1, capabilities: [{ key: "power", category: "CONTROL", dataType: "BOOLEAN" }] } };
  assert.equal(normalizeCapabilityManifest(sensor).some((item) => item.category === "CONTROL"), false);
  assert.equal(normalizeCapabilityManifest(actuator).some((item) => item.category === "CONTROL"), true);
});

test("status mapping never turns unknown evidence into online or offline", () => {
  assert.equal(getDeviceStatusPresentation("ONLINE").label, "Online");
  assert.equal(getDeviceStatusPresentation("OFFLINE").label, "Offline");
  assert.equal(getDeviceStatusPresentation("UNSUPPORTED_STATUS").label, "Status unavailable");
});

test("power commands require authoritative online status and no in-flight request", () => {
  assert.equal(canSendPowerCommand({ status: "ONLINE" }, false), true);
  assert.equal(canSendPowerCommand({ status: "OFFLINE" }, false), false);
  assert.equal(canSendPowerCommand({ status: "UNKNOWN" }, false), false);
  assert.equal(canSendPowerCommand({ status: "ONLINE" }, true), false);
});

test("telemetry definitions preserve capability labels and missing values", () => {
  const definition = getMetricDefinitionForCapability({ key: "temperature", category: "MEASUREMENT" });
  assert.equal(definition.label, "Temperature");
  assert.equal(getMetricValue(definition, { metrics: { temperature: 22.5 } }), 22.5);
  assert.equal(getMetricValue(definition, { metrics: {} }), null);
  assert.match(formatLastSeen("2026-10-21T00:00:00Z"), /^Last seen /);
  assert.equal(formatLastSeen(null), "Last seen unavailable");
});
