import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Activity,
  Bot,
  CalendarDays,
  Cpu,
  Hash,
  KeyRound,
  MapPin,
  Pencil,
  Leaf,
  Power,
  RadioTower,
  Save,
  Settings2,
  ShieldCheck,
  Thermometer,
  Trash2,
  X,
} from "lucide-react";

import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import {
  deleteDevice,
  getDeviceById,
  getDeviceReadings,
  getLatestDeviceReading,
  updateDevice,
  controlDevice,
} from "../../api/devicesApi.js";
import { getAlerts } from "../../api/alertsApi.js";
import CapabilitySelector from "../../components/device/CapabilitySelector.jsx";
import { normalizeCapabilities } from "../../config/deviceCapabilities.js";
import {
  DEVICE_METRIC_DEFINITIONS,
  getMetricDomain,
  getMetricValue,
} from "../../config/deviceMetricDefinitions.js";

const alertSeverityStyles = {
  CRITICAL: "bg-[#fdecee] text-[#d83f4d]",
  WARNING: "bg-[#fff1d7] text-[#c67b08]",
  INFO: "bg-[#e8f8fb] text-[#087f98]",
};

const alertStatusStyles = {
  ACTIVE: {
    container: "border-red-200 bg-red-50/60",
    text: "text-red-600",
  },
  ACKNOWLEDGED: {
    container: "border-blue-200 bg-blue-50/60",
    text: "text-blue-600",
  },
  RESOLVED: {
    container: "border-emerald-200 bg-emerald-50/60",
    text: "text-emerald-600",
  },
};

