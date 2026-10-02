import { useNavigate } from "react-router-dom";
import {
  Bell,
  ChartNoAxesCombined,
  Cpu,
  Plus,
  TriangleAlert,
  Wifi,
  WifiOff,
} from "lucide-react";
import DashboardStatCard from "../../components/dashboard/DashboardStatCard.jsx";
import DeviceStatusCard from "../../components/dashboard/DeviceStatusCard.jsx";
import LatestAlerts from "../../components/dashboard/LatestAlerts.jsx";
import QuickActions from "../../components/dashboard/QuickActions.jsx";
import RecentActivity from "../../components/dashboard/RecentActivity.jsx";
import TemperatureChart from "../../components/charts/TemperatureChart.jsx";
import { temperatureSeries } from "../../data/dashboardMockData.js";
import { useAuth } from "../../hooks/useAuth.js";

export default function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();

  const firstName =
    user?.fullName?.trim()?.split(/\s+/)?.[0] || "Ethan";

  return (
    <div className="px-4 pb-9 pt-[26px] md:px-[30px]">
      <div className="mx-auto w-full max-w-[1144px]">
        <section className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-[26px] font-normal leading-tight text-[#102a3a]">
              Good morning, {firstName}
            </h1>
            <p className="mt-[5px] text-[12px] text-[#6b8290]">
              Here’s what’s happening across your IoT network today.
            </p>
          </div>

          <div className="flex gap-2.5">
            <button
              type="button"
              onClick={() => navigate("/devices")}
              className="flex h-10 items-center justify-center gap-2 rounded-[10px] border border-[#08a9c4] bg-[#08a9c4] px-4 text-[12px] font-medium text-white transition hover:bg-[#0799b2]"
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

        <section className="relative mt-6 min-h-[142px] overflow-hidden rounded-[18px] bg-gradient-to-r from-[#0a91ad] to-[#08b9cd]">
          <div className="relative z-10 max-w-[620px] px-6 py-[30px] text-white">
            <div className="flex items-center gap-2 text-[12px] font-semibold">
              <span className="h-2 w-2 rounded-full bg-[#89f0ce]" />
              Live network overview
            </div>

            <h2 className="mt-2.5 text-[18px] font-semibold">
              Your connected operations are running smoothly.
            </h2>

            <p className="mt-2 max-w-[570px] text-[11px] leading-5 text-white/85">
              46 of 48 devices are online. Network health improved 3.2% this
              week with no critical outages.
            </p>
          </div>

          <img
            src="/images/nexaiot-dashboard-iot.png"
            alt="Connected IoT monitoring illustration"
            className="absolute right-2 top-1/2 hidden h-[130px] w-[360px] -translate-y-1/2 rounded-[14px] object-cover object-center opacity-95 lg:block"
          />
        </section>

        <section className="mt-6 grid gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
          <DashboardStatCard
            icon={Cpu}
            value="48"
            label="Total devices"
            trend="+4 this month"
            tone="cyan"
          />

          <DashboardStatCard
            icon={Wifi}
            value="46"
            label="Online devices"
            trend="95.8% healthy"
            tone="green"
          />

          <DashboardStatCard
            icon={WifiOff}
            value="2"
            label="Offline devices"
            trend="Needs attention"
            tone="amber"
          />

          <DashboardStatCard
            icon={TriangleAlert}
            value="7"
            label="Active alerts"
            trend="2 critical"
            tone="red"
          />
        </section>

        <section className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,760px)_minmax(300px,368px)]">
          <div className="rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-[0_6px_20px_rgba(10,48,72,0.07)]">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <h2 className="text-[15px] font-semibold text-[#102a3a]">
                  Real-time temperature
                </h2>
                <p className="mt-1 text-[11px] text-[#6b8290]">
                  Average across 24 environmental sensors
                </p>
              </div>

              <div className="flex items-center gap-3 text-[10px]">
                <span className="inline-flex h-[22px] items-center gap-1.5 rounded-full bg-[#e8f7f2] px-2 text-[#16a57a]">
                  <span className="h-1.5 w-1.5 rounded-full bg-[#16a57a]" />
                  Live
                </span>
                <span className="text-[#8397a2]">Last 6 hours</span>
              </div>
            </div>

            <div className="mt-3 flex items-end gap-4">
              <div className="text-[30px] font-medium leading-none text-[#102a3a]">
                23.8°C
              </div>
              <div className="pb-1 text-[10px] font-medium text-[#16a57a]">
                ↓ 1.2°C vs previous period
              </div>
            </div>

            <div className="mt-2">
              <TemperatureChart data={temperatureSeries} />
            </div>
          </div>

          <QuickActions />
        </section>

        <section className="mt-6 grid items-start gap-4 lg:grid-cols-[360px_372px_minmax(0,380px)]">
          <DeviceStatusCard />
          <RecentActivity />
          <LatestAlerts />
        </section>
      </div>
    </div>
  );
}
