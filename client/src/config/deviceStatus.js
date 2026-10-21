export const DEVICE_STATUS_PRESENTATIONS = {
  ONLINE: { label: "Online", tone: "success", description: "Authoritative backend evidence reports the device as connected." },
  OFFLINE: { label: "Offline", tone: "danger", description: "The backend has no current connectivity evidence." },
  MAINTENANCE: { label: "Maintenance", tone: "warning", description: "The device is in backend maintenance mode." },
  UNKNOWN: { label: "Status unavailable", tone: "neutral", description: "The backend did not provide a recognized connectivity status." },
};

export function getDeviceStatusPresentation(status) {
  const normalized = typeof status === "string" ? status.toUpperCase() : "UNKNOWN";
  return {
    value: DEVICE_STATUS_PRESENTATIONS[normalized] ? normalized : "UNKNOWN",
    ...(DEVICE_STATUS_PRESENTATIONS[normalized] || DEVICE_STATUS_PRESENTATIONS.UNKNOWN),
  };
}

export function formatLastSeen(value) {
  if (!value) return "Last seen unavailable";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Last seen unavailable";
  return `Last seen ${date.toLocaleString()}`;
}

export function canSendPowerCommand(device, loading = false) {
  return Boolean(device && device.status === "ONLINE" && !loading);
}