function formatAlertType(type) {
  return (type || "Alert")
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function formatAlertTime(value) {
  if (!value) return "Unknown time";

  const elapsedSeconds = Math.max(
    0,
    Math.floor((Date.now() - new Date(value).getTime()) / 1000),
  );

  if (elapsedSeconds < 60) return "Just now";
  if (elapsedSeconds < 3600) return `${Math.floor(elapsedSeconds / 60)} min ago`;
  if (elapsedSeconds < 86400) return `${Math.floor(elapsedSeconds / 3600)} hr ago`;
  return `${Math.floor(elapsedSeconds / 86400)} day${Math.floor(elapsedSeconds / 86400) === 1 ? "" : "s"} ago`;
}

function formatChartTime(value) {
  if (!value) return "";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return "";

  return date.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function TelemetryCard({
  icon: Icon,
  value,
  label,
  note,
  tone = "cyan",
  active = false,
  iconColor,
}) {
  const tones = {
    cyan: {
      bg: "bg-[#eaf9fc]",
      text: "text-[#08a9c4]",
    },
    blue: {
      bg: "bg-[#eef4ff]",
      text: "text-[#4d7ee8]",
    },
    amber: {
      bg: "bg-[#fff5df]",
      text: "text-[#e99a12]",
    },
    green: {
      bg: "bg-[#eaf8f3]",
      text: "text-[#16a57a]",
    },
    violet: {
      bg: "bg-[#f2efff]",
      text: "text-[#7d68df]",
    },
  };
  const selected = tones[tone] || tones.cyan;
  return (
    <div className="rounded-[14px] border border-[#dce8ee] bg-white p-3.5 shadow-[0_3px_12px_rgba(16,42,58,0.04)]">
      <div className="flex items-start justify-between">
        <div
          className={`flex h-9 w-9 items-center justify-center rounded-[10px] ${selected.bg}`}
        >
          <Icon
            size={17}
            className={iconColor || selected.text}
          />
        </div>
        <span
          className={`h-1.5 w-1.5 rounded-full ${
            active ? "bg-[#16a57a]" : "bg-[#cbd8de]"
          }`}
        />
      </div>
      <div className="mt-3 truncate text-[18px] font-medium text-[#102a3a]">
        {value}
      </div>
      <div className="mt-1.5 text-[10px] font-medium text-[#425c6b]">
        {label}
      </div>
      <div className="mt-0.5 truncate text-[8px] text-[#8ba0ab]">
        {note}
      </div>
    </div>
  );
}

function DisabledToggle({ enabled = false }) {
  return (
    <div
      className={`relative h-[22px] w-[38px] shrink-0 rounded-full ${
        enabled ? "bg-[#08a9c4]" : "bg-[#dce8ee]"
      } opacity-60`}
    >
      <span
        className={`absolute top-[3px] h-4 w-4 rounded-full bg-white shadow-sm ${
          enabled ? "left-[19px]" : "left-[3px]"
        }`}
      />
    </div>
  );
}

export default function DeviceDetails() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [device, setDevice] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [deviceAlerts, setDeviceAlerts] = useState([]);
  const [deviceAlertsLoading, setDeviceAlertsLoading] = useState(true);
  const [deviceAlertsError, setDeviceAlertsError] = useState("");
  const alertsPollingInFlight = useRef(false);

  const [latestReading, setLatestReading] = useState(null);
  const [readings, setReadings] = useState([]);
  const [selectedRange, setSelectedRange] = useState("1h");
  const [selectedMetric, setSelectedMetric] = useState("");
  const [controlLoading, setControlLoading] = useState(false);
  const [controlError, setControlError] = useState("");
  const historyPollingInFlight = useRef(false);

  const [showEditModal, setShowEditModal] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editError, setEditError] = useState("");

  const [editForm, setEditForm] = useState({
    name: "",
    type: "",
    location: "",
  });
  const [editCapabilities, setEditCapabilities] = useState(
    normalizeCapabilities(null),
  );

  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");

  useEffect(() => {
    let cancelled = false;

    async function loadDevice(showLoading = false) {
      try {
        if (showLoading) {
          setLoading(true);
        }

        setError("");

        const data = await getDeviceById(id);

        if (!cancelled) {
          setDevice(data);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err.message || "Unable to load device.");
        }
      } finally {
        if (showLoading && !cancelled) {
          setLoading(false);
        }
      }
    }

    loadDevice(true);

    const interval = setInterval(() => {
      loadDevice();
    }, 5000);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [id]);

  useEffect(() => {
    let cancelled = false;

    async function loadDeviceAlerts({ initial = false } = {}) {
      if (alertsPollingInFlight.current) return;

      alertsPollingInFlight.current = true;

      try {
        const data = await getAlerts();

        if (!cancelled) {
          setDeviceAlerts(
            (Array.isArray(data) ? data : [])
              .filter((alert) => alert.deviceId === id)
              .sort((first, second) => new Date(second.createdAt) - new Date(first.createdAt))
              .slice(0, 3),
          );
          setDeviceAlertsError("");
        }
      } catch (err) {
        if (!cancelled && initial) {
          setDeviceAlertsError(err.message || "Unable to load device alerts.");
        }
      } finally {
        alertsPollingInFlight.current = false;

        if (!cancelled && initial) {
          setDeviceAlertsLoading(false);
        }
      }
    }

    loadDeviceAlerts({ initial: true });

    const interval = setInterval(() => {
      loadDeviceAlerts();
    }, 5000);

    return () => {
      cancelled = true;
      clearInterval(interval);
      alertsPollingInFlight.current = false;
    };
  }, [id]);

  useEffect(() => {
    let cancelled = false;

    async function loadLatestReading() {
      try {
        const data = await getLatestDeviceReading(id);

        if (!cancelled) {
          setLatestReading(data);
        }
      } catch (err) {
        console.error("Unable to load latest telemetry:", err);
      }
    }

    loadLatestReading();

    const interval = setInterval(() => {
      loadLatestReading();
    }, 5000);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [id]);

  useEffect(() => {
    let cancelled = false;

    async function loadReadings() {
      if (historyPollingInFlight.current) return;

      historyPollingInFlight.current = true;

      try {
        const data = await getDeviceReadings(id, selectedRange);

        if (!cancelled) {
          setReadings(Array.isArray(data) ? data : []);
        }
      } catch (err) {
        console.error("Unable to load telemetry history:", err);
      } finally {
        historyPollingInFlight.current = false;
      }
    }

    loadReadings();

    const interval = setInterval(() => {
      loadReadings();
    }, 5000);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [id, selectedRange]);

  const openEditModal = () => {
    setEditForm({
      name: device?.name || "",
      type: device?.type || "",
      location: device?.location || "",
    });
    setEditCapabilities(normalizeCapabilities(device?.capabilities));

    setEditError("");
    setShowEditModal(true);
  };

  const closeEditModal = () => {
    if (saving) return;

    setShowEditModal(false);
    setEditError("");
  };

  const handleEditChange = (event) => {
    const { name, value } = event.target;

    setEditForm((current) => ({
      ...current,
      [name]: value,
    }));
  };

  const handleUpdate = async (event) => {
    event.preventDefault();

    if (
      !editForm.name.trim() ||
      !editForm.type.trim() ||
      !editForm.location.trim()
    ) {
      setEditError("Device name, type and location are required.");
      return;
    }

    try {
      setSaving(true);
      setEditError("");

      const updatedDevice = await updateDevice(device.id, {
        name: editForm.name.trim(),
        type: editForm.type.trim(),
        location: editForm.location.trim(),
        capabilities: editCapabilities,
      });

      setDevice(updatedDevice);
      setShowEditModal(false);
    } catch (err) {
      setEditError(err.message || "Unable to update device.");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    try {
      setDeleting(true);
      setDeleteError("");

      await deleteDevice(device.id);

      navigate("/devices");
    } catch (err) {
      setDeleteError(err.message || "Unable to delete device.");
      setDeleting(false);
    }
  };

  const closeDeleteModal = () => {
    if (deleting) return;

    setShowDeleteConfirm(false);
    setDeleteError("");
  };
async function handlePowerToggle() {
    if (!device || controlLoading || !isOnline) {
      return;
    }
    const targetPower = !device.powerOn;
    try {
      setControlLoading(true);
      setControlError("");
      await controlDevice(id, targetPower);
      for (let attempt = 0; attempt < 8; attempt++) {
        await new Promise((resolve) => setTimeout(resolve, 250));
        const updatedDevice = await getDeviceById(id);
        setDevice(updatedDevice);
        if (updatedDevice.powerOn === targetPower) {
          break;
        }
      }
    } catch (err) {
      setControlError(
        err.message || "Unable to send device command."
      );
    } finally {
      setControlLoading(false);
    }
  }
  if (loading) {
    return (
      <div className="w-full px-4 pb-10 pt-6 md:px-6 lg:px-[30px]">
        <div className="mx-auto w-full max-w-[1144px]">
          <div className="rounded-[14px] border border-[#dce8ee] bg-white p-10 text-center text-[12px] text-[#6b8290]">
            Loading device...
          </div>
        </div>
      </div>
    );
  }
  if (error || !device) {
    return (
      <div className="w-full px-4 pb-10 pt-6 md:px-6 lg:px-[30px]">
        <div className="mx-auto w-full max-w-[1144px]">
          <div className="rounded-[14px] border border-red-200 bg-red-50 p-5 text-[12px] text-red-600">
            {error || "Device not found."}
          </div>
        </div>
      </div>
    );
  }
  const isOnline = device.status === "ONLINE";
  const isMaintenance = device.status === "MAINTENANCE";
  const createdDate = device.createdAt
    ? new Date(device.createdAt).toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
        year: "numeric",
      })
    : "—";
  const lastReadingTime = latestReading?.receivedAt
    ? new Date(latestReading.receivedAt).toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      })
    : "—";
  const configuredCapabilities = normalizeCapabilities(device.capabilities);
  const supportedCapabilities = [
    ...configuredCapabilities.sensors,
    ...configuredCapabilities.deviceMetrics,
  ].filter((capability) => DEVICE_METRIC_DEFINITIONS[capability]);
  const liveMetricDefinitions = supportedCapabilities
    .map((capability) => DEVICE_METRIC_DEFINITIONS[capability])
    .filter(Boolean);
  const supportsDigitalOutput = configuredCapabilities.controls.includes(
    "DIGITAL_OUTPUT",
  );
  const supportsTemperatureSetpoint = configuredCapabilities.controls.includes(
    "TEMPERATURE_SETPOINT",
  );
  const supportsAutoMode = configuredCapabilities.controls.includes("AUTO_MODE");
  const supportsEcoSchedule = configuredCapabilities.controls.includes(
    "ECO_SCHEDULE",
  );
  const hasConfiguredControl =
    supportsDigitalOutput ||
    supportsTemperatureSetpoint ||
    supportsAutoMode ||
    supportsEcoSchedule;
  const effectiveSelectedMetric = supportedCapabilities.includes(selectedMetric)
    ? selectedMetric
    : supportedCapabilities[0] || "";
  const metricDefinition = DEVICE_METRIC_DEFINITIONS[effectiveSelectedMetric];
  const chartData = metricDefinition
    ? readings
        .map((reading) => ({
          time: reading.receivedAt || "",
          value: getMetricValue(metricDefinition, reading),
        }))
        .filter((point) => Number.isFinite(point.value))
    : [];
  const metricDomain = getMetricDomain(
    metricDefinition,
    chartData.map((point) => point.value),
  );
  return (
    <div className="w-full px-4 pb-10 pt-6 md:px-6 lg:px-[30px]">
      <div className="mx-auto w-full max-w-[1144px]">
        <section className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <button
              type="button"
              onClick={() => navigate("/devices")}
              className="mb-2 text-[10px] font-medium text-[#7d929e] transition hover:text-[#08a9c4]"
            >
              ← Back to devices
            </button>
            <h1 className="break-words text-[24px] font-normal leading-tight text-[#102a3a] sm:text-[26px]">
              {device.name}
            </h1>
            <p className="mt-1 break-all text-[10px] text-[#6b8290] sm:text-[11px]">
              {device.deviceKey} · {device.location}
            </p>
          </div>
          <div className="flex w-full flex-col gap-2.5 sm:w-auto sm:flex-row sm:items-start">
            <button
              type="button"
              onClick={openEditModal}
              className="flex h-10 w-full items-center justify-center gap-2 rounded-[10px] border border-[#dce8ee] bg-white px-4 text-[11px] font-medium text-[#102a3a] transition hover:border-[#08a9c4] hover:bg-[#f8fbfd] sm:w-auto"
            >
              <Pencil size={14} />
              Edit device
            </button>
            <div className="flex w-full flex-col sm:w-auto sm:items-end">
              <button
                type="button"
                disabled
                title="Diagnostics will be available after MQTT telemetry integration"
                className="flex h-10 w-full cursor-not-allowed items-center justify-center gap-2 rounded-[10px] bg-[#08a9c4] px-4 text-[11px] font-medium text-white opacity-60 sm:w-auto"
              >
                <Activity size={14} />
                Run diagnostics
              </button>
              <span className="mt-1.5 text-center text-[8px] text-[#8ba0ab] sm:text-right">
                Requires MQTT connection
              </span>
            </div>
          </div>
        </section>
        <section className="mt-5 rounded-[15px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(16,42,58,0.05)]">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
            <div className="flex h-[82px] w-full shrink-0 items-center justify-center rounded-[14px] bg-gradient-to-br from-[#e7f8fb] to-[#f7fbfc] sm:h-[88px] sm:w-[108px]">
              <Cpu size={36} className="text-[#84b9c5]" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span
                  className={`flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[9px] font-medium ${
                    isOnline
                      ? "bg-[#e9f8f2] text-[#16a57a]"
                      : isMaintenance
                        ? "bg-[#fff6e6] text-[#d49a24]"
                        : "bg-red-50 text-red-500"
                  }`}
                >
                  <span
                    className={`h-1.5 w-1.5 rounded-full ${
                      isOnline
                        ? "bg-[#16a57a]"
                        : isMaintenance
                          ? "bg-[#d49a24]"
                          : "bg-red-500"
                    }`}
                  />
                  {device.status}
                </span>
                <span className="rounded-full bg-[#eef4ff] px-2.5 py-1 text-[9px] font-medium text-[#4d7ee8]">
                  {device.type}
                </span>
                <span className="text-[9px] text-[#9aaeba]">
                  Telemetry not connected
                </span>
              </div>
              <p className="mt-2 max-w-[660px] text-[10px] leading-[17px] text-[#6b8290] sm:text-[11px] sm:leading-5">
                Registered NexaIoT device located at {device.location}. Live
                telemetry, diagnostics and remote controls will become
                available after MQTT integration.
              </p>
              <div className="mt-2 flex flex-wrap gap-x-5 gap-y-2 text-[8px] text-[#7d929e] sm:text-[9px]">
                <span className="flex items-center gap-1.5">
                  <MapPin size={12} />
                  {device.location}
                </span>
                <span className="flex items-center gap-1.5">
                  <RadioTower size={12} />
                  MQTT pending
                </span>
                <span className="flex items-center gap-1.5">
                  <CalendarDays size={12} />
                  Added {createdDate}
                </span>
                <span className="flex min-w-0 items-center gap-1.5">
                  <KeyRound size={12} className="shrink-0" />
                  <span className="truncate">{device.deviceKey}</span>
                </span>
              </div>
            </div>
            <div
              className={`flex w-full shrink-0 items-center justify-between rounded-[12px] px-4 py-3 sm:w-[118px] sm:flex-col sm:justify-center sm:py-4 ${
                device.powerOn ? "bg-[#eaf8f3]" : "bg-[#f3f7f9]"
              }`}
            >
              <span
                className={`text-[9px] ${
                  device.powerOn ? "text-[#16a57a]" : "text-[#7d929e]"
                }`}
              >
                Device power
              </span>
              <div
                className={`relative h-[24px] w-[42px] rounded-full sm:mt-2 ${
                  device.powerOn ? "bg-[#08a9c4]" : "bg-[#cfdde3]"
                }`}
              >
                <span
                  className={`absolute top-[3px] h-[18px] w-[18px] rounded-full bg-white shadow ${
                    device.powerOn ? "left-[21px]" : "left-[3px]"
                  }`}
                />
              </div>
              <span
                className={`text-[9px] font-medium sm:mt-2 ${
                  device.powerOn ? "text-[#16a57a]" : "text-[#7d929e]"
                }`}
              >
                {device.powerOn ? "Running" : "Stopped"}
              </span>
            </div>
          </div>
        </section>
        <section className="mt-4">
          {liveMetricDefinitions.length === 0 ? (
            <div className="rounded-[14px] border border-[#dce8ee] bg-white p-6 text-center shadow-[0_3px_12px_rgba(16,42,58,0.04)]">
              <p className="text-[12px] font-medium text-[#102a3a]">
                No measurement capabilities configured for this device.
              </p>
              <p className="mt-1 text-[10px] text-[#6b8290]">
                Edit the device to select its supported measurements.
              </p>
            </div>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
              {liveMetricDefinitions.map((definition) => {
                const value = getMetricValue(definition, latestReading);
                const Icon = definition.icon;

                return (
                  <TelemetryCard
                    key={definition.key}
                    icon={Icon}
                    value={value != null ? `${value} ${definition.unit}` : "—"}
                    label={definition.label}
                    note={
                      latestReading
                        ? "Latest MQTT reading"
                        : "Waiting for telemetry"
                    }
                    tone={definition.tone}
                  />
                );
              })}
            </div>
          )}
        </section>
        <section className="mt-4 grid items-start gap-4 xl:grid-cols-[minmax(0,2fr)_340px]">
          <div className="rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(16,42,58,0.05)] sm:p-5">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <Activity size={16} className="text-[#08a9c4]" />
                  <h2 className="text-[15px] font-semibold text-[#102a3a]">
                    Telemetry history
                  </h2>
                </div>
                <p className="mt-1 text-[10px] text-[#6b8290]">
                  Historical {metricDefinition?.label || "telemetry"} measurements
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {liveMetricDefinitions.length > 0 ? (
                  <label className="flex h-9 items-center gap-2 rounded-[9px] border border-[#dce8ee] bg-white px-2.5 text-[9px] text-[#6b8290]">
                    <span>Metric</span>
                    <select
                      value={effectiveSelectedMetric}
                      onChange={(event) => setSelectedMetric(event.target.value)}
                      className="max-w-[140px] bg-transparent text-[9px] font-medium text-[#102a3a] outline-none"
                    >
                      {liveMetricDefinitions.map((definition) => (
                        <option key={definition.key} value={definition.key}>
                          {definition.label}
                        </option>
                      ))}
                    </select>
                  </label>
                ) : null}
                <div className="flex w-fit max-w-full overflow-x-auto rounded-[9px] border border-[#e5edf1] bg-[#f8fbfd] p-1">
                  {[
                    { label: "1H", value: "1h" },
                    { label: "6H", value: "6h" },
                    { label: "24H", value: "24h" },
                    { label: "7D", value: "7d" },
                  ].map((period) => (
                    <button
                      key={period.value}
                      type="button"
                      onClick={() => setSelectedRange(period.value)}
                      className={`h-7 min-w-[38px] rounded-[7px] px-2 text-[9px] font-medium transition ${
                        selectedRange === period.value
                          ? "bg-white text-[#08a9c4] shadow-sm"
                          : "text-[#8ca0ab] hover:text-[#526b79]"
                      }`}
                    >
                      {period.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2">
              {metricDefinition ? (
                <div className="flex items-center gap-2">
                  <span
                    className="h-2 w-2 rounded-full"
                    style={{ backgroundColor: metricDefinition.color }}
                  />
                  <span className="text-[9px] text-[#6b8290]">
                    {metricDefinition.label}
                  </span>
                  <span className="text-[9px] font-semibold text-[#102a3a]">
                    {getMetricValue(metricDefinition, latestReading) != null
                      ? `${getMetricValue(metricDefinition, latestReading)} ${metricDefinition.unit}`
                      : "—"}
                  </span>
                </div>
              ) : null}
              <div className="flex items-center gap-1.5 text-[8px] text-[#8ba0ab] sm:ml-auto">
                <span
                  className={`h-1.5 w-1.5 rounded-full ${
                    isOnline ? "bg-[#16a57a]" : "bg-[#c7d4da]"
                  }`}
                />
                {isOnline ? "Device online" : "Telemetry disconnected"}
              </div>
            </div>
            <div className="relative mt-4 h-[220px] overflow-hidden rounded-[12px] border border-[#edf2f4] bg-[#fcfefe] p-2 sm:h-[235px]">
              {!metricDefinition ? (
                <div className="flex h-full items-center justify-center px-4">
                  <div className="max-w-[300px] text-center">
                    <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-[#eef9fb]">
                      <RadioTower size={20} className="text-[#08a9c4]" />
                    </div>
                    <p className="mt-3 text-[11px] font-semibold text-[#102a3a]">
                      No telemetry capabilities configured for this device.
                    </p>
                    <p className="mt-1 text-[9px] leading-4 text-[#7d929e]">
                      Edit the device to select supported measurements.
                    </p>
                  </div>
                </div>
              ) : chartData.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart
                    data={chartData}
                    margin={{ top: 12, right: 8, left: -12, bottom: 2 }}
                  >
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke="#e5edf1"
                      vertical={false}
                    />
                    <XAxis
                      dataKey="time"
                      axisLine={false}
                      tickLine={false}
                      minTickGap={24}
                      tickFormatter={formatChartTime}
                      tick={{ fontSize: 8, fill: "#8ba0ab" }}
                    />
                    <YAxis
                      axisLine={false}
                      tickLine={false}
                      width={32}
                      domain={metricDomain}
                      allowDecimals={false}
                      tick={{ fontSize: 8, fill: "#8ba0ab" }}
                    />
                    <Tooltip
                      contentStyle={{
                        borderRadius: 10,
                        border: "1px solid #dce8ee",
                        fontSize: 10,
                      }}
                      labelStyle={{ color: "#6b8290", fontSize: 9 }}
                      labelFormatter={formatChartTime}
                      formatter={(value) => [
                        `${value} ${metricDefinition.unit}`,
                        metricDefinition.label,
                      ]}
                    />
                    <Line
                      type="monotone"
                      dataKey="value"
                      name={`${metricDefinition.label} (${metricDefinition.unit})`}
                      stroke={metricDefinition.color}
                      strokeWidth={2}
                      dot={false}
                      connectNulls
                      isAnimationActive={false}
                    />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <div className="flex h-full items-center justify-center px-4">
                  <div className="max-w-[300px] text-center">
                    <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-[#eef9fb]">
                      <RadioTower size={20} className="text-[#08a9c4]" />
                    </div>
                    <p className="mt-3 text-[11px] font-semibold text-[#102a3a]">
                      No {metricDefinition.label} data available for this time range.
                    </p>
                    <p className="mt-1 text-[9px] leading-4 text-[#7d929e]">
                      No valid readings for this capability are available in the selected range.
                    </p>
                  </div>
                </div>
              )}
            </div>
            <div className="mt-3 flex flex-col gap-2 border-t border-[#edf2f4] pt-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex flex-wrap items-center gap-4 text-[8px] text-[#8ba0ab]">
                <span>
                  Last reading:{" "}
                  <strong className="font-medium text-[#526b79]">
                    {lastReadingTime}
                  </strong>
                </span>
                <span>
                  Data points:{" "}
                  <strong className="font-medium text-[#526b79]">
                    {chartData.length}
                  </strong>
                </span>
              </div>
              <span className="text-[8px] text-[#9aadb6]">
                History refreshes every 5 seconds
              </span>
            </div>
          </div>
          <div className="self-start rounded-[16px] border border-[#dce8ee] bg-white p-5 shadow-[0_8px_24px_rgba(16,42,58,0.06)] sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <h2 className="text-[15px] font-semibold text-[#102a3a]">
                  Device controls
                </h2>
                <p className="mt-1 text-[10px] text-[#6b8290]">
                  Remote actuator configuration
                </p>
              </div>
              <span
                className={`rounded-full px-2.5 py-1 text-[8px] font-medium ${
                  isOnline
                    ? "bg-[#eaf8f3] text-[#16a57a]"
                    : "bg-red-50 text-red-500"
                }`}
              >
                {isOnline ? "MQTT connected" : "Device offline"}
              </span>
            </div>
            {hasConfiguredControl ? (
              <>
                {supportsTemperatureSetpoint ? (
                  <div className="mt-4 min-h-[104px] rounded-[14px] border border-[#e0edf1] bg-[#f8fbfd] p-4 opacity-70 shadow-[0_3px_10px_rgba(16,42,58,0.025)]">
                    <div className="flex justify-between gap-3">
                      <div className="flex min-w-0 flex-1 items-start gap-3">
                        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[10px] bg-[#eaf9fc] text-[#08a9c4]">
                          <Thermometer size={15} />
                        </div>
                        <div className="min-w-0">
                          <span className="text-[10px] font-semibold text-[#425c6b]">
                            Temperature set point
                          </span>
                          <p className="mt-1 text-[8px] text-[#8da1ac]">
                            Automatic temperature target
                          </p>
                        </div>
                      </div>
                      <span className="w-[38px] shrink-0 text-right text-[15px] font-semibold text-[#08a9c4]">
                        —
                      </span>
                    </div>
                    <div className="relative mt-4 h-1.5 rounded-full bg-[#dce8ee]">
                      <span className="absolute left-0 top-0 h-1.5 w-[50%] rounded-full bg-[#a4dfe8]" />
                      <span className="absolute left-[50%] top-1/2 h-3 w-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-[#08a9c4] shadow" />
                    </div>
                    <div className="mt-2 flex justify-between text-[8px] text-[#9aadb6]">
                      <span>16°C</span>
                      <span>28°C</span>
                    </div>
                  </div>
                ) : null}
                {supportsAutoMode ? (
                  <div className="mt-3 flex min-h-[72px] items-center justify-between gap-4 rounded-[14px] border border-[#e0edf1] bg-[#f8fbfd] p-4 shadow-[0_3px_10px_rgba(16,42,58,0.025)]">
                    <div className="flex min-w-0 flex-1 items-center gap-3">
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[10px] bg-[#eef4ff] text-[#4d7ee8]">
                        <Bot size={15} />
                      </div>
                      <div className="min-w-0">
                        <p className="text-[10px] font-semibold text-[#425c6b]">
                          Auto mode
                        </p>
                        <p className="mt-1 text-[8px] leading-3 text-[#8da1ac]">
                          Automatically control connected actuator
                        </p>
                      </div>
                    </div>
                    <DisabledToggle />
                  </div>
                ) : null}
                {supportsEcoSchedule ? (
                  <div className="mt-3 flex min-h-[72px] items-center justify-between gap-4 rounded-[14px] border border-[#e0edf1] bg-[#f8fbfd] p-4 shadow-[0_3px_10px_rgba(16,42,58,0.025)]">
                    <div className="flex min-w-0 flex-1 items-center gap-3">
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[10px] bg-[#eaf8f3] text-[#16a57a]">
                        <Leaf size={15} />
                      </div>
                      <div className="min-w-0">
                        <p className="text-[10px] font-semibold text-[#425c6b]">
                          Eco schedule
                        </p>
                        <p className="mt-1 text-[8px] leading-3 text-[#8da1ac]">
                          Energy-saving operating schedule
                        </p>
                      </div>
                    </div>
                    <DisabledToggle />
                  </div>
                ) : null}
                {supportsDigitalOutput ? (
                  <>
                    <div className={`mt-3 flex min-h-[72px] items-center justify-between gap-4 rounded-[14px] border p-4 shadow-[0_3px_10px_rgba(16,42,58,0.025)] ${device.powerOn ? "border-[#b9e5eb] bg-[#eefafd]" : "border-[#e0edf1] bg-[#f8fbfd]"}`}>
                      <div className="flex min-w-0 flex-1 items-center gap-3">
                        <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-[10px] ${device.powerOn ? "bg-[#d9f4f8] text-[#08a9c4]" : "bg-[#edf4f6] text-[#6b8290]"}`}>
                          <Power size={15} />
                        </div>
                        <div className="min-w-0">
                          <p className="text-[10px] font-semibold text-[#425c6b]">
                            Device output
                          </p>
                          <p className="mt-1 text-[8px] leading-3 text-[#8da1ac]">
                            {controlLoading
                              ? "Sending MQTT command..."
                              : "Confirmed device power state"}
                          </p>
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={handlePowerToggle}
                        disabled={controlLoading || !isOnline}
                        className={`relative h-[22px] w-[38px] shrink-0 rounded-full transition ${
                          device.powerOn ? "bg-[#08a9c4]" : "bg-[#dce8ee]"
                        } ${
                          controlLoading || !isOnline
                            ? "cursor-not-allowed opacity-50"
                            : "cursor-pointer"
                        }`}
                        title={
                          !isOnline
                            ? "Device must be online"
                            : controlLoading
                              ? "Sending command..."
                              : device.powerOn
                                ? "Turn device off"
                                : "Turn device on"
                        }
                        aria-label={device.powerOn ? "Turn device off" : "Turn device on"}
                      >
                        <span
                          className={`absolute top-[3px] h-4 w-4 rounded-full bg-white shadow-sm transition-all ${
                            device.powerOn ? "left-[19px]" : "left-[3px]"
                          }`}
                        />
                      </button>
                    </div>
                    {controlError ? (
                      <div className="mt-4 rounded-[9px] border border-red-200 bg-red-50 px-3 py-2.5 text-[9px] text-red-600">
                        {controlError}
                      </div>
                    ) : null}
                    <div className="mt-4 flex min-h-10 w-full items-center justify-center gap-2 rounded-[11px] border border-[#dce8ee] bg-[#f8fbfd] px-3 py-2 text-[10px] font-medium text-[#526b79]">
                      <RadioTower size={14} className={isOnline ? "text-[#16a57a]" : "text-[#9aadb6]"} />
                      {controlLoading
                        ? "Sending power command..."
                        : isOnline
                          ? "Manual power control ready"
                          : "Connect device to enable control"}
                    </div>
                    <p className="mt-2 text-center text-[8px] leading-4 text-[#9aadb6]">
                      Power commands are sent through MQTT and the UI reflects the
                      confirmed device state.
                    </p>
                  </>
                ) : null}
              </>
            ) : (
              <div className="mt-4 rounded-[11px] border border-[#edf2f4] bg-[#f8fbfd] p-5 text-center">
                <p className="text-[11px] font-medium text-[#102a3a]">
                  No control capabilities configured for this device.
                </p>
                <p className="mt-1 text-[9px] text-[#6b8290]">
                  Edit the device to select supported controls.
                </p>
              </div>
            )}
          </div>
        </section>
        <section className="mt-4 grid items-start gap-4 xl:grid-cols-[1.35fr_1fr_1fr]">
          <div className="self-start rounded-[14px] border border-[#dce8ee] bg-white p-5">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-[15px] font-semibold text-[#102a3a]">
                Activity & logs
              </h2>
              <span className="text-[9px] text-[#08a9c4]">
                Live events later
              </span>
            </div>
            <div className="mt-4 flex gap-3">
              <div className="flex flex-col items-center">
                <span className="h-2 w-2 rounded-full bg-[#08a9c4]" />
                <span className="h-12 w-px bg-[#dce8ee]" />
              </div>
              <div className="min-w-0">
                <p className="text-[10px] font-medium text-[#102a3a]">
                  Device registered
                </p>
                <p className="mt-1 text-[9px] leading-4 text-[#6b8290]">
                  Device is available in your NexaIoT workspace.
                </p>
                <p className="mt-1 text-[8px] text-[#9aadb6]">
                  {createdDate}
                </p>
              </div>
            </div>
            <div className="flex gap-3">
              <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-[#dce8ee]" />
              <div className="min-w-0">
                <p className="text-[10px] font-medium text-[#102a3a]">
                  Waiting for device events
                </p>
                <p className="mt-1 text-[9px] leading-4 text-[#6b8290]">
                  MQTT activity and sensor events will appear here.
                </p>
              </div>
            </div>
          </div>
          <div className="self-start rounded-[14px] border border-[#dce8ee] bg-white p-5">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-[15px] font-semibold text-[#102a3a]">
                Device information
              </h2>
              <button
                type="button"
                onClick={openEditModal}
                className="shrink-0 text-[9px] font-medium text-[#08a9c4] transition hover:text-[#078ca2]"
              >
                Edit
              </button>
            </div>
            <div className="mt-4 space-y-3">
              <div className="flex justify-between gap-4 text-[9px]">
                <span className="shrink-0 text-[#8ba0ab]">Device ID</span>
                <span className="min-w-0 break-all text-right font-semibold text-[#425c6b]">
                  {device.deviceKey}
                </span>
              </div>
              <div className="flex justify-between gap-4 text-[9px]">
                <span className="shrink-0 text-[#8ba0ab]">Type</span>
                <span className="text-right font-semibold text-[#425c6b]">
                  {device.type}
                </span>
              </div>
              <div className="flex justify-between gap-4 text-[9px]">
                <span className="shrink-0 text-[#8ba0ab]">Location</span>
                <span className="text-right font-semibold text-[#425c6b]">
                  {device.location}
                </span>
              </div>
              <div className="flex justify-between gap-4 text-[9px]">
                <span className="text-[#8ba0ab]">Status</span>
                <span className="font-semibold text-[#425c6b]">
                  {device.status}
                </span>
              </div>
              <div className="flex justify-between gap-4 text-[9px]">
                <span className="text-[#8ba0ab]">Connectivity</span>
                <span className="font-semibold text-[#425c6b]">
                  MQTT pending
                </span>
              </div>
              <div className="flex justify-between gap-4 text-[9px]">
                <span className="text-[#8ba0ab]">Added</span>
                <span className="font-semibold text-[#425c6b]">
                  {createdDate}
                </span>
              </div>
            </div>
            <button
              type="button"
              onClick={() => {
                setDeleteError("");
                setShowDeleteConfirm(true);
              }}
              className="mt-4 flex h-9 w-full items-center justify-center gap-2 rounded-[9px] border border-red-200 bg-red-50 text-[10px] font-medium text-red-600 transition hover:bg-red-100"
            >
              <Trash2 size={13} />
              Delete device
            </button>
          </div>
          <div className="self-start rounded-[14px] border border-[#dce8ee] bg-white p-5">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-[15px] font-semibold text-[#102a3a]">
                Device alerts
              </h2>
              <button
                type="button"
                onClick={() => navigate("/alerts")}
                className="text-[9px] font-medium text-[#08a9c4] hover:text-[#0799b2]"
              >
                View all alerts
              </button>
            </div>
            {deviceAlertsLoading ? (
              <div className="mt-4 space-y-2" aria-label="Loading device alerts">
                {[1, 2].map((item) => (
                  <div key={item} className="h-[68px] animate-pulse rounded-[10px] bg-[#f8fbfd]" />
                ))}
              </div>
            ) : deviceAlertsError ? (
              <div className="mt-4 rounded-[10px] bg-[#f8fbfd] p-4 text-[10px] text-[#6b8290]">
                Unable to load alerts right now.
              </div>
            ) : deviceAlerts.length === 0 ? (
              <div className="mt-4 rounded-[10px] bg-[#f8fbfd] p-4 text-[10px] text-[#6b8290]">
                No alerts recorded for this device.
              </div>
            ) : (
              <div className="mt-4 space-y-2">
                {deviceAlerts.map((alert) => {
                  const status = alertStatusStyles[alert.status] || alertStatusStyles.ACTIVE;
                  const severity = alertSeverityStyles[alert.severity] || alertSeverityStyles.INFO;

                  return (
                    <div
                      key={alert.id}
                      className={`rounded-[10px] border p-3 ${status.container}`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className={`rounded-full px-2 py-0.5 text-[8px] font-semibold ${severity}`}>
                          {alert.severity || "INFO"}
                        </span>
                        <span className={`text-[8px] font-semibold ${status.text}`}>
                          {alert.status === "ACKNOWLEDGED" ? "Acknowledged" : alert.status === "RESOLVED" ? "Resolved" : "Active"}
                        </span>
                      </div>
                      <div className="mt-2 flex items-baseline justify-between gap-2">
                        <span className="truncate text-[10px] font-semibold text-[#102a3a]">
                          {formatAlertType(alert.type)}
                        </span>
                        <span className="shrink-0 text-[8px] text-[#8397a2]">
                          {formatAlertTime(alert.createdAt)}
                        </span>
                      </div>
                      <p className="mt-1 truncate text-[9px] text-[#6b8290]">
                        {alert.message || formatAlertType(alert.type)}
                      </p>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </section>
      </div>
      {showEditModal ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-[#102a3a]/45 px-3 py-4 backdrop-blur-[3px] sm:px-4"
          onClick={closeEditModal}
        >
          <div
            className="relative max-h-[94vh] w-full max-w-[760px] overflow-y-auto rounded-[18px] border border-[#e0e9ed] bg-white shadow-[0_30px_90px_rgba(16,42,58,0.28)] sm:rounded-[20px]"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-[#e7eef1] bg-white px-4 py-4 sm:px-6 sm:py-5">
              <div className="flex min-w-0 items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[12px] bg-[#eaf9fc] text-[#08a9c4] sm:h-11 sm:w-11">
                  <Settings2 size={20} />
                </div>
                <div className="min-w-0">
                  <h2 className="text-[17px] font-semibold text-[#102a3a] sm:text-[18px]">
                    Edit device
                  </h2>
                  <p className="mt-1 text-[9px] leading-4 text-[#6b8290] sm:text-[10px]">
                    Update device information and review system configuration.
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={closeEditModal}
                disabled={saving}
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] text-[#8397a2] transition hover:bg-[#f4f8fa] hover:text-[#102a3a]"
                aria-label="Close edit device"
              >
                <X size={17} />
              </button>
            </div>
            <form onSubmit={handleUpdate}>
              <div className="grid gap-4 p-4 lg:grid-cols-[minmax(0,1.45fr)_minmax(250px,0.8fr)] lg:gap-5 lg:p-6">
                <div className="space-y-4 lg:space-y-5">
                  <section className="rounded-[14px] border border-[#dce8ee] bg-white p-4 sm:p-5">
                    <div className="flex items-center gap-2">
                      <Pencil size={15} className="text-[#08a9c4]" />
                      <h3 className="text-[14px] font-semibold text-[#102a3a]">
                        General information
                      </h3>
                    </div>
                    <p className="mt-1 text-[9px] text-[#7d929e]">
                      These fields can be changed at any time.
                    </p>
                    <div className="mt-4 space-y-4">
                      <div>
                        <label
                          htmlFor="edit-device-name"
                          className="text-[10px] font-medium text-[#526b79]"
                        >
                          Device name
                        </label>
                        <div className="relative mt-2">
                          <Cpu
                            size={15}
                            className="absolute left-3 top-1/2 -translate-y-1/2 text-[#9aaeba]"
                          />
                          <input
                            id="edit-device-name"
                            type="text"
                            name="name"
                            value={editForm.name}
                            onChange={handleEditChange}
                            placeholder="Enter device name"
                            autoComplete="off"
                            className="h-11 w-full rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] pl-9 pr-3 text-[12px] text-[#102a3a] outline-none transition placeholder:text-[#9aaeba] focus:border-[#08a9c4] focus:bg-white focus:ring-2 focus:ring-[#08a9c4]/10"
                          />
                        </div>
                      </div>
                      <div>
                        <label
                          htmlFor="edit-device-type"
                          className="text-[10px] font-medium text-[#526b79]"
                        >
                          Device type
                        </label>
                        <div className="relative mt-2">
                          <Settings2
                            size={15}
                            className="absolute left-3 top-1/2 -translate-y-1/2 text-[#9aaeba]"
                          />
                          <input
                            id="edit-device-type"
                            type="text"
                            name="type"
                            list="device-types"
                            value={editForm.type}
                            onChange={handleEditChange}
                            placeholder="Select or enter device type"
                            autoComplete="off"
                            className="h-11 w-full rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] pl-9 pr-3 text-[12px] text-[#102a3a] outline-none transition placeholder:text-[#9aaeba] focus:border-[#08a9c4] focus:bg-white focus:ring-2 focus:ring-[#08a9c4]/10"
                          />
                          <datalist id="device-types">
                            <option value="Temperature Sensor" />
                            <option value="Humidity Sensor" />
                            <option value="Pressure Sensor" />
                            <option value="Light Sensor" />
                            <option value="Energy Meter" />
                            <option value="Gateway" />
                            <option value="Fan Controller" />
                            <option value="Relay Controller" />
                          </datalist>
                        </div>
                      </div>
                      <div>
                        <label
                          htmlFor="edit-device-location"
                          className="text-[10px] font-medium text-[#526b79]"
                        >
                          Location
                        </label>
                        <div className="relative mt-2">
                          <MapPin
                            size={15}
                            className="absolute left-3 top-1/2 -translate-y-1/2 text-[#9aaeba]"
                          />
                          <input
                            id="edit-device-location"
                            type="text"
                            name="location"
                            value={editForm.location}
                            onChange={handleEditChange}
                            placeholder="Enter device location"
                            autoComplete="off"
                            className="h-11 w-full rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] pl-9 pr-3 text-[12px] text-[#102a3a] outline-none transition placeholder:text-[#9aaeba] focus:border-[#08a9c4] focus:bg-white focus:ring-2 focus:ring-[#08a9c4]/10"
                          />
                        </div>
                      </div>
                    </div>
                  </section>
                  <CapabilitySelector
                    capabilities={editCapabilities}
                    onChange={setEditCapabilities}
                  />
                  <section className="rounded-[14px] border border-[#dce8ee] bg-[#f8fbfd] p-4 sm:p-5">
                    <div className="flex items-center gap-2">
                      <RadioTower size={15} className="text-[#08a9c4]" />
                      <h3 className="text-[14px] font-semibold text-[#102a3a]">
                        Connectivity
                      </h3>
                    </div>
                    <div className="mt-4 rounded-[10px] border border-[#dce8ee] bg-white p-4">
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div>
                          <p className="text-[10px] font-medium text-[#102a3a]">
                            MQTT configuration
                          </p>
                          <p className="mt-1 max-w-[350px] text-[9px] leading-4 text-[#7d929e]">
                            Broker, topic and hardware connection settings will
                            become editable after MQTT integration.
                          </p>
                        </div>
                        <span className="w-fit shrink-0 rounded-full bg-[#fff5df] px-2.5 py-1 text-[8px] font-medium text-[#d08a11]">
                          Coming later
                        </span>
                      </div>
                    </div>
                  </section>
                  {editError ? (
                    <div className="rounded-[10px] border border-red-200 bg-red-50 p-3 text-[10px] text-red-600">
                      {editError}
                    </div>
                  ) : null}
                </div>
                <aside className="space-y-4">
                  <section className="rounded-[14px] border border-[#dce8ee] bg-[#f8fbfd] p-4 sm:p-5">
                    <div className="flex items-center gap-2">
                      <ShieldCheck size={15} className="text-[#16a57a]" />
                      <h3 className="text-[13px] font-semibold text-[#102a3a]">
                        System information
                      </h3>
                    </div>
                    <p className="mt-1 text-[9px] text-[#7d929e]">
                      Managed automatically by NexaIoT.
                    </p>
                    <div className="mt-4 space-y-4">
                      <div>
                        <div className="flex items-center gap-2 text-[#8ba0ab]">
                          <Hash size={12} />
                          <span className="text-[8px]">Device key</span>
                        </div>
                        <p className="mt-1.5 break-all rounded-[8px] bg-white px-3 py-2 text-[9px] font-medium text-[#425c6b]">
                          {device.deviceKey}
                        </p>
                      </div>
                      <div className="border-t border-[#dce8ee] pt-3.5">
                        <span className="text-[8px] text-[#8ba0ab]">
                          Device status
                        </span>
                        <div className="mt-2 flex items-center gap-2">
                          <span
                            className={`h-2 w-2 rounded-full ${
                              isOnline
                                ? "bg-[#16a57a]"
                                : isMaintenance
                                  ? "bg-[#d49a24]"
                                  : "bg-red-500"
                            }`}
                          />
                          <span className="text-[10px] font-semibold text-[#425c6b]">
                            {device.status}
                          </span>
                        </div>
                      </div>
                      <div className="border-t border-[#dce8ee] pt-3.5">
                        <span className="text-[8px] text-[#8ba0ab]">
                          Power state
                        </span>
                        <p className="mt-1.5 text-[10px] font-semibold text-[#425c6b]">
                          {device.powerOn ? "ON" : "OFF"}
                        </p>
                      </div>
                      <div className="border-t border-[#dce8ee] pt-3.5">
                        <span className="text-[8px] text-[#8ba0ab]">
                          Created
                        </span>
                        <p className="mt-1.5 text-[10px] font-semibold text-[#425c6b]">
                          {createdDate}
                        </p>
                      </div>
                    </div>
                  </section>
                  <div className="rounded-[14px] border border-[#cfe7ed] bg-[#eef9fb] p-4">
                    <p className="text-[10px] font-semibold text-[#087b9c]">
                      Protected fields
                    </p>
                    <p className="mt-2 text-[9px] leading-4 text-[#527683]">
                      Device key, connection status and power state are managed
                      by the backend and hardware integration.
                    </p>
                  </div>
                </aside>
              </div>
              <div className="sticky bottom-0 flex flex-col gap-3 border-t border-[#e7eef1] bg-white px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
                <p className="hidden text-[9px] text-[#8ba0ab] sm:block">
                  Changes will be saved to your NexaIoT workspace.
                </p>
                <div className="flex w-full gap-2.5 sm:ml-auto sm:w-auto">
                  <button
                    type="button"
                    onClick={closeEditModal}
                    disabled={saving}
                    className="h-10 flex-1 rounded-[10px] border border-[#dce8ee] bg-white px-5 text-[11px] font-medium text-[#102a3a] transition hover:bg-[#f8fbfd] disabled:opacity-60 sm:flex-none"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={saving}
                    className="flex h-10 flex-1 items-center justify-center gap-2 rounded-[10px] bg-[#08a9c4] px-5 text-[11px] font-medium text-white transition hover:bg-[#0799b2] disabled:cursor-not-allowed disabled:opacity-60 sm:min-w-[132px] sm:flex-none"
                  >
                    <Save size={14} />
                    {saving ? "Saving..." : "Save changes"}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      ) : null}
      {showDeleteConfirm ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-[#102a3a]/45 px-4 py-5 backdrop-blur-[2px]"
          onClick={closeDeleteModal}
        >
          <div
            className="relative w-full max-w-[430px] rounded-[18px] border border-[#e4ecef] bg-white p-5 shadow-[0_28px_80px_rgba(16,42,58,0.28)] sm:p-6"
            onClick={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              onClick={closeDeleteModal}
              disabled={deleting}
              className="absolute right-4 top-4 flex h-8 w-8 items-center justify-center rounded-[8px] text-[#8397a2] transition hover:bg-[#f4f8fa]"
              aria-label="Close delete confirmation"
            >
              <X size={16} />
            </button>
            <div className="flex h-11 w-11 items-center justify-center rounded-[12px] bg-red-50 text-red-500">
              <Trash2 size={20} />
            </div>
            <h2 className="mt-4 text-[18px] font-semibold text-[#102a3a]">
              Delete this device?
            </h2>
            <p className="mt-2 text-[11px] leading-5 text-[#6b8290]">
              You are about to permanently delete{" "}
              <span className="font-semibold text-[#102a3a]">
                {device.name}
              </span>
              .
            </p>
            <div className="mt-4 rounded-[10px] border border-red-100 bg-red-50 px-4 py-3 text-[10px] text-red-600">
              This action cannot be undone.
            </div>
            {deleteError ? (
              <div className="mt-4 rounded-[10px] border border-red-200 bg-red-50 p-3 text-[10px] text-red-600">
                {deleteError}
              </div>
            ) : null}
            <div className="mt-6 flex flex-col-reverse gap-2.5 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={closeDeleteModal}
                disabled={deleting}
                className="h-10 rounded-[10px] border border-[#dce8ee] bg-white px-5 text-[11px] font-medium text-[#102a3a] transition hover:bg-[#f8fbfd] disabled:opacity-60"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleting}
                className="flex h-10 items-center justify-center gap-2 rounded-[10px] bg-red-500 px-5 text-[11px] font-medium text-white transition hover:bg-red-600 disabled:opacity-60"
              >
                <Trash2 size={14} />
                {deleting ? "Deleting..." : "Delete device"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );

}
