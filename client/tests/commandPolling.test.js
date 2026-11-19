import assert from "node:assert/strict";
import test from "node:test";
import { waitForCommandResult } from "../src/utils/commandPolling.js";

const accepted = { commandId: "cmd-1", issuedAt: "2026-01-01T00:00:00Z", expiresAt: "2026-01-01T00:00:30Z" };
function harness(getStatus) {
  let elapsed = 0;
  return { getStatus: (...args) => getStatus(elapsed, ...args), now: () => elapsed,
    wait: async (milliseconds) => { elapsed += milliseconds; } };
}

test("waits for acknowledgement beyond the old five-second limit", async () => {
  let requests = 0;
  const result = await waitForCommandResult("device", accepted, harness((elapsed, device, command) => {
    assert.equal(device, "device"); assert.equal(command, "cmd-1"); requests++;
    return elapsed >= 7000 ? { status: "ACCEPTED" } : { status: "PENDING", remainingMs: 30_000 - elapsed };
  }));
  assert.equal(result.status, "ACCEPTED");
  assert.ok(requests > 10);
});

test("uses server timeout and preserves uncertainty about physical execution", async () => {
  const result = await waitForCommandResult("device", accepted, harness((elapsed) => (
    elapsed >= 30_000 ? { status: "TIMEOUT", reason: "Device may still have acted." }
      : { status: "PENDING", remainingMs: 30_000 - elapsed }
  )));
  assert.equal(result.status, "TIMEOUT");
  assert.match(result.reason, /may still have acted/);
});

test("unknown commands stop polling without being labelled timeout", async () => {
  let calls = 0;
  const result = await waitForCommandResult("device", accepted, harness(() => {
    calls++; return { status: "UNKNOWN", reason: "No retained outcome." };
  }));
  assert.equal(result.status, "UNKNOWN"); assert.equal(calls, 1);
});

test("remaining duration handles an unrelated browser wall clock", async () => {
  let calls = 0;
  const options = harness(() => { calls++; return { status: "PENDING", remainingMs: 0 }; });
  const result = await waitForCommandResult("device", accepted, options);
  assert.equal(result.status, "UNCONFIRMED");
  assert.ok(calls <= 3);
  assert.match(result.reason, /may still have acted/);
});

test("inconsistent pending responses cannot extend the initial polling budget", async () => {
  let calls = 0;
  const result = await waitForCommandResult("device", accepted, harness(() => {
    calls++; return { status: "PENDING", remainingMs: 30_000 };
  }));
  assert.equal(result.status, "UNCONFIRMED");
  assert.ok(calls <= 63);
});

test("rejected commands preserve the device reason", async () => {
  const result = await waitForCommandResult("device", accepted,
    harness(() => ({ status: "REJECTED", reason: "Unsupported value." })));
  assert.equal(result.reason, "Unsupported value.");
});

test("network failures are surfaced without resending or reporting success", async () => {
  let calls = 0;
  await assert.rejects(waitForCommandResult("device", accepted, harness(() => {
    calls++; throw new Error("Connection lost; outcome unconfirmed.");
  })), /outcome unconfirmed/);
  assert.equal(calls, 1);
});

test("invalid status/deadline fails without guessing an outcome", async () => {
  await assert.rejects(waitForCommandResult("device", { commandId: "cmd-1" }, harness(() => {
    throw new Error("Should not fetch");
  })), /valid command deadline/);
  await assert.rejects(waitForCommandResult("device", accepted,
    harness(() => ({ status: "PENDING" }))), /invalid command status/);
});
