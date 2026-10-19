import { apiClient } from "./client.js";

export async function loginRequest(email, password) {
  return apiClient.post("/api/auth/login", {
    email,
    password,
  });
}

export async function registerRequest({ fullName, email, password }) {
  return apiClient.post("/api/auth/register", {
    fullName,
    email,
    password,
  });
}

export async function forgotPasswordRequest(email) {
  return apiClient.post("/api/auth/forgot-password", { email });
}

export async function resetPasswordRequest(token, newPassword) {
  return apiClient.post("/api/auth/reset-password", { token, newPassword });
}
