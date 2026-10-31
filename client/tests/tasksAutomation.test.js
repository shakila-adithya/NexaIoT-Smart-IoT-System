import test from "node:test";
import assert from "node:assert/strict";
import { validateAutomationRule } from "../src/config/automationValidation.js";
import { nextTaskStatus, taskStatusOptions } from "../src/config/taskStatus.js";

const metrics = [{ key: "temperature" }, { key: "humidity" }];

test("task controls follow backend transition rules", () => {
  assert.equal(nextTaskStatus("OPEN"), "IN_PROGRESS");
  assert.equal(nextTaskStatus("IN_PROGRESS"), "COMPLETED");
  assert.equal(nextTaskStatus("COMPLETED"), null);
  assert.deepEqual(taskStatusOptions("OPEN"), ["OPEN", "IN_PROGRESS", "CANCELLED"]);
  assert.deepEqual(taskStatusOptions("IN_PROGRESS"), ["IN_PROGRESS", "COMPLETED", "CANCELLED"]);
});

test("automation validation rejects unsupported capabilities and invalid schedules", () => {
  const base = { deviceId: "device-1", name: "Temperature rule", ruleType: "METRIC_THRESHOLD", metricKey: "temperature", operator: "GREATER_THAN", threshold: "30", severity: "WARNING" };
  assert.equal(validateAutomationRule(base, metrics), "");
  assert.match(validateAutomationRule({ ...base, metricKey: "relay" }, metrics), /measurable numeric capability/);
  assert.match(validateAutomationRule({ ...base, ruleType: "DEVICE_OFFLINE", offlineDurationMinutes: "0" }, metrics), /between 1 minute and 7 days/);
  assert.match(validateAutomationRule({ ...base, threshold: "not-a-number" }, metrics), /valid threshold/);
});
