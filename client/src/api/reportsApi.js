import { apiClient } from "./client.js";
export function getGeneratedReports() { return apiClient.get("/api/reports", { cache: "no-store" }); }
export function generateReport(payload) { return apiClient.post("/api/reports", payload); }
export function getGeneratedReport(id) { return apiClient.get(`/api/reports/${id}`); }
export function deleteGeneratedReport(id) { return apiClient.delete(`/api/reports/${id}`); }
