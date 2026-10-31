import { apiClient } from "./client.js";

export function getFirmwarePackages() {
  return apiClient.get("/api/admin/firmware", { cache: "no-store" });
}

export function uploadFirmwarePackage({ file, version, compatibleDeviceTypes }) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("version", version);
  formData.append("compatibleDeviceTypes", compatibleDeviceTypes);
  return apiClient.post("/api/admin/firmware", formData);
}
