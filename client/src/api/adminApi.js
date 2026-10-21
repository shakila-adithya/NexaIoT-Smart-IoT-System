import { apiClient } from "./client.js";

export function getAdminUsers(search = "") {
  return apiClient.get(`/api/admin/users?search=${encodeURIComponent(search)}`, { cache: "no-store" });
}

export function getAdminDevices(search = "") {
  return apiClient.get(`/api/admin/devices?search=${encodeURIComponent(search)}`, { cache: "no-store" });
}

export function getAdminStats() {
  return apiClient.get("/api/admin/stats", { cache: "no-store" });
}
