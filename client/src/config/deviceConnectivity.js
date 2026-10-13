export const DEVICE_CONNECTIVITY_TYPES = ["WIFI", "ETHERNET", "CELLULAR"];

export const DEVICE_CONNECTIVITY_PROTOCOL = "MQTT";

export const deviceConnectivityLabels = {
  WIFI: "Wi-Fi",
  ETHERNET: "Ethernet",
  CELLULAR: "Cellular",
};

export function formatConnectivityType(type) {
  return deviceConnectivityLabels[type] || "Not configured";
}
