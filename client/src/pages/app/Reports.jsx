import { useEffect, useMemo, useRef, useState } from "react";
import {
  BatteryMedium,
  Bell,
  CalendarDays,
  Cpu,
  Download,
  Droplets,
  FileText,
  Lightbulb,
  Plus,
  Signal,
  Thermometer,
  Zap,
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

import { getAlerts } from "../../api/alertsApi.js";
import {
  getDeviceReadings,
  getDevices,
} from "../../api/devicesApi.js";

const ranges = [
  { label: "1H", value: "1h" },
  { label: "6H", value: "6h" },
  { label: "24H", value: "24h" },
  { label: "7D", value: "7d" },
];

function formatValue(value, digits = 1) {
  if (!Number.isFinite(value)) return "—";

  return Number(value.toFixed(digits));
}

function formatAxisTime(value, range) {
  if (!value) return "";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";

  if (range === "7d") {
    return date.toLocaleString([], {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  return date.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatTooltipTime(value) {
  if (!value) return "";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";

  return date.toLocaleString([], {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function getMetricStats(readings, selector) {
  const values = readings
    .map(selector)
    .filter((value) => Number.isFinite(value));

  if (values.length === 0) {
    return { latest: null, minimum: null, maximum: null, average: null };
  }

  return {
    latest: values[values.length - 1],
    minimum: Math.min(...values),
    maximum: Math.max(...values),
    average: values.reduce((sum, value) => sum + value, 0) / values.length,
  };
}

function getTemperatureDomain(values) {
  const validValues = values.filter((value) => Number.isFinite(value));

  if (validValues.length === 0) return [24, 32];

  const minimum = Math.min(...validValues);
  const maximum = Math.max(...validValues);
  const range = maximum - minimum;

  if (range < 4) {
    const center = Math.round((minimum + maximum) / 2);
    return [center - 2, center + 2];
  }

  return [Math.floor(minimum - 0.5), Math.ceil(maximum + 0.5)];
}

function getSignalLabel(value) {
  if (!Number.isFinite(value)) return "—";
  if (value >= -55) return "Strong";
  if (value >= -70) return "Good";
  return "Weak";
}

function SummaryCard({ icon: Icon, title, latest, unit, tone, children }) {
  const tones = {
    orange: "bg-[#fff4e5] text-[#e79a23]",
    blue: "bg-[#eef4ff] text-[#4d7ee8]",
    green: "bg-[#eaf8f3] text-[#16a57a]",
    violet: "bg-[#f1efff] text-[#7664d8]",
  };

  return (
    <div className="rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)]">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <span className={`flex h-9 w-9 items-center justify-center rounded-[10px] ${tones[tone]}`}>
            <Icon size={17} />
          </span>
          <span className="text-[12px] font-semibold text-[#102a3a]">{title}</span>
        </div>
        <span className="text-[9px] text-[#8ba0ab]">Latest</span>
      </div>

      <div className="mt-4 text-[24px] font-semibold tracking-[-0.03em] text-[#102a3a]">
        {formatValue(latest)}
        {Number.isFinite(latest) ? <span className="ml-1 text-[11px] font-medium tracking-normal text-[#6b8290]">{unit}</span> : null}
      </div>

      {children}
    </div>
  );
}

function StatLine({ label, value, unit = "" }) {
  return (
    <div className="flex items-center justify-between gap-2 text-[9px]">
      <span className="text-[#8ba0ab]">{label}</span>
      <span className="font-semibold text-[#526b79]">{formatValue(value)}{Number.isFinite(value) ? unit : ""}</span>
    </div>
  );
}

export default function Reports() {
  const [devices, setDevices] = useState([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [selectedRange, setSelectedRange] = useState("1h");
  const [readings, setReadings] = useState([]);
  const [devicesLoading, setDevicesLoading] = useState(true);
  const [readingsLoading, setReadingsLoading] = useState(false);
  const [error, setError] = useState("");
  const [historyError, setHistoryError] = useState("");
  const [alerts, setAlerts] = useState([]);
  const [alertsLoading, setAlertsLoading] = useState(true);
  const historyInFlight = useRef(false);
  const historyRequestVersion = useRef(0);
  const alertsInFlight = useRef(false);

  useEffect(() => {
    let mounted = true;

    async function loadDevices() {
      try {
        setDevicesLoading(true);
        setError("");

        const data = await getDevices();
        const loadedDevices = Array.isArray(data) ? data : [];

        if (!mounted) return;

        setDevices(loadedDevices);
        setSelectedDeviceId((currentId) => (
          currentId && loadedDevices.some((device) => device.id === currentId)
            ? currentId
            : loadedDevices[0]?.id || ""
        ));
      } catch {
        if (mounted) {
          setError(err.message || "Unable to load devices.");
          setDevices([]);
          setSelectedDeviceId("");
        }
      } finally {
        if (mounted) setDevicesLoading(false);
      }
    }

    loadDevices();

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    const requestVersion = historyRequestVersion.current + 1;
    historyRequestVersion.current = requestVersion;

    async function loadHistory({ initial = false } = {}) {
      if (!selectedDeviceId || historyInFlight.current) return;

      historyInFlight.current = true;

      try {
        if (initial) setReadingsLoading(true);

        const data = await getDeviceReadings(selectedDeviceId, selectedRange);

        if (!mounted || requestVersion !== historyRequestVersion.current) return;

        setReadings(Array.isArray(data) ? data : []);
        setHistoryError("");
      } catch (err) {
        if (mounted && requestVersion === historyRequestVersion.current && initial) {
          setHistoryError(err.message || "Unable to load telemetry history.");
        }
      } finally {
        historyInFlight.current = false;

        if (mounted && initial) setReadingsLoading(false);
      }
    }

    if (!selectedDeviceId) {
      setReadings([]);
      setReadingsLoading(false);
      return () => {
        mounted = false;
      };
    }

    loadHistory({ initial: true });
    const intervalId = window.setInterval(() => loadHistory(), 5000);

    return () => {
      mounted = false;
      window.clearInterval(intervalId);
    };
  }, [selectedDeviceId, selectedRange]);

  useEffect(() => {
    let mounted = true;

    async function loadAlerts({ initial = false } = {}) {
      if (alertsInFlight.current) return;

      alertsInFlight.current = true;

      try {
        const data = await getAlerts();

        if (mounted) setAlerts(Array.isArray(data) ? data : []);
      } catch (err) {
        if (initial && mounted) setAlerts([]);
      } finally {
        alertsInFlight.current = false;
        if (initial && mounted) setAlertsLoading(false);
      }
    }

    loadAlerts({ initial: true });
    const intervalId = window.setInterval(() => loadAlerts(), 5000);

    return () => {
      mounted = false;
      window.clearInterval(intervalId);
    };
  }, []);

  const selectedDevice = devices.find((device) => device.id === selectedDeviceId) || null;
  const sortedReadings = useMemo(
    () => readings.slice().sort((first, second) => new Date(first.receivedAt) - new Date(second.receivedAt)),
    [readings],
  );

  const chartData = useMemo(
    () => sortedReadings.map((reading) => ({
      time: reading.receivedAt || "",
      temperature: reading.metrics?.temperature ?? null,
      humidity: reading.metrics?.humidity ?? null,
    })),
    [sortedReadings],
  );

  const temperatureStats = useMemo(
    () => getMetricStats(sortedReadings, (reading) => reading.metrics?.temperature),
    [sortedReadings],
  );
  const humidityStats = useMemo(
    () => getMetricStats(sortedReadings, (reading) => reading.metrics?.humidity),
    [sortedReadings],
  );
  const latestReading = sortedReadings[sortedReadings.length - 1] || null;
  const latestBattery = latestReading?.battery;
  const latestRssi = latestReading?.rssi;
  const temperatureDomain = getTemperatureDomain(
    chartData.map((point) => point.temperature),
  );
  const alertFrequency = useMemo(() => {
    const now = Date.now();
    const day = 24 * 60 * 60 * 1000;
    const buckets = Array.from({ length: 7 }, (_, index) => ({
      label: index === 6 ? "Now" : `D${index + 1}`,
      count: 0,
    }));

    alerts.forEach((alert) => {
      const createdAt = new Date(alert.createdAt).getTime();
      if (Number.isNaN(createdAt)) return;

      const daysAgo = Math.floor((now - createdAt) / day);
      if (daysAgo >= 0 && daysAgo < 7) {
        buckets[6 - daysAgo].count += 1;
      }
    });

    return buckets;
  }, [alerts]);
  const alertFrequencyMax = Math.max(...alertFrequency.map((bucket) => bucket.count), 1);
  const latestSignalLabel = getSignalLabel(latestRssi);

  return (
    <div className="w-full px-4 pb-10 pt-[26px] md:px-[30px]">
      <div className="mx-auto w-full max-w-[1144px]">
        <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-[26px] font-normal leading-tight text-[#102a3a]">Reports &amp; analytics</h1>
            <p className="mt-[5px] text-[12px] text-[#6b8290]">Analyze trends, compare performance, and export operational insights.</p>
          </div>

          <div className="flex flex-wrap gap-2.5">
            <button type="button" disabled title="CSV export will be available in a future release" className="inline-flex h-10 cursor-not-allowed items-center gap-2 rounded-[10px] border border-[#dce8ee] bg-white px-4 text-[11px] font-medium text-[#9aaeba] opacity-80"><Download size={14} /> Export CSV</button>
            <button type="button" disabled title="PDF export will be available in a future release" className="inline-flex h-10 cursor-not-allowed items-center gap-2 rounded-[10px] border border-[#dce8ee] bg-white px-4 text-[11px] font-medium text-[#9aaeba] opacity-80"><FileText size={14} /> Export PDF</button>
            <button type="button" disabled title="Report generation will be available in a future release" className="inline-flex h-10 cursor-not-allowed items-center gap-2 rounded-[10px] bg-[#08a9c4] px-4 text-[11px] font-medium text-white opacity-55"><Plus size={14} /> Generate report</button>
          </div>
        </section>

        {error ? <div className="mt-5 rounded-[12px] border border-red-200 bg-red-50 p-4 text-[11px] text-red-600">{error}</div> : null}

        {devicesLoading ? (
          <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {[1, 2, 3, 4].map((item) => <div key={item} className="h-[145px] animate-pulse rounded-[14px] border border-[#dce8ee] bg-white" />)}
          </div>
        ) : devices.length === 0 ? (
          <div className="mt-6 rounded-[14px] border border-dashed border-[#dce8ee] bg-white p-12 text-center text-[12px] text-[#6b8290]">No devices available</div>
        ) : (
          <>
            <section className="mt-6 rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-5">
              <div className="flex flex-col gap-3 xl:flex-row xl:items-center">
                <div className="flex items-center gap-2.5 xl:mr-2">
                  <span className="flex h-9 w-9 items-center justify-center rounded-[10px] bg-[#e8f8fb] text-[#08a9c4]"><CalendarDays size={17} /></span>
                  <span className="text-[10px] font-semibold text-[#526b79]">Reporting period</span>
                </div>
                <label htmlFor="report-device" className="sr-only">Device</label>
                <select id="report-device" value={selectedDeviceId} disabled={devicesLoading || devices.length === 0} onChange={(event) => setSelectedDeviceId(event.target.value)} className="h-10 min-w-0 flex-1 rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[10px] text-[#102a3a] outline-none transition focus:border-[#08a9c4] disabled:cursor-not-allowed disabled:bg-[#f8fbfd] xl:max-w-[300px]">
                  {devices.length === 0 ? <option value="">No devices available</option> : null}
                  {devices.map((device) => <option key={device.id} value={device.id}>{device.name || device.deviceKey}</option>)}
                </select>
                <div className="flex w-fit rounded-[9px] border border-[#e5edf1] bg-[#f8fbfd] p-1">
                  {ranges.map((range) => <button key={range.value} type="button" onClick={() => setSelectedRange(range.value)} className={`h-7 min-w-[42px] rounded-[7px] px-2 text-[9px] font-semibold transition ${selectedRange === range.value ? "bg-white text-[#08a9c4] shadow-sm" : "text-[#8ca0ab] hover:text-[#526b79]"}`}>{range.label}</button>)}
                </div>
                <span className="text-[9px] text-[#9aadb6] xl:ml-auto">Based on available range samples</span>
              </div>
            </section>

            {historyError ? <div className="mt-4 rounded-[12px] border border-red-200 bg-red-50 p-4 text-[11px] text-red-600">Unable to load telemetry history right now.</div> : null}

            {readingsLoading ? (
              <div className="mt-4 h-[145px] animate-pulse rounded-[14px] border border-[#dce8ee] bg-white" />
            ) : chartData.length === 0 ? (
              <div className="mt-4 rounded-[14px] border border-dashed border-[#dce8ee] bg-white p-12 text-center text-[12px] text-[#6b8290]">No telemetry available for this device in the selected range.</div>
            ) : (
              <>
                <section className="mt-4 grid gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
                  <SummaryCard icon={Thermometer} title="Temperature" latest={temperatureStats.latest} unit="°C" tone="orange">
                    <div className="mt-4 space-y-1.5"><StatLine label="Minimum" value={temperatureStats.minimum} unit="°C" /><StatLine label="Maximum" value={temperatureStats.maximum} unit="°C" /><StatLine label="Average" value={temperatureStats.average} unit="°C" /></div>
                  </SummaryCard>
                  <SummaryCard icon={Droplets} title="Humidity" latest={humidityStats.latest} unit="%" tone="blue">
                    <div className="mt-4 space-y-1.5"><StatLine label="Minimum" value={humidityStats.minimum} unit="%" /><StatLine label="Maximum" value={humidityStats.maximum} unit="%" /><StatLine label="Average" value={humidityStats.average} unit="%" /></div>
                  </SummaryCard>
                  <SummaryCard icon={BatteryMedium} title="Battery" latest={latestBattery} unit="%" tone="green">
                    <p className="mt-4 text-[9px] text-[#8ba0ab]">Latest reported battery level</p>
                  </SummaryCard>
                  <SummaryCard icon={Signal} title="Signal" latest={latestRssi} unit="dBm" tone="violet">
                    <p className="mt-4 text-[9px] font-medium text-[#6b8290]">{getSignalLabel(latestRssi)}</p>
                  </SummaryCard>
                </section>

                <div className="mt-4 grid items-start gap-4 lg:grid-cols-[minmax(0,1.65fr)_minmax(280px,0.75fr)]">
                <section className="rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-5">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <div><h2 className="text-[15px] font-semibold text-[#102a3a]">Temperature &amp; humidity history</h2><p className="mt-1 text-[10px] text-[#6b8290]">{selectedDevice?.name || "Selected device"} · {chartData.length} available samples</p></div>
                    <div className="flex flex-wrap gap-3 text-[9px] text-[#6b8290]"><span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-[#08a9c4]" />Temperature</span><span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-[#7568e8]" />Humidity</span></div>
                  </div>
                  <div className="mt-4 h-[300px] w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={chartData} margin={{ top: 12, right: 8, left: -8, bottom: 4 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#e5edf1" vertical={false} />
                        <XAxis dataKey="time" axisLine={false} tickLine={false} minTickGap={32} tickFormatter={(value) => formatAxisTime(value, selectedRange)} tick={{ fontSize: 9, fill: "#8ba0ab" }} />
                        <YAxis yAxisId="temperature" axisLine={false} tickLine={false} width={34} domain={temperatureDomain} allowDecimals={false} tick={{ fontSize: 9, fill: "#8ba0ab" }} />
                        <YAxis yAxisId="humidity" orientation="right" axisLine={false} tickLine={false} width={34} domain={[0, 100]} ticks={[0, 25, 50, 75, 100]} tick={{ fontSize: 9, fill: "#8ba0ab" }} />
                        <Tooltip labelFormatter={formatTooltipTime} contentStyle={{ borderRadius: 10, border: "1px solid #dce8ee", fontSize: 10 }} labelStyle={{ color: "#6b8290", fontSize: 9 }} />
                        <Line yAxisId="temperature" type="monotone" dataKey="temperature" name="Temperature (°C)" stroke="#08a9c4" strokeWidth={2.5} dot={false} connectNulls isAnimationActive={false} />
                        <Line yAxisId="humidity" type="monotone" dataKey="humidity" name="Humidity (%)" stroke="#7568e8" strokeWidth={2.5} dot={false} connectNulls isAnimationActive={false} />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                </section>

                <section className="rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-5">
                  <div className="flex items-center gap-2"><Cpu size={16} className="text-[#08a9c4]" /><h2 className="text-[15px] font-semibold text-[#102a3a]">Device health</h2></div>
                  <div className="mt-4 grid gap-3 text-[10px] sm:grid-cols-2 lg:grid-cols-4">
                    <div><p className="text-[#8ba0ab]">Device</p><p className="mt-1 truncate font-semibold text-[#102a3a]">{selectedDevice?.name || "—"}</p><p className="mt-1 truncate text-[9px] text-[#6b8290]">{selectedDevice?.deviceKey || "—"}</p></div>
                    <div><p className="text-[#8ba0ab]">Current status</p><p className="mt-1 flex items-center gap-1.5 font-semibold text-[#526b79]"><span className={`h-1.5 w-1.5 rounded-full ${selectedDevice?.status === "ONLINE" ? "bg-[#16a57a]" : "bg-[#e24e5a]"}`} />{selectedDevice?.status || "—"}</p></div>
                    <div><p className="text-[#8ba0ab]">Latest battery</p><p className="mt-1 font-semibold text-[#526b79]">{formatValue(latestBattery)}{Number.isFinite(latestBattery) ? " %" : ""}</p></div>
                    <div><p className="text-[#8ba0ab]">Latest RSSI / last telemetry</p><p className="mt-1 font-semibold text-[#526b79]">{formatValue(latestRssi)}{Number.isFinite(latestRssi) ? " dBm" : ""}</p><p className="mt-1 text-[9px] text-[#6b8290]">{formatTooltipTime(latestReading?.receivedAt) || "—"}</p></div>
                  </div>
                </section>
                </div>

                <div className="mt-4 grid items-start gap-4 lg:grid-cols-[1fr_1fr_0.86fr]">
                  <section className="rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-5">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <h2 className="text-[14px] font-semibold text-[#102a3a]">Energy usage</h2>
                        <p className="mt-1 text-[9px] text-[#8ba0ab]">Daily consumption in kWh</p>
                      </div>
                      <span className="text-[9px] text-[#9aadb6]">Not available</span>
                    </div>
                    <div className="mt-5 flex min-h-[138px] flex-col items-center justify-center rounded-[10px] border border-dashed border-[#e5edf1] bg-[#f8fbfd] text-center">
                      <span className="flex h-9 w-9 items-center justify-center rounded-[10px] bg-[#fff4e5] text-[#e79a23]"><Zap size={16} /></span>
                      <p className="mt-2 text-[11px] font-semibold text-[#526b79]">—</p>
                      <p className="mt-1 text-[9px] text-[#8ba0ab]">Energy readings are not provided by the current telemetry model.</p>
                    </div>
                  </section>

                  <section className="rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-5">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <h2 className="text-[14px] font-semibold text-[#102a3a]">Alert frequency</h2>
                        <p className="mt-1 text-[9px] text-[#8ba0ab]">Alerts from the last 7 days</p>
                      </div>
                      <span className="flex items-center gap-1.5 text-[9px] text-[#087f98]"><Bell size={12} /> Alert history</span>
                    </div>
                    <div className="mt-3 text-[22px] font-medium tracking-[-0.03em] text-[#102a3a]">{alertsLoading ? "—" : alerts.length} <span className="text-[10px] font-normal tracking-normal text-[#6b8290]">alerts</span></div>
                    <div className="mt-3 flex h-[92px] items-end justify-between gap-2 border-b border-[#edf2f4] px-1">
                      {alertFrequency.map((bucket) => (
                        <div key={bucket.label} className="flex h-full flex-1 flex-col items-center justify-end gap-1.5">
                          <div className="flex h-full w-full items-end justify-center rounded-t-[7px] bg-[#f8fbfd]">
                            <span className="w-3/5 rounded-t-[7px] bg-[#e99a12] transition-all" style={{ height: bucket.count > 0 ? `${Math.max(12, (bucket.count / alertFrequencyMax) * 100)}%` : "0%" }} />
                          </div>
                          <span className="text-[8px] text-[#9aadb6]">{bucket.label}</span>
                        </div>
                      ))}
                    </div>
                  </section>

                  <section className="rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-5">
                    <div className="flex items-center gap-2"><Lightbulb size={15} className="text-[#08a9c4]" /><h2 className="text-[14px] font-semibold text-[#102a3a]">Efficiency insights</h2></div>
                    <p className="mt-1 text-[9px] text-[#8ba0ab]">Automated findings from available telemetry</p>
                    <div className="mt-4 space-y-2">
                      <div className="rounded-[10px] bg-[#eaf8f3] p-3"><p className="text-[9px] font-medium text-[#16815f]">Battery telemetry</p><p className="mt-1 text-[8px] text-[#6b8290]">{Number.isFinite(latestBattery) ? `Latest reported level: ${formatValue(latestBattery)}%` : "No battery reading available"}</p></div>
                      <div className="rounded-[10px] bg-[#fff4df] p-3"><p className="text-[9px] font-medium text-[#a36a0a]">Signal quality</p><p className="mt-1 text-[8px] text-[#6b8290]">{Number.isFinite(latestRssi) ? `${latestSignalLabel} at ${formatValue(latestRssi, 0)} dBm` : "No RSSI reading available"}</p></div>
                      <div className="rounded-[10px] bg-[#eef4ff] p-3"><p className="text-[9px] font-medium text-[#416ac2]">Range coverage</p><p className="mt-1 text-[8px] text-[#6b8290]">{chartData.length} telemetry samples available</p></div>
                    </div>
                  </section>
                </div>

                <section className="mt-4 overflow-hidden rounded-[14px] border border-[#dce8ee] bg-white shadow-[0_5px_18px_rgba(10,48,72,0.05)]">
                  <div className="flex flex-col gap-3 border-b border-[#edf2f4] p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
                    <div><h2 className="text-[14px] font-semibold text-[#102a3a]">Generated reports</h2><p className="mt-1 text-[9px] text-[#8ba0ab]">Saved and scheduled report files</p></div>
                    <button type="button" disabled title="Report generation will be available in a future release" className="inline-flex h-9 cursor-not-allowed items-center justify-center gap-1.5 rounded-[9px] bg-[#08a9c4] px-3.5 text-[10px] font-medium text-white opacity-55"><Plus size={13} /> Generate report</button>
                  </div>
                  <div className="flex min-h-[96px] items-center justify-center bg-[#fbfdfe] px-4 text-center text-[10px] text-[#8ba0ab]">No generated reports yet. Report generation is not available in this release.</div>
                </section>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}
