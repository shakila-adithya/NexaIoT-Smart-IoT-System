import { apiClient } from "./client.js";

export function getTasks() { return apiClient.get("/api/tasks"); }
export function createTask(payload) { return apiClient.post("/api/tasks", payload); }
export function updateTask(id, payload) { return apiClient.put(`/api/tasks/${id}`, payload); }
export function deleteTask(id) { return apiClient.delete(`/api/tasks/${id}`); }
