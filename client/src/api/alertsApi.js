import { apiClient } from "./client.js";

export function getAlerts() {
  return apiClient.get("/api/alerts", { cache: "no-store" });
}

export function getAlert(id) {
  return apiClient.get(`/api/alerts/${id}`);
}

export function acknowledgeAlert(id) {
  return apiClient.patch(`/api/alerts/${id}/acknowledge`);
}

export function resolveAlert(id) {
  return apiClient.patch(`/api/alerts/${id}/resolve`);
}

export function deleteAlert(id) {
  return apiClient.delete(`/api/alerts/${id}`);
}

export function clearAllAlerts() {
  return apiClient.delete("/api/alerts");
}
