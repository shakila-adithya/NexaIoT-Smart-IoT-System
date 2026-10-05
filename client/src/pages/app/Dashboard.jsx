import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ChartNoAxesCombined,
  Cpu,
  BatteryMedium,
  Droplets,
  Plus,
  RadioTower,
  Signal,
  Thermometer,
  TriangleAlert,
  Wifi,
  WifiOff,
} from "lucide-react";

import DashboardStatCard from "../../components/dashboard/DashboardStatCard.jsx";
import LatestAlerts from "../../components/dashboard/LatestAlerts.jsx";
import QuickActions from "../../components/dashboard/QuickActions.jsx";
import PageHero from "../../components/common/PageHero.jsx";
import { getDevices, getLatestDeviceReading } from "../../api/devicesApi.js";
import { getAlerts } from "../../api/alertsApi.js";
import { useAuth } from "../../hooks/useAuth.js";

function formatTelemetryTime(value) {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return "—";

  return date.toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function getSignalLabel(value) {
  if (value == null) return "—";
  if (value >= -55) return "Strong";
  if (value >= -70) return "Good";
  return "Weak";
}

function TelemetryMetric({ icon: Icon, label, value, unit, note, tone }) {
  const tones = {
    orange: "bg-[#fff4e5] text-[#e79a23]",
    blue: "bg-[#eef4ff] text-[#4d7ee8]",
    green: "bg-[#eaf8f3] text-[#16a57a]",
    violet: "bg-[#f1efff] text-[#7664d8]",
  };

  return (
    <div className="rounded-[12px] border border-[#e7eff3] bg-gradient-to-br from-white to-[#f7fbfd] p-3.5 shadow-[0_3px_10px_rgba(16,42,58,0.03)] transition hover:-translate-y-0.5 hover:shadow-[0_6px_16px_rgba(16,42,58,0.07)]">
      <div className="flex items-center gap-2">
        <span className={`flex h-7 w-7 items-center justify-center rounded-[8px] ${tones[tone] || tones.blue}`}>
          <Icon size={14} />
        </span>
        <div className="text-[9px] font-semibold uppercase tracking-[0.06em] text-[#8ba0ab]">
          {label}
        </div>
      </div>
      <div className="mt-3 text-[20px] font-semibold tracking-[-0.02em] text-[#102a3a]">
        {value == null ? "—" : value}
        {value != null ? <span className="ml-1 text-[10px] font-medium tracking-normal text-[#6b8290]">{unit}</span> : null}
      </div>
      {note ? <div className="mt-1 text-[9px] font-medium text-[#6b8290]">{note}</div> : null}
    </div>
  );
}

export default function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [devices, setDevices] = useState([]);
  const [activeAlertCount, setActiveAlertCount] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [alertsError, setAlertsError] = useState("");
  const [telemetry, setTelemetry] = useState(null);
  const pollingInFlight = useRef(false);

  const firstName =
    user?.fullName?.trim()?.split(/\s+/)?.[0] || "User";

  const hour = new Date().getHours();

  const greeting =
    hour < 12
      ? "Good morning"
      : hour < 18
        ? "Good afternoon"
        : "Good evening";

  useEffect(() => {
    let cancelled = false;

    async function loadDashboardData({ initial = false } = {}) {
      if (pollingInFlight.current) return;

      pollingInFlight.current = true;

      try {
        const [devicesResult, alertsResult] = await Promise.allSettled([
          getDevices(),
          getAlerts(),
        ]);

        if (cancelled) return;

        if (devicesResult.status === "fulfilled") {
          const deviceList = Array.isArray(devicesResult.value) ? devicesResult.value : [];

          setDevices(deviceList);
          setError("");

          if (deviceList.length === 0) {
            setTelemetry(null);
          } else {
            const readingResults = await Promise.allSettled(
              deviceList.map((device) => getLatestDeviceReading(device.id)),
            );

            if (!cancelled) {
              const candidates = readingResults
                .map((result, index) => ({
                  result,
                  device: deviceList[index],
                }))
                .filter(({ result }) => result.status === "fulfilled" && result.value);

              const latest = candidates
                .sort((first, second) => {
                  const firstTime = new Date(first.result.value.receivedAt).getTime();
                  const secondTime = new Date(second.result.value.receivedAt).getTime();

                  if (Number.isNaN(firstTime)) return Number.isNaN(secondTime) ? 0 : 1;
                  if (Number.isNaN(secondTime)) return -1;
                  return secondTime - firstTime;
                })
                .at(0) || null;

              setTelemetry(
                latest
                  ? {
                      device: latest.device,
                      reading: latest.result.value,
                    }
                  : null,
              );
            }
          }
        } else if (initial) {
          setError(devicesResult.reason?.message || "Unable to load dashboard data.");
        }

        if (alertsResult.status === "fulfilled") {
          const alerts = Array.isArray(alertsResult.value) ? alertsResult.value : [];

          setActiveAlertCount(
            alerts.filter(
              (alert) =>
                alert.status === "ACTIVE" || alert.status === "ACKNOWLEDGED",
            ).length,
          );
          setAlertsError("");
        } else if (initial) {
          setAlertsError(alertsResult.reason?.message || "Unable to load alerts.");
        }
      } finally {
        pollingInFlight.current = false;

        if (initial && !cancelled) {
          setLoading(false);
        }
      }
    }

    loadDashboardData({ initial: true });

    const intervalId = window.setInterval(() => {
      loadDashboardData();
    }, 5000);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
      pollingInFlight.current = false;
    };
  }, []);

  const stats = useMemo(() => {
    const total = devices.length;

    const online = devices.filter(
      (device) => device.status === "ONLINE",
    ).length;

    const offline = devices.filter(
      (device) => device.status === "OFFLINE",
    ).length;

    const maintenance = devices.filter(
      (device) => device.status === "MAINTENANCE",
    ).length;

    const onlinePercentage =
      total > 0 ? Math.round((online / total) * 100) : 0;

    return {
      total,
      online,
      offline,
      maintenance,
      onlinePercentage,
    };
  }, [devices]);

  const networkTitle =
    stats.total === 0
      ? "Start building your IoT network."
      : stats.offline === 0 && stats.maintenance === 0
        ? "Your registered devices are online."
        : "Your IoT network needs attention.";

  const networkDescription =
    stats.total === 0
      ? "Register your first IoT device to begin monitoring your connected environment."
      : `${stats.total} devices registered · ${stats.online} online · ${stats.offline} offline · ${stats.maintenance} maintenance`;

  return (
    <div className="w-full px-4 pb-9 pt-[26px] md:px-[30px]">
      <div className="mx-auto w-full max-w-[1144px]">
        <section className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-[26px] font-normal leading-tight text-[#102a3a]">
              {greeting}, {firstName}
            </h1>

            <p className="mt-[5px] text-[12px] text-[#6b8290]">
              Here&apos;s what&apos;s happening across your IoT network today.
            </p>
          </div>

          <div className="flex gap-2.5">
            <button
              type="button"
              onClick={() => navigate("/devices/register")}
              className="flex h-10 items-center justify-center gap-2 rounded-[10px] bg-[#08a9c4] px-4 text-[12px] font-medium text-white transition hover:bg-[#0799b2]"
            >
              <Plus size={15} />
              Add device
            </button>

            <button
              type="button"
              onClick={() => navigate("/reports")}
              className="flex h-10 items-center justify-center gap-2 rounded-[10px] border border-[#dce8ee] bg-white px-4 text-[12px] font-medium text-[#102a3a] transition hover:bg-[#f8fbfd]"
            >
              <ChartNoAxesCombined size={15} />
              View reports
            </button>
          </div>
        </section>

        <div className="mt-6">
          <PageHero
            eyebrow="Network overview"
            title={
              loading
                ? "Loading your IoT network..."
                : networkTitle
            }
            description={
              loading
                ? "Checking your registered devices..."
                : error
                  ? "Unable to load your current device information."
                  : networkDescription
            }
            image="https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=1400&q=85"
            imageAlt="Connected IoT network infrastructure"
          >
            <button
              type="button"
              onClick={() => navigate("/devices")}
              className="flex h-9 items-center rounded-[9px] border border-white/30 bg-white/15 px-4 text-[11px] font-medium text-white transition hover:bg-white/25"
            >
              View devices
            </button>
          </PageHero>
        </div>

        {error ? (
          <div className="mt-6 rounded-[14px] border border-red-200 bg-red-50 p-4 text-[12px] text-red-600">
            {error}
          </div>
        ) : null}

        <section className="mt-6 grid gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
          <DashboardStatCard
            icon={Cpu}
            value={loading || error ? "—" : stats.total}
            label="Total devices"
            trend={
              loading
                ? "Checking..."
                : error
                  ? "Unavailable"
                : stats.total === 1
                  ? "1 registered device"
                  : `${stats.total} registered devices`
            }
            tone="cyan"
          />

          <DashboardStatCard
            icon={Wifi}
            value={loading || error ? "—" : stats.online}
            label="Online devices"
            trend={
              loading
                ? "Checking..."
                : error
                  ? "Unavailable"
                : `${stats.onlinePercentage}% online`
            }
            tone="green"
          />

          <DashboardStatCard
            icon={WifiOff}
            value={loading || error ? "—" : stats.offline}
            label="Offline devices"
            trend={
              loading
                ? "Checking..."
                : error
                  ? "Unavailable"
                : stats.offline > 0
                  ? "Needs attention"
                  : "No offline devices"
            }
            tone="amber"
          />

          <DashboardStatCard
            icon={TriangleAlert}
            value={loading || activeAlertCount === null ? "—" : activeAlertCount}
            label="Active alerts"
            trend={
              loading
                ? "Checking..."
                : alertsError || activeAlertCount === null
                  ? "Unavailable"
                : activeAlertCount > 0
                  ? "Needs attention"
                  : "No active alerts"
            }
            tone="red"
          />
        </section>

        <section className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,760px)_minmax(300px,368px)]">
          <div className="relative overflow-hidden rounded-[14px] border border-[#dce8ee] bg-gradient-to-br from-white via-white to-[#f3fbfd] p-5 shadow-[0_6px_20px_rgba(10,48,72,0.07)]">
            <div className="pointer-events-none absolute -right-16 -top-20 h-44 w-44 rounded-full bg-[#dff6fa]/60 blur-2xl" />
            <div className="flex items-start justify-between gap-4">
              <div className="relative">
                <div className="flex items-center gap-2.5">
                  <span className="flex h-9 w-9 items-center justify-center rounded-[10px] bg-[#e8f8fb] text-[#08a9c4] shadow-[0_4px_10px_rgba(8,169,196,0.12)]">
                    <RadioTower size={17} />
                  </span>
                  <div>
                    <h2 className="text-[15px] font-semibold text-[#102a3a]">
                      Live telemetry
                    </h2>

                    <p className="mt-1 text-[11px] text-[#6b8290]">
                      Latest reading from your most recently reporting device.
                    </p>
                  </div>
                </div>
              </div>

              <span className={`relative inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[9px] font-semibold ${telemetry ? "bg-[#eaf8f3] text-[#16815f]" : "bg-[#edf4f6] text-[#6b8290]"}`}>
                <span className={`h-1.5 w-1.5 rounded-full ${telemetry ? "bg-[#16a57a]" : "bg-[#9aadb6]"}`} />
                {telemetry ? "Latest reading" : "Waiting"}
              </span>
            </div>

            {loading ? (
              <div className="mt-5 grid min-h-[180px] animate-pulse grid-cols-2 gap-3 rounded-[12px] border border-dashed border-[#dce8ee] bg-[#f8fbfd] p-4 sm:grid-cols-4">
                {[1, 2, 3, 4].map((item) => (
                  <div key={item} className="rounded-[10px] bg-white/70" />
                ))}
              </div>
            ) : stats.total === 0 ? (
              <div className="mt-5 flex min-h-[180px] items-center justify-center rounded-[12px] border border-dashed border-[#dce8ee] bg-[#f8fbfd] p-4 text-center text-[11px] text-[#6b8290]">
                No devices available
              </div>
            ) : !telemetry ? (
              <div className="mt-5 flex min-h-[180px] items-center justify-center rounded-[12px] border border-dashed border-[#dce8ee] bg-[#f8fbfd] p-4 text-center text-[11px] text-[#6b8290]">
                No telemetry received yet
              </div>
            ) : (
              <div className="relative mt-5">
                <div className="mb-4 flex flex-col gap-3 rounded-[12px] border border-[#e7eff3] bg-white/75 p-3.5 sm:flex-row sm:items-center sm:justify-between">
                  <div className="flex min-w-0 items-center gap-3">
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-[#eef9fb] text-[#08a9c4]">
                      <Cpu size={16} />
                    </span>
                    <div className="min-w-0">
                      <p className="truncate text-[12px] font-semibold text-[#102a3a]">
                      {telemetry.device.name || telemetry.device.deviceKey || "Unnamed device"}
                      </p>
                      <p className="mt-1 truncate text-[9px] text-[#8397a2]">
                        {telemetry.device.deviceKey || "Device"}
                      </p>
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-1.5 text-[9px] text-[#6b8290] sm:text-right">
                    <span className="h-1.5 w-1.5 rounded-full bg-[#16a57a]" />
                    <span>Updated {formatTelemetryTime(telemetry.reading.receivedAt)}</span>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  <TelemetryMetric
                    icon={Thermometer}
                    label="Temperature"
                    value={telemetry.reading.metrics?.temperature}
                    unit="°C"
                    tone="orange"
                  />
                  <TelemetryMetric
                    icon={Droplets}
                    label="Humidity"
                    value={telemetry.reading.metrics?.humidity}
                    unit="%"
                    tone="blue"
                  />
                  <TelemetryMetric
                    icon={BatteryMedium}
                    label="Battery"
                    value={telemetry.reading.battery}
                    unit="%"
                    tone="green"
                  />
                  <TelemetryMetric
                    icon={Signal}
                    label="Signal"
                    value={telemetry.reading.rssi}
                    unit="dBm"
                    note={getSignalLabel(telemetry.reading.rssi)}
                    tone="violet"
                  />
                </div>
              </div>
            )}
          </div>

          <QuickActions />
        </section>

        <section className="mt-6 grid gap-4 md:grid-cols-3">
          <div className="rounded-[14px] border border-[#dce8ee] bg-white p-5">
            <div className="flex items-center justify-between">
              <h2 className="text-[14px] font-semibold text-[#102a3a]">
                Device status
              </h2>

              <button
                type="button"
                onClick={() => navigate("/devices")}
                className="text-[10px] font-medium text-[#08a9c4] hover:text-[#0799b2]"
              >
                View all
              </button>
            </div>

            <div className="mt-5 space-y-4 text-[11px]">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-[#16a57a]" />
                  <span className="text-[#6b8290]">
                    Online
                  </span>
                </div>

                <span className="font-semibold text-[#16a57a]">
                  {loading ? "—" : stats.online}
                </span>
              </div>

              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-[#e24e5a]" />
                  <span className="text-[#6b8290]">
                    Offline
                  </span>
                </div>

                <span className="font-semibold text-[#e24e5a]">
                  {loading ? "—" : stats.offline}
                </span>
              </div>

              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-[#d49a24]" />
                  <span className="text-[#6b8290]">
                    Maintenance
                  </span>
                </div>

                <span className="font-semibold text-[#d49a24]">
                  {loading ? "—" : stats.maintenance}
                </span>
              </div>
            </div>
          </div>

          <div className="rounded-[14px] border border-[#dce8ee] bg-white p-5">
            <h2 className="text-[14px] font-semibold text-[#102a3a]">
              Recent activity
            </h2>

            <div className="mt-4 rounded-[10px] bg-[#f8fbfd] p-4">
              <p className="text-[11px] font-medium text-[#102a3a]">
                No activity data yet
              </p>

              <p className="mt-1 text-[10px] leading-4 text-[#6b8290]">
                Device events and activity history will appear here after
                event logging is implemented.
              </p>
            </div>
          </div>

          <LatestAlerts />
        </section>
      </div>
    </div>
  );
}
