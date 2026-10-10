import { apiClient } from "./client.js";

export function getDeviceTemplates() {
  return apiClient.get("/api/device-templates");
}

export function getDeviceTemplate(templateId) {
  return apiClient.get(`/api/device-templates/${templateId}`);
}
