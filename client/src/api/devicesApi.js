import { apiClient } from "./client.js";

export function getDevices() {
  return apiClient.get("/api/devices");
}

export function getDeviceById(id) {
  return apiClient.get(`/api/devices/${id}`);
}

export function createDevice(device) {
  return apiClient.post("/api/devices", device);
}

export function updateDevice(id, device) {
  return apiClient.put(`/api/devices/${id}`, device);
}

export function deleteDevice(id) {
  return apiClient.delete(`/api/devices/${id}`);
}

export function getLatestDeviceReading(id) {
  return apiClient.get(`/api/devices/${id}/readings/latest`);
}