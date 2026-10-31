import test from "node:test";
import assert from "node:assert/strict";

import {
  ENROLLMENT_STAGES,
  canStartActivation,
  isTerminalEnrollmentState,
  isTerminalPairingState,
  isVerifiedCompletion,
  mergePairingResponse,
  recoveryPresentation,
} from "../src/components/device/provisioningState.js";

test("documents the backend enrollment lifecycle in order", () => {
  assert.deepEqual(ENROLLMENT_STAGES.slice(0, 7), [
    "REQUESTED", "BROKER_CLIENT_PENDING", "BROKER_CLIENT_CREATED", "ACL_PENDING",
    "ACL_ASSIGNED", "BROKER_VERIFIED", "CREDENTIAL_DELIVERY_PENDING",
  ]);
});

test("credential delivery pending is not a verified completion", () => {
  assert.equal(isVerifiedCompletion({ state: "CREDENTIAL_DELIVERY_PENDING", credentialDelivered: false, deviceVerified: false }), false);
  assert.equal(isVerifiedCompletion({ state: "COMPLETED", credentialDelivered: false, deviceVerified: true }), false);
  assert.equal(isVerifiedCompletion({ state: "COMPLETED", credentialDelivered: true, deviceVerified: true }), true);
});

test("activation starts only after credential delivery", () => {
  assert.equal(canStartActivation({ state: "CREDENTIAL_DELIVERY_PENDING", credentialDelivered: true }), false);
  assert.equal(canStartActivation({ state: "DEVICE_VERIFICATION_PENDING", credentialDelivered: false }), false);
  assert.equal(canStartActivation({ state: "DEVICE_VERIFICATION_PENDING", credentialDelivered: true, deviceVerified: false }), true);
  assert.equal(canStartActivation({ state: "DEVICE_VERIFICATION_PENDING", credentialDelivered: true, deviceVerified: true }), false);
});

test("recovery dispositions expose only supported guidance", () => {
  assert.equal(recoveryPresentation({ recoveryOutcome: "RETRYABLE" }).action, "reconcile");
  assert.equal(recoveryPresentation({ recoveryOutcome: "SAFE_TO_CONTINUE" }).action, null);
  assert.equal(recoveryPresentation({ recoveryOutcome: "CREDENTIAL_REISSUANCE_REQUIRED" }).action, null);
  assert.equal(recoveryPresentation({ recoveryOutcome: "MANUAL_REVIEW_REQUIRED" }).action, null);
  assert.equal(recoveryPresentation({ reconciliationRequired: true }).action, "reconcile");
});

test("terminal states stop polling", () => {
  assert.equal(isTerminalPairingState("EXPIRED"), true);
  assert.equal(isTerminalPairingState("ACTIVE"), false);
  assert.equal(isTerminalEnrollmentState("FAILED"), true);
  assert.equal(isTerminalEnrollmentState("BROKER_VERIFIED"), false);
});

test("pairing token is cleared after the initial active response", () => {
  const current = { sessionId: "session-1", pairingToken: "secret-token", status: "ACTIVE" };
  const next = mergePairingResponse(current, { sessionId: "session-1", status: "CLAIMED" });
  assert.equal(next.pairingToken, undefined);
  assert.equal(Object.prototype.hasOwnProperty.call(next, "pairingToken"), true);
});
