import test from "node:test";
import assert from "node:assert/strict";

import { getDeviceControls, normalizeCapabilityManifest } from "../src/config/capabilityManifest.js";
import { canSendPowerCommand, formatLastSeen, getDeviceStatusPresentation } from "../src/config/deviceStatus.js";
import { getMetricDefinitionForCapability, getMetricValue } from "../src/config/deviceMetricDefinitions.js";

test("capability manifests drive supported controls", () => {
  const sensor = { capabilityManifest: { version: 1, capabilities: [{ key: "temperature", category: "MEASUREMENT", dataType: "NUMBER" }] } };
  const actuator = { capabilityManifest: { version: 1, capabilities: [{ key: "power", category: "CONTROL", dataType: "BOOLEAN" }] } };
  assert.equal(normalizeCapabilityManifest(sensor).some((item) => item.category === "CONTROL"), false);
  assert.equal(normalizeCapabilityManifest(actuator).some((item) => item.category === "CONTROL"), true);
});

test("an enum direction control is not mistaken for legacy power", () => {
  const capabilities = normalizeCapabilityManifest({ capabilityManifest: { version: 1, capabilities: [
    { key: "power", category: "CONTROL", dataType: "BOOLEAN", control: { type: "SWITCH" } },
    { key: "targetSpeed", category: "CONTROL", dataType: "NUMBER", control: { type: "SLIDER" } },
    { key: "digitalOutput", name: "Direction", category: "CONTROL", dataType: "ENUM", semanticType: "digitalOutput", control: { type: "SELECT" }, options: [{ label: "Forward", value: "FORWARD" }, { label: "Reverse", value: "REVERSE" }] },
  ] } });
  assert.equal(getDeviceControls(capabilities).power?.key, "power");
  assert.deepEqual(getDeviceControls(capabilities).others.map((capability) => capability.name || capability.key), ["targetSpeed", "Direction"]);
  assert.equal(getDeviceControls([capabilities[2]]).power, undefined);
  assert.equal(getDeviceControls([capabilities[2]]).others[0].name, "Direction");
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
