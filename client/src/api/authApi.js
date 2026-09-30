import { apiClient } from "./client.js";
import { demoUser, DEMO_CREDENTIALS } from "../data/mockData.js";

function mockToken() {
  return `demo.${btoa(Date.now().toString())}.token`;
}

export async function loginRequest(email, password) {
  try {
    return await apiClient.post("/api/auth/login", { email, password });
  } catch {
    if (email === DEMO_CREDENTIALS.email && password === DEMO_CREDENTIALS.password) {
      return { token: mockToken(), user: demoUser };
    }
    const err = new Error("Invalid email or password.");
    err.code = "INVALID_CREDENTIALS";
    throw err;
  }
}

export async function registerRequest({ fullName, email, password }) {
  try {
    return await apiClient.post("/api/auth/register", { fullName, email, password });
  } catch {
    return {
      token: mockToken(),
      user: { ...demoUser, name: fullName, email, avatarInitials: fullName.slice(0, 2).toUpperCase() },
    };
  }
}
