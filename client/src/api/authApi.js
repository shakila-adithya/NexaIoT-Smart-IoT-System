import { apiClient } from "./client.js";

export async function loginRequest(email, password) {
  return apiClient.post("/api/auth/login", {
    email,
    password,
  });
}

export async function registerRequest({ fullName, email, password }) {
  // First create the account
  await apiClient.post("/api/auth/register", {
    fullName,
    email,
    password,
  });

  // Then log in automatically to obtain the JWT
  return apiClient.post("/api/auth/login", {
    email,
    password,
  });
}