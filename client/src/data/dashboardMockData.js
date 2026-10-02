export const temperatureSeries = [
  { time: "09:00", temperature: 22.4 },
  { time: "10:00", temperature: 22.9 },
  { time: "11:00", temperature: 23.5 },
  { time: "12:00", temperature: 23.1 },
  { time: "13:00", temperature: 23.4 },
  { time: "14:00", temperature: 23.8 },
];

export const recentActivity = [
  {
    title: "HVAC Unit B-12 turned on",
    meta: "Building B · 2 min ago",
    icon: "power",
    tone: "cyan",
  },
  {
    title: "Temperature threshold updated",
    meta: "Cold Storage 02 · 18 min ago",
    icon: "temperature",
    tone: "amber",
  },
  {
    title: "Sensor gateway reconnected",
    meta: "Warehouse West · 36 min ago",
    icon: "wifi",
    tone: "green",
  },
  {
    title: "Maintenance task assigned",
    meta: "Ethan Miller · 1 hr ago",
    icon: "user",
    tone: "purple",
  },
];

export const latestAlerts = [
  {
    severity: "Critical",
    status: "Open",
    title: "Boiler pressure above safe limit",
    meta: "Boiler Room · 4 min",
  },
  {
    severity: "Warning",
    status: "Open",
    title: "Battery level below 15%",
    meta: "Loading Dock Sensor · 21 min",
  },
  {
    severity: "Info",
    status: "Open",
    title: "Firmware update available",
    meta: "Gateway GW-104 · 1 hr",
  },
];
