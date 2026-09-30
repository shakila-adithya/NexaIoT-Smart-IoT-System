export const demoUser = {
  id: "usr-001",
  name: "John Doe",
  email: "admin@nexaiot.com",
  role: "Administrator",
  avatarInitials: "JD",
};

export const DEMO_CREDENTIALS = {
  email: "admin@nexaiot.com",
  password: "admin123",
};

export const solutions = [
  { key: "buildings", title: "Smart Buildings", blurb: "Monitor temperature, humidity, lighting, and energy across every floor.", metrics: ["Temperature", "Humidity", "Lighting", "Energy"] },
  { key: "agriculture", title: "Smart Agriculture", blurb: "Track soil and environmental conditions to optimize irrigation.", metrics: ["Soil Moisture", "Temperature", "Humidity", "Irrigation"] },
  { key: "industrial", title: "Industrial IoT", blurb: "Keep tabs on machine health, vibration, and operational status.", metrics: ["Vibration", "Energy", "Uptime", "Status"] },
  { key: "energy", title: "Energy Monitoring", blurb: "Visualize voltage and consumption trends across your facilities.", metrics: ["Voltage", "Consumption", "Device Status"] },
  { key: "environmental", title: "Environmental Monitoring", blurb: "Track air quality, pressure, and climate conditions in real time.", metrics: ["Air Quality", "Pressure", "Climate"] },
  { key: "asset", title: "Asset Monitoring", blurb: "Locate and monitor the condition of mobile and fixed assets.", metrics: ["Location", "Condition", "Utilization"] },
];

export const features = [
  { title: "Device Management", desc: "Add, organize, and manage every connected device from a single console." },
  { title: "Real-Time Monitoring", desc: "Stream live sensor data with automatic refresh and status tracking." },
  { title: "Sensor Analytics", desc: "Explore historical trends across temperature, humidity, energy, and more." },
  { title: "Device Control", desc: "Toggle power, lighting, and relays remotely with instant feedback." },
  { title: "Smart Alerts", desc: "Get notified the moment a reading crosses a configurable threshold." },
  { title: "Reports", desc: "Generate daily, weekly, and monthly reports in a couple of clicks." },
  { title: "Data Visualization", desc: "Understand your fleet at a glance with clean, purposeful charts." },
  { title: "API Integration", desc: "Connect your own backend with a REST-ready client out of the box." },
  { title: "User Management", desc: "Control who can view, edit, or administer your IoT infrastructure." },
  { title: "Security", desc: "Token-based authentication built to work with your production auth." },
];
