import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ChartNoAxesCombined,
  Cpu,
  Plus,
  TriangleAlert,
  Wifi,
  WifiOff,
} from "lucide-react";

import DashboardStatCard from "../../components/dashboard/DashboardStatCard.jsx";
import QuickActions from "../../components/dashboard/QuickActions.jsx";
import PageHero from "../../components/common/PageHero.jsx";
import { getDevices } from "../../api/devicesApi.js";
import { useAuth } from "../../hooks/useAuth.js";

export default function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

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
    async function loadDevices() {
      try {
        setLoading(true);
        setError("");

        const data = await getDevices();

        setDevices(Array.isArray(data) ? data : []);
      } catch (err) {
        setError(err.message || "Unable to load dashboard data.");
      } finally {
        setLoading(false);
      }
    }

    loadDevices();
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
            value={loading ? "—" : stats.total}
            label="Total devices"
            trend={
              loading
                ? "Checking..."
                : stats.total === 1
                  ? "1 registered device"
                  : `${stats.total} registered devices`
            }
            tone="cyan"
          />

          <DashboardStatCard
            icon={Wifi}
            value={loading ? "—" : stats.online}
            label="Online devices"
            trend={
              loading
                ? "Checking..."
                : `${stats.onlinePercentage}% online`
            }
            tone="green"
          />

          <DashboardStatCard
            icon={WifiOff}
            value={loading ? "—" : stats.offline}
            label="Offline devices"
            trend={
              loading
                ? "Checking..."
                : stats.offline > 0
                  ? "Needs attention"
                  : "No offline devices"
            }
            tone="amber"
          />

          <DashboardStatCard
            icon={TriangleAlert}
            value={loading ? "—" : stats.maintenance}
            label="Maintenance"
            trend={
              loading
                ? "Checking..."
                : stats.maintenance > 0
                  ? "Maintenance required"
                  : "No maintenance devices"
            }
            tone="red"
          />
        </section>

        <section className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,760px)_minmax(300px,368px)]">
          <div className="rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-[0_6px_20px_rgba(10,48,72,0.07)]">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-[15px] font-semibold text-[#102a3a]">
                  Real-time sensor monitoring
                </h2>

                <p className="mt-1 text-[11px] text-[#6b8290]">
                  Live sensor readings from your connected devices.
                </p>
              </div>

              <span className="rounded-full bg-[#edf4f6] px-2.5 py-1 text-[9px] font-medium text-[#6b8290]">
                Not connected
              </span>
            </div>

            <div className="mt-5 flex min-h-[180px] items-center justify-center rounded-[12px] border border-dashed border-[#dce8ee] bg-[#f8fbfd]">
              <div className="max-w-[300px] text-center">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-[12px] bg-[#eef9fb]">
                  <Wifi
                    size={23}
                    className="text-[#08a9c4]"
                  />
                </div>

                <p className="mt-3 text-[12px] font-semibold text-[#102a3a]">
                  Waiting for sensor data
                </p>

                <p className="mt-1 text-[10px] leading-4 text-[#6b8290]">
                  MQTT and live sensor readings will appear here after
                  hardware integration is completed.
                </p>
              </div>
            </div>
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

          <div className="rounded-[14px] border border-[#dce8ee] bg-white p-5">
            <h2 className="text-[14px] font-semibold text-[#102a3a]">
              Latest alerts
            </h2>

            <div className="mt-4 rounded-[10px] bg-[#f8fbfd] p-4">
              <p className="text-[11px] font-medium text-[#102a3a]">
                No alert data yet
              </p>

              <p className="mt-1 text-[10px] leading-4 text-[#6b8290]">
                Real device alerts will appear here after the alert system
                is implemented.
              </p>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}