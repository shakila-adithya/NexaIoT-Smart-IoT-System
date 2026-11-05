import { apiClient } from "./client.js";

export function getCurrentUser() {
  return apiClient.get("/api/user/me", { cache: "no-store" });
}

export function updateProfile(profile) {
  return apiClient.put("/api/user/me", profile);
}

export function updatePreferences(preferences) {
  return apiClient.put("/api/user/me/preferences", preferences);
}

export function changePassword(payload) {
  return apiClient.post("/api/user/me/change-password", payload);
}

export function uploadProfileImage(file) {
  const formData = new FormData();
  formData.append("image", file);
  return apiClient.post("/api/user/me/profile-image", formData);
}

export function getProfileImage() {
  return apiClient.get("/api/user/me/profile-image", {
    cache: "no-store",
    responseType: "blob",
  });
}

export function deleteProfileImage() {
  return apiClient.delete("/api/user/me/profile-image");
}

export function deleteAccount(password) {
  return apiClient.delete("/api/user/me", { body: { password } });
}

