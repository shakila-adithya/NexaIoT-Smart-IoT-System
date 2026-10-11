import { apiClient } from "./client.js";

export function getAlertRules() {
  return apiClient.get("/api/alert-rules");
}

export function createAlertRule(rule) {
  return apiClient.post("/api/alert-rules", rule);
}

export function updateAlertRule(id, rule) {
  return apiClient.put(`/api/alert-rules/${id}`, rule);
}

export function deleteAlertRule(id) {
  return apiClient.delete(`/api/alert-rules/${id}`);
}
