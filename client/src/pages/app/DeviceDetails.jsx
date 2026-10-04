import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Activity,
  BatteryMedium,
  CalendarDays,
  CheckCircle2,
  CircleAlert,
  Cpu,
  Droplets,
  Hash,
  KeyRound,
  MapPin,
  Pencil,
  RadioTower,
  Save,
  Settings2,
  ShieldCheck,
  Thermometer,
  Trash2,
  Wifi,
  X,
  Zap,
} from "lucide-react";

import {
  deleteDevice,
  getDeviceById,
  getLatestDeviceReading,
  updateDevice,
} from "../../api/devicesApi.js";

function TelemetryCard({
  icon: Icon,
  value,
  label,
  note,
  tone = "cyan",
  active = false,
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
          <Icon size={17} className={selected.text} />
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
  const [latestReading, setLatestReading] = useState(null);

  const [showEditModal, setShowEditModal] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editError, setEditError] = useState("");

  const [editForm, setEditForm] = useState({
    name: "",
    type: "",
    location: "",
  });

  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");

  useEffect(() => {
    async function loadDevice() {
      try {
        setLoading(true);
        setError("");

        const data = await getDeviceById(id);
        setDevice(data);
      } catch (err) {
        setError(err.message || "Unable to load device.");
      } finally {
        setLoading(false);
      }
    }

    loadDevice();
  }, [id]);

    useEffect(() => {
      async function loadLatestReading() {
        try {
          const data = await getLatestDeviceReading(id);
          setLatestReading(data);
        } catch (err) {
          console.error("Unable to load latest telemetry:", err);
        }
      }

      loadLatestReading();

      const interval = setInterval(() => {
        loadLatestReading();
      }, 5000);

      return () => clearInterval(interval);
    }, [id]);
    
  const openEditModal = () => {
    setEditForm({
      name: device.name || "",
      type: device.type || "",
      location: device.location || "",
    });

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

        <section className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
          <TelemetryCard
            icon={Thermometer}
            value={
              latestReading?.metrics?.temperature != null
                ? `${latestReading.metrics.temperature} °C`
                : "—"
            }
            label="Temperature"
            note={latestReading ? "Latest MQTT reading" : "Waiting for telemetry"}
            tone="cyan"
          />

          <TelemetryCard
            icon={Droplets}
            value={
              latestReading?.metrics?.humidity != null
                ? `${latestReading.metrics.humidity} %`
                : "—"
            }
            label="Humidity"
            note={latestReading ? "Latest MQTT reading" : "Waiting for telemetry"}
            tone="blue"
          />

          <TelemetryCard
            icon={Zap}
            value={device.powerOn ? "ON" : "OFF"}
            label="Power"
            note="Current backend state"
            tone="amber"
            active={device.powerOn}
          />

          <TelemetryCard
            icon={Wifi}
            value={device.status}
            label="Connectivity"
            note={
              isOnline
                ? "Device is connected"
                : "No live device connection"
            }
            tone="green"
            active={isOnline}
          />

          <TelemetryCard
            icon={BatteryMedium}
            value={
              latestReading?.battery != null
                ? `${latestReading.battery} %`
                : "—"
            }
            label="Battery"
            note={latestReading ? "Latest MQTT reading" : "Waiting for telemetry"}
            tone="violet"
          />
        </section>

        <section className="mt-4 grid items-start gap-4 xl:grid-cols-[minmax(0,2fr)_340px]">
          <div className="rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(16,42,58,0.05)] sm:p-5">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <Activity size={16} className="text-[#08a9c4]" />

                  <h2 className="text-[15px] font-semibold text-[#102a3a]">
                    Real-time telemetry
                  </h2>
                </div>

                <p className="mt-1 text-[10px] text-[#6b8290]">
                  Live temperature and humidity measurements
                </p>
              </div>

              <div className="flex w-fit max-w-full overflow-x-auto rounded-[9px] border border-[#e5edf1] bg-[#f8fbfd] p-1">
                {["1H", "6H", "24H", "7D"].map((period) => (
                  <button
                    key={period}
                    type="button"
                    disabled
                    title="Available when telemetry data is connected"
                    className={`h-7 min-w-[38px] cursor-not-allowed rounded-[7px] px-2 text-[9px] font-medium ${
                      period === "6H"
                        ? "bg-white text-[#08a9c4] shadow-sm"
                        : "text-[#8ca0ab]"
                    }`}
                  >
                    {period}
                  </button>
                ))}
              </div>
            </div>

            <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2">
              <div className="flex items-center gap-2">
                <span className="h-2 w-2 rounded-full bg-[#08a9c4]" />

                <span className="text-[9px] text-[#6b8290]">
                  Temperature
                </span>

                <span className="text-[9px] font-semibold text-[#102a3a]">
                  {latestReading?.metrics?.temperature != null
                    ? `${latestReading.metrics.temperature} °C`
                    : "—"}
                </span>
              </div>

              <div className="flex items-center gap-2">
                <span className="h-2 w-2 rounded-full bg-[#7568e8]" />

                <span className="text-[9px] text-[#6b8290]">
                  Humidity
                </span>

                <span className="text-[9px] font-semibold text-[#102a3a]">
                  {latestReading?.metrics?.humidity != null
                    ? `${latestReading.metrics.humidity} %`
                    : "—"}
                </span>
              </div>

              <div className="flex items-center gap-1.5 text-[8px] text-[#8ba0ab] sm:ml-auto">
                <span
                  className={`h-1.5 w-1.5 rounded-full ${
                    isOnline ? "bg-[#16a57a]" : "bg-[#c7d4da]"
                  }`}
                />

                {isOnline ? "Device online" : "Telemetry disconnected"}
              </div>
            </div>

            <div className="relative mt-4 h-[220px] overflow-hidden rounded-[12px] border border-[#edf2f4] bg-[#fcfefe] sm:h-[235px]">
              <div className="absolute inset-0 flex flex-col justify-evenly px-5 sm:px-10">
                {[1, 2, 3, 4, 5].map((line) => (
                  <div
                    key={line}
                    className="h-px w-full border-t border-dashed border-[#e5edf1]"
                  />
                ))}
              </div>

              <div className="absolute bottom-4 left-5 right-5 hidden justify-between text-[8px] text-[#a0b0b8] sm:flex sm:left-10">
                <span>Now</span>
                <span>-1h</span>
                <span>-2h</span>
                <span>-3h</span>
                <span>-4h</span>
                <span>-5h</span>
              </div>

              <div className="relative z-[1] flex h-full items-center justify-center px-4 pb-5">
                <div className="max-w-[300px] text-center">
                  <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-[#eef9fb]">
                    <RadioTower size={20} className="text-[#08a9c4]" />
                  </div>

                  <p className="mt-3 text-[11px] font-semibold text-[#102a3a]">
                    Waiting for telemetry
                  </p>

                  <p className="mt-1 text-[9px] leading-4 text-[#7d929e]">
                    Live sensor measurements will automatically appear here
                    once this device is connected through MQTT.
                  </p>

                  <div className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-[#f4f8fa] px-3 py-1.5 text-[8px] text-[#80949f]">
                    <RadioTower size={10} />
                    MQTT integration required
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-3 flex flex-col gap-2 border-t border-[#edf2f4] pt-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex flex-wrap items-center gap-4 text-[8px] text-[#8ba0ab]">
                <span>
                  Last reading:{" "}
                  <strong className="font-medium text-[#526b79]">—</strong>
                </span>

                <span>
                  Data points:{" "}
                  <strong className="font-medium text-[#526b79]">0</strong>
                </span>
              </div>

              <span className="text-[8px] text-[#9aadb6]">
                Sensor history available after sensor integration
              </span>
            </div>
          </div>

          <div className="self-start rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(16,42,58,0.05)] sm:p-5">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <h2 className="text-[15px] font-semibold text-[#102a3a]">
                  Device controls
                </h2>

                <p className="mt-1 text-[10px] text-[#6b8290]">
                  Remote actuator configuration
                </p>
              </div>

              <span className="rounded-full bg-[#fff5df] px-2.5 py-1 text-[8px] font-medium text-[#d08a11]">
                MQTT required
              </span>
            </div>

            <div className="mt-4 rounded-[11px] border border-[#edf2f4] bg-[#f8fbfd] p-4 opacity-70">
              <div className="flex justify-between gap-3">
                <div>
                  <span className="text-[10px] font-medium text-[#425c6b]">
                    Temperature set point
                  </span>

                  <p className="mt-1 text-[8px] text-[#8da1ac]">
                    Automatic temperature target
                  </p>
                </div>

                <span className="text-[15px] font-semibold text-[#08a9c4]">
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

            <div className="mt-4 divide-y divide-[#edf2f4]">
              <div className="flex items-center justify-between gap-4 pb-3.5">
                <div>
                  <p className="text-[10px] font-medium text-[#425c6b]">
                    Auto mode
                  </p>

                  <p className="mt-1 text-[8px] leading-3 text-[#8da1ac]">
                    Automatically control connected actuator
                  </p>
                </div>

                <DisabledToggle />
              </div>

              <div className="flex items-center justify-between gap-4 py-3.5">
                <div>
                  <p className="text-[10px] font-medium text-[#425c6b]">
                    Eco schedule
                  </p>

                  <p className="mt-1 text-[8px] leading-3 text-[#8da1ac]">
                    Energy-saving operating schedule
                  </p>
                </div>

                <DisabledToggle />
              </div>

              <div className="flex items-center justify-between gap-4 pt-3.5">
                <div>
                  <p className="text-[10px] font-medium text-[#425c6b]">
                    Device output
                  </p>

                  <p className="mt-1 text-[8px] leading-3 text-[#8da1ac]">
                    Current backend power state
                  </p>
                </div>

                <DisabledToggle enabled={device.powerOn} />
              </div>
            </div>

            <button
              type="button"
              disabled
              title="Device control will be enabled after MQTT actuator integration"
              className="mt-4 flex h-10 w-full cursor-not-allowed items-center justify-center gap-2 rounded-[9px] bg-[#08a9c4] text-[11px] font-medium text-white opacity-50"
            >
              <CheckCircle2 size={14} />
              Apply controls
            </button>

            <p className="mt-2 text-center text-[8px] leading-4 text-[#9aadb6]">
              Controls are locked until secure MQTT actuator communication is
              configured.
            </p>
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

              <span className="text-[9px] text-[#08a9c4]">
                Alert system later
              </span>
            </div>

            {!isOnline ? (
              <div className="mt-4 rounded-[10px] bg-[#fff4df] p-4">
                <div className="flex items-center gap-2 text-[#d08a11]">
                  <CircleAlert size={14} />

                  <span className="text-[9px] font-semibold">
                    Device offline
                  </span>
                </div>

                <p className="mt-2 text-[10px] font-medium text-[#425c6b]">
                  No active telemetry connection
                </p>

                <p className="mt-1 text-[8px] leading-4 text-[#8ba0ab]">
                  Live connectivity monitoring will become available after
                  MQTT integration.
                </p>
              </div>
            ) : (
              <div className="mt-4 rounded-[10px] bg-[#eaf8f3] p-4">
                <div className="flex items-center gap-2 text-[#16a57a]">
                  <CheckCircle2 size={14} />

                  <span className="text-[9px] font-semibold">
                    Device is online
                  </span>
                </div>

                <p className="mt-2 text-[8px] leading-4 text-[#6f9083]">
                  Detailed sensor alerts will appear when the alert backend is
                  implemented.
                </p>
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