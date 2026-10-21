export const taskStatusLabels = {
  OPEN: "Open",
  IN_PROGRESS: "In progress",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

export function taskStatusOptions(status) {
  if (status === "OPEN") return ["OPEN", "IN_PROGRESS", "CANCELLED"];
  if (status === "IN_PROGRESS") return ["IN_PROGRESS", "COMPLETED", "CANCELLED"];
  return status ? [status] : ["OPEN"];
}

export function nextTaskStatus(status) {
  if (status === "OPEN") return "IN_PROGRESS";
  if (status === "IN_PROGRESS") return "COMPLETED";
  return null;
}

export function taskStatusActionLabel(status) {
  if (status === "OPEN") return "Start task";
  if (status === "IN_PROGRESS") return "Complete task";
  return "";
}
