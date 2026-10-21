import { apiClient } from "./client.js";

export function getProvisioningMetadata(deviceId) {
  return apiClient.get(`/api/devices/${deviceId}/provisioning`, { cache: "no-store" });
}

export function startPairingSession(deviceId) {
  return apiClient.post(`/api/devices/${deviceId}/pairing-sessions`);
}

export function getPairingSession(deviceId, sessionId) {
  return apiClient.get(`/api/devices/${deviceId}/pairing-sessions/${sessionId}`, { cache: "no-store" });
}

export function cancelPairingSession(deviceId, sessionId) {
  return apiClient.delete(`/api/devices/${deviceId}/pairing-sessions/${sessionId}`);
}

export function startEnrollment(deviceId, pairingSessionId) {
  return apiClient.post(`/api/devices/${deviceId}/enrollment-operations`, { pairingSessionId });
}

export function getEnrollmentOperation(deviceId, operationId) {
  return apiClient.get(`/api/devices/${deviceId}/enrollment-operations/${operationId}`, { cache: "no-store" });
}

export function reconcileEnrollment(deviceId, operationId) {
  return apiClient.post(`/api/devices/${deviceId}/enrollment-operations/${operationId}/reconcile`);
}
