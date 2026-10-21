import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useOutletContext } from "react-router-dom";
import {
  ChartNoAxesCombined,
  Clock3,
  Cpu,
  Plus,
  RadioTower,
  RefreshCw,
  TriangleAlert,
  Wifi,
  WifiOff,
  Wrench,
} from "lucide-react";

import LatestAlerts from "../../components/dashboard/LatestAlerts.jsx";
import NetworkOverviewAnimation from "../../components/dashboard/NetworkOverviewAnimation.jsx";
import RecentActivity from "../../components/dashboard/RecentActivity.jsx";
import Button from "../../components/ui/Button.jsx";
import PageHeader from "../../components/ui/PageHeader.jsx";
import BentoCard from "../../components/ui/BentoCard.jsx";
import StatusBadge from "../../components/ui/StatusBadge.jsx";
import networkOverviewImage from "../../assets/dashboard/device-status/robot-iot-car.png";
import shakingHandsIllustration from "../../assets/dashboard/device-status/shaking-hands.png";
import { getDevices, getLatestDeviceReading } from "../../api/devicesApi.js";
import { useAuth } from "../../hooks/useAuth.js";
import { normalizeCapabilityManifest } from "../../config/capabilityManifest.js";
import {
  getMetricDefinitionForCapability,
} from "../../config/deviceMetricDefinitions.js";
import { normalizeUserPreferences } from "../../config/userPreferences.js";
import {
  formatDisplayDate,
  getDisplayMetricUnit,
  getDisplayMetricValue,
  isStaleReading,
} from "../../config/displayPreferences.js";

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
  const outletContext = useOutletContext() || {};
  const alerts = outletContext.alerts || [];
  const alertsLoading = outletContext.alertsLoading ?? true;
  const alertsError = outletContext.alertsError || "";
  const preferences = normalizeUserPreferences(user);

  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [telemetry, setTelemetry] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
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
        const devicesResult = await Promise.allSettled([getDevices()]);
        const deviceResult = devicesResult[0];

        if (cancelled) return;

        if (deviceResult.status === "fulfilled") {
          const deviceList = Array.isArray(deviceResult.value) ? deviceResult.value : [];

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
          setError(deviceResult.reason?.message || "Unable to load dashboard data.");
        }
      } finally {
        pollingInFlight.current = false;

        if (initial && !cancelled) {
          setLoading(false);
        }
      }
    }

    loadDashboardData({ initial: true });

    const intervalId = user?.dashboardAutoRefresh === false
      ? null
      : window.setInterval(() => {
          loadDashboardData();
        }, 5000);

    return () => {
      cancelled = true;
      if (intervalId) window.clearInterval(intervalId);
      pollingInFlight.current = false;
    };
  }, [user?.dashboardAutoRefresh, refreshKey]);

  const activeAlertCount = alertsLoading || alertsError
    ? null
    : alerts.filter((alert) => alert.status === "ACTIVE" || alert.status === "ACKNOWLEDGED").length;

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
    const unknown = devices.filter(
      (device) => !["ONLINE", "OFFLINE", "MAINTENANCE"].includes(device.status),
    ).length;

    const onlinePercentage =
      total > 0 ? Math.round((online / total) * 100) : 0;

    return {
      total,
      online,
      offline,
      maintenance,
      unknown,
      onlinePercentage,
    };
  }, [devices]);

  const networkTitle =
    stats.total === 0
      ? "Start building your IoT network."
      : stats.offline === 0 && stats.maintenance === 0 && stats.unknown === 0
        ? "Your registered devices are online."
        : "Your IoT network needs attention.";

  const networkStatus = loading
    ? "Checking"
    : error
      ? "Unavailable"
      : stats.total === 0
        ? "No devices"
        : stats.offline === 0 && stats.maintenance === 0 && stats.unknown === 0
          ? "Healthy"
          : "Needs attention";
  const networkStatusTone = loading || stats.total === 0 ? "neutral" : error ? "danger" : stats.offline === 0 && stats.maintenance === 0 && stats.unknown === 0 ? "success" : "warning";
  const networkInsightMessage = stats.maintenance > 0
    ? `${stats.maintenance} device${stats.maintenance === 1 ? " is" : "s are"} currently under maintenance.`
    : stats.offline > 0
      ? `${stats.offline} device${stats.offline === 1 ? "" : "s"} currently needs attention.`
      : stats.total > 0 && stats.online === stats.total
        ? "All registered devices are currently online."
        : stats.unknown > 0
          ? `${stats.unknown} device${stats.unknown === 1 ? " has" : "s have"} an unavailable status.`
          : "No registered devices yet.";

  const dashboardMetricDefinitions = normalizeCapabilityManifest(telemetry?.device)
    .filter((capability) => capability.category !== "CONTROL")
    .map(getMetricDefinitionForCapability)
    .filter(Boolean);

  return (
    <div className={`min-w-0 max-w-full px-4 pb-9 ${preferences.compactMode ? "pt-5" : "pt-7"} md:px-8 lg:px-[30px]`}>
      <div className="mx-auto min-w-0 w-full max-w-[1240px]">
        <PageHeader
          title={<><span>{greeting}, </span><span className="text-[var(--topic-telemetry)]">{firstName}</span></>}
          description={loading ? "Checking your registered devices..." : "Monitor your connected devices and recent activity."}
          actions={(
            <>
              <Button type="button" onClick={() => navigate("/devices/register")} icon={Plus}>Add device</Button>
              <Button type="button" variant="secondary" onClick={() => navigate("/reports")} icon={ChartNoAxesCombined}>View reports</Button>
              <Button type="button" variant="secondary" onClick={() => setRefreshKey((current) => current + 1)} loading={loading} icon={RefreshCw}>Refresh</Button>
            </>
          )}
        />

        <section className="network-overview-card mt-6" aria-labelledby="network-overview-title">
          <div className="network-overview-content">
            <NetworkOverviewAnimation />
            <header className="network-overview-header">
              <div className="network-overview-heading">
                <span className="network-overview-icon" aria-hidden="true">
                  <img src={shakingHandsIllustration} alt="" className="network-overview-hand h-9 w-9 object-contain" />
                </span>
                <div className="min-w-0">
                  <div className="network-overview-title-row">
                    <h2 id="network-overview-title" className="topic-heading topic-telemetry">Network overview</h2>
                    <StatusBadge tone={networkStatusTone}>{networkStatus}</StatusBadge>
                  </div>
                  <p className="network-overview-support">{loading ? "Loading your IoT network..." : error ? "Unable to load your current device information." : networkTitle}</p>
                </div>
              </div>
            </header>

            <div className="network-overview-insights" aria-label="Operational network insights">
              <p className="network-overview-insight-message">
                {loading ? "Loading network insights..." : error ? "Network insights are temporarily unavailable." : networkInsightMessage}
              </p>
              <div className="network-overview-insight-row">
                <span className={`network-overview-insight-chip insight-alert ${activeAlertCount > 0 ? "insight-danger" : "insight-neutral"}`}>
                  <TriangleAlert size={14} aria-hidden="true" />
                  <strong>{activeAlertCount === null || loading || error ? "—" : activeAlertCount}</strong>
                  <span>{activeAlertCount === 1 ? "active alert" : "active alerts"}</span>
                </span>
                <span className="network-overview-insight-chip insight-neutral insight-updated">
                  <Clock3 size={14} aria-hidden="true" />
                  <strong>Updated</strong>
                  <span>{loading || error ? "—" : formatDisplayDate(telemetry?.reading?.receivedAt, preferences.dateFormat)}</span>
                </span>
              </div>
            </div>

            <div className="network-overview-metrics" aria-label="Network status summary">
              <div className="network-overview-metric metric-registered">
                <span className="network-overview-metric-icon" aria-hidden="true"><Cpu size={16} /></span>
                <div><strong>{loading || error ? "—" : stats.total}</strong><span>Registered</span></div>
              </div>
              <div className="network-overview-metric metric-online">
                <span className="network-overview-metric-icon" aria-hidden="true"><Wifi size={16} /></span>
                <div><strong>{loading || error ? "—" : stats.online}</strong><span>Online</span></div>
              </div>
              <div className="network-overview-metric metric-offline">
                <span className="network-overview-metric-icon" aria-hidden="true"><WifiOff size={16} /></span>
                <div><strong>{loading || error ? "—" : stats.offline}</strong><span>Offline</span></div>
              </div>
              <div className="network-overview-metric metric-maintenance">
                <span className="network-overview-metric-icon" aria-hidden="true"><Wrench size={16} /></span>
                <div><strong>{loading || error ? "—" : stats.maintenance}</strong><span>Maintenance</span></div>
              </div>
            <div className="network-overview-metric metric-unknown">
              <span className="network-overview-metric-icon" aria-hidden="true"><WifiOff size={16} /></span>
              <div><strong>{loading || error ? "—" : stats.unknown}</strong><span>Unknown</span></div>
            </div>
            </div>
          </div>
          <div className="network-overview-image">
            <img src={networkOverviewImage} alt="Autonomous IoT inspection robot car in a smart factory" />
            <span className="network-overview-image-badge" aria-hidden="true"><ChartNoAxesCombined size={22} /></span>
          </div>
        </section>

        {error ? (
          <div className="mt-6 rounded-[14px] border border-red-200 bg-red-50 p-4 text-[12px] text-red-600">
            {error}
          </div>
        ) : null}

        <section className={preferences.compactMode ? "mt-4 min-w-0" : "mt-6 min-w-0"}>
          <BentoCard icon={RadioTower} title="Latest telemetry" description="Latest reading from your most recently reporting device." action={<StatusBadge tone={telemetry && !isStaleReading(telemetry.reading.receivedAt) ? "success" : telemetry ? "warning" : "neutral"}>{telemetry ? (isStaleReading(telemetry.reading.receivedAt) ? "Stale" : "Current") : "Waiting"}</StatusBadge>} className="min-w-0">
            <div className="pointer-events-none absolute -right-16 -top-20 h-44 w-44 rounded-full bg-[#dff6fa]/60 blur-2xl" />
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
                    <span className={`h-1.5 w-1.5 rounded-full ${isStaleReading(telemetry.reading.receivedAt) ? "bg-[#e99a12]" : "bg-[#16a57a]"}`} />
                    <span>{isStaleReading(telemetry.reading.receivedAt) ? "Stale reading" : "Updated"} {formatDisplayDate(telemetry.reading.receivedAt, preferences.dateFormat)}</span>
                  </div>
                </div>

                {dashboardMetricDefinitions.length === 0 ? (
                  <div className="rounded-[12px] border border-dashed border-[#dce8ee] bg-[#f8fbfd] p-6 text-center text-[11px] text-[#6b8290]">
                    <p className="font-medium text-[#102a3a]">
                      No measurement capabilities configured for this device.
                    </p>
                    <p className="mt-1 text-[10px]">
                      Edit the device to select its supported measurements.
                    </p>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-[repeat(auto-fit,minmax(180px,1fr))]">
                    {dashboardMetricDefinitions.map((definition) => (
                      <TelemetryMetric
                        key={definition.key}
                        icon={definition.icon}
                        label={definition.label}
                        value={getDisplayMetricValue(definition, telemetry.reading, preferences)}
                        unit={getDisplayMetricUnit(definition, preferences)}
                        tone={definition.tone === "cyan" ? "orange" : definition.tone}
                      />
                    ))}
                  </div>
                )}
              </div>
            )}
          </BentoCard>

        </section>

        <section className="mt-6 grid min-w-0 gap-5 lg:grid-cols-2">
          <div className="min-w-0"><RecentActivity devices={devices} alerts={alerts} /></div>

          <div className="min-w-0"><LatestAlerts
            alerts={alerts}
            loading={alertsLoading}
            error={alertsError}
            activeAlertCount={activeAlertCount}
          /></div>
        </section>
      </div>
    </div>
  );
}
