import { getDeviceCommandStatus } from "../api/devicesApi.js";

const terminalStatuses = new Set(["ACCEPTED", "DUPLICATE", "REJECTED", "TIMEOUT", "UNKNOWN"]);

async function readStatus(getStatus, deviceId, commandId, timeoutMs) {
  const controller = new AbortController();
  let timer;
  try {
    return await Promise.race([
      getStatus(deviceId, commandId, { signal: controller.signal }),
      new Promise((_, reject) => {
        timer = setTimeout(() => {
          reject(new Error("Status request timed out. The command outcome is unconfirmed."));
          controller.abort();
        }, timeoutMs);
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}

/** Wait for the server's acknowledgement decision; never resend a device command. */
export async function waitForCommandResult(deviceId, accepted, {
  getStatus = getDeviceCommandStatus,
  now = () => performance.now(),
  wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds)),
} = {}) {
  if (!accepted?.commandId) throw new Error("The backend did not return a command identifier.");
  const duration = Date.parse(accepted.expiresAt) - Date.parse(accepted.issuedAt);
  if (!Number.isFinite(duration) || duration <= 0 || duration > 300_000) {
    throw new Error("The backend did not return a valid command deadline.");
  }
  // Use a monotonic local clock and the server's remaining duration, avoiding wall-clock skew.
  const graceMs = 1000;
  let deadline = now() + duration + graceMs;
  while (now() < deadline) {
    const response = await readStatus(getStatus, deviceId, accepted.commandId,
      Math.min(5000, deadline - now()));
    if (terminalStatuses.has(response?.status)) return response;
    if (response?.status !== "PENDING" || !Number.isFinite(response.remainingMs) || response.remainingMs < 0) {
      throw new Error("The backend returned an invalid command status. The outcome is unconfirmed.");
    }
    deadline = Math.min(deadline, now() + response.remainingMs + graceMs);
    await wait(Math.min(500, Math.max(0, deadline - now())));
  }
  return { status: "UNCONFIRMED", commandId: accepted.commandId,
    reason: "Acknowledgement could not be confirmed. The device may still have acted; check its reported state." };
}
