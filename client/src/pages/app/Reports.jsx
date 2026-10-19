import { useEffect, useMemo, useRef, useState } from "react";
import {
  Bell,
  BatteryMedium,
  CalendarDays,
  Clock3,
  Cpu,
  Download,
  FileText,
  Lightbulb,
  Plus,
  Trash2,
  Wifi,
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

import { useAuth } from "../../hooks/useAuth.js";
import useReducedMotion from "../../hooks/useReducedMotion.js";
import { useNavigate, useOutletContext } from "react-router-dom";
import { useToast } from "../../hooks/useToast.js";
import { deleteGeneratedReport, generateReport, getGeneratedReports } from "../../api/reportsApi.js";
import {
  getDeviceEnergy,
  getDeviceReadings,
  getDevices,
} from "../../api/devicesApi.js";
import { normalizeCapabilityManifest } from "../../config/capabilityManifest.js";
import ErrorState from "../../components/ui/ErrorState.jsx";
import PageHeader from "../../components/ui/PageHeader.jsx";
import Select from "../../components/ui/Select.jsx";
import {
  DEVICE_METRIC_DEFINITIONS,
  getMetricDefinitionForCapability,
  getMetricDomain,
  getMetricValue,
} from "../../config/deviceMetricDefinitions.js";

const ranges = [
  { label: "1H", value: "1h" },
  { label: "6H", value: "6h" },
  { label: "24H", value: "24h" },
  { label: "7D", value: "7d" },
];

const rangeDurationsMs = {
  "1h": 60 * 60 * 1000,
  "6h": 6 * 60 * 60 * 1000,
  "24h": 24 * 60 * 60 * 1000,
  "7d": 7 * 24 * 60 * 60 * 1000,
};

const rangeTickIntervalsMs = {
  "1h": 15 * 60 * 1000,
  "6h": 60 * 60 * 1000,
  "24h": 6 * 60 * 60 * 1000,
  "7d": 24 * 60 * 60 * 1000,
};

function getRangeTicks(range, windowEnd) {
  if (!windowEnd) return undefined;

  const duration = rangeDurationsMs[range];
  const interval = rangeTickIntervalsMs[range];
  const start = windowEnd - duration;

  return Array.from({ length: Math.floor(duration / interval) + 1 }, (_, index) => (
    start + index * interval
  ));
}

function formatCoverageDuration(milliseconds) {
  const minutes = Math.round(milliseconds / (60 * 1000));
  if (minutes < 1) return "less than a minute";
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? "" : "s"}`;

  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? "" : "s"}`;

  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? "" : "s"}`;
}

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

function getSignalLabel(value) {
  if (!Number.isFinite(value)) return "—";
  if (value >= -55) return "Strong";
  if (value >= -70) return "Good";
  return "Weak";
}

function csvCell(value, numeric = false) {
  if (value === null || value === undefined) return "";
  const raw = String(value);
  const safe = !numeric && /^[=+\-@]/.test(raw) ? `'${raw}` : raw;
  return /[",\r\n]/.test(safe) ? `"${safe.replaceAll('"', '""')}"` : safe;
}

function buildTelemetryCsv(device, readings, definitions) {
  const activeDefinitions = definitions.filter((definition) => readings.some((reading) => Number.isFinite(getMetricValue(definition, reading))));
  const headers = ["timestamp", "deviceId", "deviceName", ...activeDefinitions.map((definition) => definition.key)];
  const rows = readings.map((reading) => [
    csvCell(reading.receivedAt),
    csvCell(device.id),
    csvCell(device.name || device.deviceKey || "Device"),
    ...activeDefinitions.map((definition) => {
      const value = getMetricValue(definition, reading);
      return Number.isFinite(value) ? csvCell(value, true) : "";
    }),
  ]);
  return [headers.map((header) => csvCell(header)).join(","), ...rows.map((row) => row.join(","))].join("\r\n");
}

function safeFilePart(value) {
  return String(value || "device").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 60) || "device";
}

function SummaryCard({ icon: Icon, title, latest, unit, tone, children }) {
  const tones = {
    cyan: "bg-[#e8f8fb] text-[#08a9c4]",
    orange: "bg-[#fff4e5] text-[#e79a23]",
    blue: "bg-[#eef4ff] text-[#4d7ee8]",
    green: "bg-[#eaf8f3] text-[#16a57a]",
    violet: "bg-[#f1efff] text-[#7664d8]",
  };

  return (
    <div className="flex h-full min-h-[205px] flex-col rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)]">
      <div className="flex min-h-[36px] items-start justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <span className={`flex h-9 w-9 items-center justify-center rounded-[10px] ${tones[tone]}`}>
            <Icon size={17} />
          </span>
          <span className="text-[12px] font-semibold text-[#102a3a]">{title}</span>
        </div>
        <span className="text-[9px] text-[#8ba0ab]">Latest</span>
      </div>

      <div className="mt-4 flex min-h-[42px] items-start text-[24px] font-semibold tracking-[-0.03em] text-[#102a3a]">
        {formatValue(latest)}
        {Number.isFinite(latest) ? <span className="ml-1 text-[11px] font-medium tracking-normal text-[#6b8290]">{unit}</span> : null}
      </div>

      <div className="mt-auto min-h-[58px] pt-4">
        {children}
      </div>
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

function EnergyUsageCard({ energy, loading, error, animate }) {
  const trend = Array.isArray(energy?.trend) ? energy.trend : [];
  const unavailable = !loading && (!energy?.available || error);

  return (
    <section className="rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[14px] font-semibold text-[#102a3a]">Energy usage</h2>
          <p className="mt-1 text-[9px] text-[#8ba0ab]">Real persisted electrical telemetry</p>
        </div>
        <span className="text-[9px] text-[#9aadb6]">{loading ? "Loading..." : energy?.source || "Unavailable"}</span>
      </div>
      {loading ? <div className="mt-5 h-[138px] animate-pulse rounded-[10px] bg-[#f8fbfd]" /> : null}
      {unavailable ? <div className="mt-5 flex min-h-[138px] flex-col items-center justify-center rounded-[10px] border border-dashed border-[#e5edf1] bg-[#f8fbfd] text-center"><span className="flex h-9 w-9 items-center justify-center rounded-[10px] bg-[#fff4e5] text-[#e79a23]"><Zap size={16} /></span><p className="mt-2 text-[11px] font-semibold text-[#526b79]">Energy data unavailable</p><p className="mt-1 text-[9px] text-[#8ba0ab]">{error || "No valid energy telemetry is available for this period."}</p></div> : null}
      {!loading && !unavailable ? <>
        <div className="mt-4 grid grid-cols-2 gap-2">
          {[['Total energy', energy.totalEnergyKWh, 'kWh'], ['Average power', energy.averagePowerW, 'W'], ['Peak power', energy.peakPowerW, 'W'], [energy.coverageBasis === "METER_SPAN" ? "Meter span" : "Data coverage", Number.isFinite(energy.coverage) ? energy.coverage * 100 : null, '%']].map(([label, value, unit]) => <div key={label} className="rounded-[10px] bg-[#f8fbfd] p-2.5"><p className="text-[8px] text-[#8ba0ab]">{label}</p><p className="mt-1 text-[15px] font-semibold text-[#102a3a]">{formatValue(value, 2)}{Number.isFinite(value) ? ` ${unit}` : ""}</p></div>)}
        </div>
        <div className="mt-4 h-[130px] w-full">
          {trend.length === 0 ? <div className="flex h-full items-center justify-center rounded-[10px] border border-dashed border-[#e5edf1] text-[9px] text-[#8ba0ab]">No energy trend points for this period.</div> : <ResponsiveContainer width="100%" height="100%"><LineChart data={trend} margin={{ top: 8, right: 4, left: -24, bottom: 0 }}><CartesianGrid strokeDasharray="3 3" stroke="#e5edf1" vertical={false} /><XAxis dataKey="bucketStart" hide /><YAxis axisLine={false} tickLine={false} tick={{ fontSize: 8, fill: "#8ba0ab" }} /><Tooltip labelFormatter={formatTooltipTime} formatter={(value) => [`${formatValue(value, 2)} ${energy.trendUnit || "W"}`, energy.trendUnit === "kWh" ? "Energy" : "Power"]} contentStyle={{ borderRadius: 8, border: "1px solid #dce8ee", fontSize: 9 }} /><Line type="monotone" dataKey="value" stroke="#e99a12" strokeWidth={2} dot={false} isAnimationActive={animate} /></LineChart></ResponsiveContainer>}
        </div>
      </> : null}
    </section>
  );
}

export default function Reports() {
  const navigate = useNavigate();
  const { alerts = [], alertsLoading = true } = useOutletContext() || {};
  const { showToast } = useToast();
  const { user } = useAuth();
  const prefersReducedMotion = useReducedMotion();
  const chartAnimationsEnabled = user?.liveChartAnimations !== false && !prefersReducedMotion;
  const [devices, setDevices] = useState([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [selectedRange, setSelectedRange] = useState("1h");
  const [readings, setReadings] = useState([]);
  const [chartWindowEnd, setChartWindowEnd] = useState(0);
  const [devicesLoading, setDevicesLoading] = useState(true);
  const [readingsLoading, setReadingsLoading] = useState(false);
  const [error, setError] = useState("");
  const [historyError, setHistoryError] = useState("");
  const [energyResult, setEnergyResult] = useState({ key: "", data: null, error: "" });
  const [generatedReports, setGeneratedReports] = useState([]);
  const [generatingReport, setGeneratingReport] = useState(false);
  const [reportError, setReportError] = useState("");
  const [exportingCsv, setExportingCsv] = useState(false);
  const [exportingPdf, setExportingPdf] = useState(false);
  const [selectedMetric, setSelectedMetric] = useState("");
  const [alertNow] = useState(() => Date.now());
  const historyRequestVersion = useRef(0);

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
      } catch (err) {
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
    getGeneratedReports().then((data) => mounted && setGeneratedReports(Array.isArray(data) ? data : [])).catch((err) => mounted && setReportError(err.message || "Unable to load generated reports."));
    return () => { mounted = false; };
  }, []);

  async function handleGenerateReport() {
    if (!selectedDeviceId || generatingReport) return;
    try {
      setGeneratingReport(true); setReportError("");
      const report = await generateReport({ deviceId: selectedDeviceId, range: selectedRange });
      setGeneratedReports((current) => [report, ...current]);
      showToast("Report generated successfully.");
    } catch (err) { setReportError(err.message || "Unable to generate report."); }
    finally { setGeneratingReport(false); }
  }

  async function handleDeleteReport(report) {
    if (!window.confirm(`Delete report for “${report.deviceName}”?`)) return;
    try { await deleteGeneratedReport(report.id); setGeneratedReports((current) => current.filter((item) => item.id !== report.id)); showToast("Report deleted."); }
    catch (err) { setReportError(err.message || "Unable to delete report."); }
  }

  async function handleExportCsv() {
    if (!selectedDevice || !selectedDeviceId || exportingCsv || readingsLoading) return;
    try {
      setExportingCsv(true); setReportError("");
      const rawReadings = await getDeviceReadings(selectedDeviceId, selectedRange, true);
      if (!Array.isArray(rawReadings) || rawReadings.length === 0) {
        setReportError("No telemetry data is available for this range.");
        return;
      }
      const definitions = supportedCapabilities.map(getMetricDefinitionForCapability).filter(Boolean);
      const csv = buildTelemetryCsv(selectedDevice, rawReadings, definitions);
      const blob = new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" });
      const url = URL.createObjectURL(blob); const link = document.createElement("a");
      link.href = url; link.download = `nexaiot-${safeFilePart(selectedDevice.name || selectedDevice.deviceKey)}-${selectedRange}-${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
      showToast("CSV exported successfully.");
    } catch (err) { setReportError(err.message || "Unable to export CSV."); }
    finally { setExportingCsv(false); }
  }

  async function handleExportPdf() {
    if (!selectedDeviceId || exportingPdf) return;
    try {
      setExportingPdf(true); setReportError("");
      const report = await generateReport({ deviceId: selectedDeviceId, range: selectedRange });
      setGeneratedReports((current) => [report, ...current]);
      const { downloadReportPdf, reportFilename } = await import("../../utils/reportPdf.js");
      downloadReportPdf(report, reportFilename(report));
      showToast("PDF exported successfully.");
    } catch (err) { setReportError(err.message || "Unable to export PDF."); }
    finally { setExportingPdf(false); }
  }

  useEffect(() => {
    let mounted = true;
    const requestVersion = historyRequestVersion.current + 1;
    historyRequestVersion.current = requestVersion;

    async function loadHistory() {
      if (!selectedDeviceId) return;

      try {
        setChartWindowEnd(Date.now());
        setReadingsLoading(true);

        const data = await getDeviceReadings(selectedDeviceId, selectedRange);

        if (!mounted || requestVersion !== historyRequestVersion.current) return;

        setReadings(Array.isArray(data) ? data : []);
        setHistoryError("");
      } catch (err) {
        if (mounted && requestVersion === historyRequestVersion.current) {
          setHistoryError(err.message || "Unable to load telemetry history.");
        }
      } finally {
        if (mounted && requestVersion === historyRequestVersion.current) setReadingsLoading(false);
      }
    }

    if (!selectedDeviceId) {
      return () => {
        mounted = false;
      };
    }

    loadHistory();

    return () => {
      mounted = false;
    };
  }, [selectedDeviceId, selectedRange]);

  const selectedDevice = devices.find((device) => device.id === selectedDeviceId) || null;
  const configuredCapabilities = normalizeCapabilityManifest(selectedDevice);
  const supportedCapabilities = configuredCapabilities
    .filter((capability) => capability.category !== "CONTROL");
  const metricDefinitions = supportedCapabilities
    .filter((capability) => capability.key !== "energyConsumption")
    .map(getMetricDefinitionForCapability)
    .filter(Boolean);
  const hasEnergyCapability = (key) => supportedCapabilities.some(
    (capability) => (capability.key || capability.semanticType) === key,
  );
  const energyAnalyticsConfigured = hasEnergyCapability("energyConsumption")
    || hasEnergyCapability("power")
    || (hasEnergyCapability("voltage") && hasEnergyCapability("current"));
  const energyRequestKey = selectedDeviceId && energyAnalyticsConfigured
    ? `${selectedDeviceId}:${selectedRange}`
    : "";

  useEffect(() => {
    let mounted = true;
    if (!selectedDeviceId || !energyAnalyticsConfigured) {
      return () => { mounted = false; };
    }

    getDeviceEnergy(selectedDeviceId, selectedRange)
      .then((data) => mounted && setEnergyResult({ key: energyRequestKey, data, error: "" }))
      .catch((err) => mounted && setEnergyResult({ key: energyRequestKey, data: null, error: err.message || "Unable to load energy data." }));

    return () => { mounted = false; };
  }, [selectedDeviceId, selectedRange, energyAnalyticsConfigured, energyRequestKey]);

  const energy = energyResult.key === energyRequestKey ? energyResult.data : null;
  const energyError = energyResult.key === energyRequestKey ? energyResult.error : "";
  const energyLoading = Boolean(energyRequestKey) && energyResult.key !== energyRequestKey;
  const effectiveSelectedMetric = supportedCapabilities.some(
    (capability) => capability.key === selectedMetric,
  )
    ? selectedMetric
    : supportedCapabilities[0]?.key || "";
  const selectedMetricDefinition =
    getMetricDefinitionForCapability(
      supportedCapabilities.find((capability) => capability.key === effectiveSelectedMetric),
    );
  const sortedReadings = useMemo(
    () => readings.slice().sort((first, second) => new Date(first.receivedAt) - new Date(second.receivedAt)),
    [readings],
  );

  const chartData = selectedMetricDefinition
    ? sortedReadings
        .map((reading) => {
          const time = new Date(reading.receivedAt).getTime();
          return {
            time,
            value: getMetricValue(selectedMetricDefinition, reading),
          };
        })
        .filter((point) => Number.isFinite(point.time) && Number.isFinite(point.value))
    : [];
  const latestReading = sortedReadings[sortedReadings.length - 1] || null;
  const latestBattery = getMetricValue(
    DEVICE_METRIC_DEFINITIONS.BATTERY,
    latestReading,
  );
  const latestRssi = getMetricValue(
    DEVICE_METRIC_DEFINITIONS.RSSI,
    latestReading,
  );
  const metricDomain = getMetricDomain(
    selectedMetricDefinition,
    chartData.map((point) => point.value),
  );
  const chartDomain = [
    chartWindowEnd ? chartWindowEnd - rangeDurationsMs[selectedRange] : "dataMin",
    chartWindowEnd || "dataMax",
  ];
  const chartTicks = getRangeTicks(selectedRange, chartWindowEnd);
  const earliestReadingTime = readings
    .map((reading) => new Date(reading.receivedAt).getTime())
    .filter(Number.isFinite)
    .reduce((earliest, timestamp) => Math.min(earliest, timestamp), Number.POSITIVE_INFINITY);
  const chartCoverageMessage = chartWindowEnd
    && Number.isFinite(earliestReadingTime)
    && chartWindowEnd - earliestReadingTime < rangeDurationsMs[selectedRange] - 60 * 1000
    ? `Historical data available for the last ${formatCoverageDuration(chartWindowEnd - earliestReadingTime)}.`
    : "";
  const hasConfiguredCapability = (key) => configuredCapabilities.some(
    (capability) => capability.key === key,
  );
  const alertFrequency = useMemo(() => {
    const now = alertNow;
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
  }, [alerts, alertNow]);
  const alertFrequencyMax = Math.max(...alertFrequency.map((bucket) => bucket.count), 1);
  const latestSignalLabel = getSignalLabel(latestRssi);

  return (
    <div className="w-full px-4 pb-10 pt-7 md:px-8 lg:px-[30px]">
      <div className="mx-auto w-full max-w-[1240px]">
        <PageHeader
          tone="analytics"
          title="Reports & analytics"
          description="Analyze trends, compare performance, and export operational insights."
          actions={(
            <div className="flex flex-wrap gap-2.5">
            <button type="button" disabled={!selectedDeviceId || readingsLoading || exportingCsv} onClick={handleExportCsv} className="inline-flex h-10 items-center gap-2 rounded-[10px] border border-[#dce8ee] bg-white px-4 text-[11px] font-medium text-[#526b79] transition hover:border-[#08a9c4] hover:text-[#087f98] disabled:cursor-not-allowed disabled:opacity-55"><Download size={14} /> {exportingCsv ? "Exporting..." : "Export CSV"}</button>
            <button type="button" disabled={!selectedDeviceId || exportingPdf} onClick={handleExportPdf} className="inline-flex h-10 items-center gap-2 rounded-[10px] border border-[#dce8ee] bg-white px-4 text-[11px] font-medium text-[#526b79] transition hover:border-[#08a9c4] hover:text-[#087f98] disabled:cursor-not-allowed disabled:opacity-55"><FileText size={14} /> {exportingPdf ? "Exporting..." : "Export PDF"}</button>
            <button type="button" disabled={!selectedDeviceId || generatingReport} onClick={handleGenerateReport} className="inline-flex h-10 items-center gap-2 rounded-[10px] bg-[#08a9c4] px-4 text-[11px] font-medium text-white disabled:cursor-not-allowed disabled:opacity-55"><Plus size={14} /> {generatingReport ? "Generating..." : "Generate report"}</button>
            </div>
          )}
        />

        {error ? <ErrorState className="mt-5" message={error} /> : null}
        {reportError ? <ErrorState className="mt-5" message={reportError} /> : null}

        {devicesLoading ? (
          <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {[1, 2, 3, 4].map((item) => <div key={item} className="h-[145px] animate-pulse rounded-[14px] border border-[#dce8ee] bg-white" />)}
          </div>
        ) : devices.length === 0 ? (
          <div className="mt-6 rounded-[14px] border border-dashed border-[#dce8ee] bg-white p-12 text-center text-[12px] text-[#6b8290]">No devices available</div>
        ) : (
          <>
            <section className="relative z-20 mt-6 overflow-visible rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-5">
              <div className="flex flex-col gap-3 xl:flex-row xl:items-center">
                <div className="flex items-center gap-2.5 xl:mr-2">
                  <span className="flex h-9 w-9 items-center justify-center rounded-[10px] bg-[#e8f8fb] text-[#08a9c4]"><CalendarDays size={17} /></span>
                  <span className="text-[10px] font-semibold text-[#526b79]">Reporting period</span>
                </div>
                <Select id="report-device" value={selectedDeviceId} disabled={devicesLoading || devices.length === 0} onChange={setSelectedDeviceId} aria-label="Device" className="min-w-0 flex-1 xl:max-w-[300px]" buttonClassName="h-10 text-[10px]" options={[...(devices.length === 0 ? [{ value: "", label: "No devices available" }] : []), ...devices.map((device) => ({ value: device.id, label: device.name || device.deviceKey }))]} />
                <div className="flex w-fit rounded-[9px] border border-[#e5edf1] bg-[#f8fbfd] p-1">
                  {ranges.map((range) => <button key={range.value} type="button" onClick={() => setSelectedRange(range.value)} className={`h-7 min-w-[42px] rounded-[7px] px-2 text-[9px] font-semibold transition ${selectedRange === range.value ? "bg-white text-[#08a9c4] shadow-sm" : "text-[#8ca0ab] hover:text-[#526b79]"}`}>{range.label}</button>)}
                </div>
                <span className="text-[9px] text-[#9aadb6] xl:ml-auto">Based on available range samples</span>
              </div>
            </section>

            {historyError ? <div className="mt-4 rounded-[12px] border border-red-200 bg-red-50 p-4 text-[11px] text-red-600">Unable to load telemetry history right now.</div> : null}

            {readingsLoading ? (
              <div className="mt-4 h-[145px] animate-pulse rounded-[14px] border border-[#dce8ee] bg-white" />
            ) : supportedCapabilities.length === 0 ? (
              <div className="mt-4 rounded-[14px] border border-dashed border-[#dce8ee] bg-white p-12 text-center text-[12px] text-[#6b8290]">
                <p className="font-medium text-[#102a3a]">No measurement capabilities configured for this device.</p>
                <p className="mt-1 text-[10px]">Edit the device to select its supported measurements.</p>
              </div>
            ) : chartData.length === 0 ? (
              <>
                <div className="mt-4 rounded-[14px] border border-dashed border-[#dce8ee] bg-white p-12 text-center text-[12px] text-[#6b8290]">
                  <p>No {selectedMetricDefinition?.label || "telemetry"} data available for this time range.</p>
                  {metricDefinitions.length > 0 ? (
                    <label className="mx-auto mt-4 flex h-9 w-fit items-center gap-2 rounded-[9px] border border-[#dce8ee] bg-white px-2.5 text-[9px] text-[#6b8290]">
                      <span>Metric</span>
                      <Select value={effectiveSelectedMetric} onChange={setSelectedMetric} aria-label="Metric" className="max-w-[140px]" buttonClassName="min-h-8 rounded-[8px] bg-transparent px-2 text-[9px] font-medium" options={metricDefinitions.map((definition) => ({ value: definition.key, label: definition.label }))} />
                    </label>
                  ) : null}
                </div>
                {energyAnalyticsConfigured ? <div className="mt-4"><EnergyUsageCard energy={energy} loading={energyLoading} error={energyError} animate={chartAnimationsEnabled} /></div> : null}
              </>
            ) : (
              <>
                <section className="mt-4 grid grid-cols-1 items-stretch gap-3.5 sm:grid-cols-[repeat(auto-fit,minmax(220px,1fr))]">
                  {metricDefinitions.map((definition) => {
                    const stats = getMetricStats(
                      sortedReadings,
                      (reading) => getMetricValue(definition, reading),
                    );

                    return (
                      <SummaryCard
                        key={definition.key}
                        icon={definition.icon}
                        title={definition.label}
                        latest={stats.latest}
                        unit={definition.unit}
                        tone={definition.tone}
                      >
                        {definition.source === "metrics" ? (
                          <div className="space-y-1.5">
                            <StatLine label="Minimum" value={stats.minimum} unit={definition.unit} />
                            <StatLine label="Maximum" value={stats.maximum} unit={definition.unit} />
                            <StatLine label="Average" value={stats.average} unit={definition.unit} />
                          </div>
                        ) : (
                          <p className="text-[9px] text-[#8ba0ab]">
                            Latest reported {definition.label.toLowerCase()}
                          </p>
                        )}
                      </SummaryCard>
                    );
                  })}
                </section>

                <div className="mt-4 grid items-start gap-4 lg:grid-cols-[minmax(0,1.65fr)_minmax(280px,0.75fr)]">
                <section className="rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-5">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <h2 className="topic-heading topic-telemetry text-[15px] font-semibold text-[#102a3a]">{selectedMetricDefinition?.label || "Telemetry"} history</h2>
                      <p className="mt-1 text-[10px] text-[#6b8290]">{selectedDevice?.name || "Selected device"} · {chartData.length} available samples</p>
                    </div>
                    <label className="flex h-9 items-center gap-2 rounded-[9px] border border-[#dce8ee] bg-white px-2.5 text-[9px] text-[#6b8290]">
                      <span>Metric</span>
                      <Select value={effectiveSelectedMetric} onChange={setSelectedMetric} aria-label="Metric" className="max-w-[140px]" buttonClassName="min-h-8 rounded-[8px] bg-transparent px-2 text-[9px] font-medium" options={metricDefinitions.map((definition) => ({ value: definition.key, label: definition.label }))} />
                    </label>
                  </div>
                  <div className="mt-4 h-[300px] w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={chartData} margin={{ top: 12, right: 8, left: -8, bottom: 4 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#e5edf1" vertical={false} />
                        <XAxis type="number" dataKey="time" scale="time" domain={chartDomain} ticks={chartTicks} interval="preserveStartEnd" axisLine={false} tickLine={false} minTickGap={32} tickFormatter={(value) => formatAxisTime(value, selectedRange)} tick={{ fontSize: 9, fill: "#8ba0ab" }} />
                        <YAxis axisLine={false} tickLine={false} width={34} domain={metricDomain} allowDecimals={false} tick={{ fontSize: 9, fill: "#8ba0ab" }} />
                        <Tooltip labelFormatter={formatTooltipTime} formatter={(value) => [`${value} ${selectedMetricDefinition.unit}`, selectedMetricDefinition.label]} contentStyle={{ borderRadius: 10, border: "1px solid #dce8ee", fontSize: 10 }} labelStyle={{ color: "#6b8290", fontSize: 9 }} />
                        <Line type="monotone" dataKey="value" name={`${selectedMetricDefinition.label} (${selectedMetricDefinition.unit})`} stroke={selectedMetricDefinition.color} strokeWidth={2.5} dot={false} connectNulls isAnimationActive={chartAnimationsEnabled} />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                  {chartCoverageMessage ? <p className="mt-2 text-[9px] text-[#9aadb6]">{chartCoverageMessage}</p> : null}
                </section>

                <section className="rounded-[16px] border border-[#dce8ee] bg-white p-4 shadow-[0_8px_24px_rgba(10,48,72,0.06)] sm:p-5">
                  <div className="flex items-center gap-2.5">
                    <span className="flex h-9 w-9 items-center justify-center rounded-[10px] bg-[#e8f8fb] text-[#08a9c4]">
                      <Cpu size={17} />
                    </span>
                    <div>
                      <h2 className="topic-heading topic-health text-[15px] font-semibold text-[#102a3a]">Device health</h2>
                      <p className="mt-0.5 text-[9px] text-[#8ba0ab]">Current device status and telemetry health</p>
                    </div>
                  </div>

                  <div className="mt-5 flex items-start justify-between gap-3 rounded-[12px] border border-[#e7eff3] bg-[#f8fbfd] p-3.5">
                    <div className="flex min-w-0 items-center gap-3">
                      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] bg-white text-[#08a9c4] shadow-[0_3px_10px_rgba(16,42,58,0.05)]">
                        <Cpu size={18} />
                      </span>
                      <div className="min-w-0">
                        <p className="truncate text-[12px] font-semibold text-[#102a3a]">{selectedDevice?.name || "—"}</p>
                        <p className="mt-1 truncate text-[9px] text-[#6b8290]">{selectedDevice?.deviceKey || "—"}</p>
                      </div>
                    </div>
                    <span className={`flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-[8px] font-semibold ${selectedDevice?.status === "ONLINE" ? "bg-[#eaf8f3] text-[#16815f]" : "bg-[#fdecee] text-[#d83f4d]"}`}>
                      <span className={`h-1.5 w-1.5 rounded-full ${selectedDevice?.status === "ONLINE" ? "bg-[#16a57a]" : "bg-[#e24e5a]"}`} />
                      {selectedDevice?.status || "—"}
                    </span>
                  </div>

                  {hasConfiguredCapability("battery") || hasConfiguredCapability("rssi") ? (
                    <div className="mt-3 grid gap-3 sm:grid-cols-2">
                      {hasConfiguredCapability("battery") ? (
                        <div className="rounded-[12px] border border-[#e7eff3] bg-white p-3.5">
                          <div className="flex items-center gap-2 text-[#16a57a]"><BatteryMedium size={15} /><span className="text-[9px] font-semibold uppercase tracking-[0.06em] text-[#6b8290]">Battery</span></div>
                          <p className="mt-3 text-[21px] font-semibold tracking-[-0.03em] text-[#102a3a]">{formatValue(latestBattery)}{Number.isFinite(latestBattery) ? "%" : ""}</p>
                          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-[#eaf3f0]"><span className="block h-full rounded-full bg-[#16a57a]" style={{ width: Number.isFinite(latestBattery) ? `${Math.min(100, Math.max(0, latestBattery))}%` : "0%" }} /></div>
                          <p className="mt-2 text-[9px] text-[#8ba0ab]">Latest reported battery level</p>
                        </div>
                      ) : null}
                      {hasConfiguredCapability("rssi") ? (
                        <div className="rounded-[12px] border border-[#e7eff3] bg-white p-3.5">
                          <div className="flex items-center gap-2 text-[#4d7ee8]"><Wifi size={15} /><span className="text-[9px] font-semibold uppercase tracking-[0.06em] text-[#6b8290]">Signal</span></div>
                          <p className="mt-3 text-[21px] font-semibold tracking-[-0.03em] text-[#102a3a]">{formatValue(latestRssi, 0)}{Number.isFinite(latestRssi) ? " dBm" : ""}</p>
                          <p className="mt-2 text-[9px] font-medium text-[#6b8290]">{getSignalLabel(latestRssi)}</p>
                        </div>
                      ) : null}
                    </div>
                  ) : (
                    <div className="mt-3 rounded-[12px] border border-dashed border-[#dce8ee] bg-[#f8fbfd] p-4 text-center text-[10px] text-[#6b8290]">No device health metrics configured.</div>
                  )}

                  <div className="mt-3 flex items-start gap-2.5 rounded-[11px] border border-[#dce8ee] bg-[#eef9fb] px-3.5 py-3">
                    <Clock3 size={15} className="mt-0.5 shrink-0 text-[#08a9c4]" />
                    <div><p className="text-[9px] font-semibold text-[#526b79]">Last telemetry</p><p className="mt-1 text-[10px] font-medium text-[#102a3a]">{formatTooltipTime(latestReading?.receivedAt) || "—"}</p></div>
                  </div>
                </section>
                </div>

                <div className={`mt-4 grid items-start gap-4 ${energyAnalyticsConfigured ? "lg:grid-cols-[1fr_1fr_0.86fr]" : "lg:grid-cols-[1fr_0.86fr]"}`}>
                  {energyAnalyticsConfigured ? (
                  <EnergyUsageCard energy={energy} loading={energyLoading} error={energyError} animate={chartAnimationsEnabled} />
                  ) : null}

                  <section className="rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-5">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <h2 className="topic-heading topic-warning text-[14px] font-semibold text-[#102a3a]">Alert frequency</h2>
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
                    <div className="flex items-center gap-2"><Lightbulb size={15} className="text-[var(--topic-analytics)]" /><h2 className="topic-heading topic-analytics text-[14px] font-semibold text-[#102a3a]">Efficiency insights</h2></div>
                    <p className="mt-1 text-[9px] text-[#8ba0ab]">Automated findings from available telemetry</p>
                    <div className="mt-4 space-y-2">
                      {hasConfiguredCapability("battery") ? <div className="rounded-[10px] bg-[#eaf8f3] p-3"><p className="text-[9px] font-medium text-[#16815f]">Battery telemetry</p><p className="mt-1 text-[8px] text-[#6b8290]">{Number.isFinite(latestBattery) ? `Latest reported level: ${formatValue(latestBattery)}%` : "No battery reading available"}</p></div> : null}
                      {hasConfiguredCapability("rssi") ? <div className="rounded-[10px] bg-[#fff4df] p-3"><p className="text-[9px] font-medium text-[#a36a0a]">Signal quality</p><p className="mt-1 text-[8px] text-[#6b8290]">{Number.isFinite(latestRssi) ? `${latestSignalLabel} at ${formatValue(latestRssi, 0)} dBm` : "No RSSI reading available"}</p></div> : null}
                      <div className="rounded-[10px] bg-[#eef4ff] p-3"><p className="text-[9px] font-medium text-[#416ac2]">Range coverage</p><p className="mt-1 text-[8px] text-[#6b8290]">{chartData.length} telemetry samples available</p></div>
                    </div>
                  </section>
                </div>

                <section className="mt-4 overflow-hidden rounded-[14px] border border-[#dce8ee] bg-white shadow-[0_5px_18px_rgba(10,48,72,0.05)]">
                  <div className="flex flex-col gap-3 border-b border-[#edf2f4] p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
                    <div><h2 className="topic-heading topic-analytics text-[14px] font-semibold text-[#102a3a]">Generated reports</h2><p className="mt-1 text-[9px] text-[#8ba0ab]">Saved and scheduled report files</p></div>
                    <button type="button" disabled={!selectedDeviceId || generatingReport} onClick={handleGenerateReport} className="inline-flex h-9 items-center justify-center gap-1.5 rounded-[9px] bg-[#08a9c4] px-3.5 text-[10px] font-medium text-white disabled:cursor-not-allowed disabled:opacity-55"><Plus size={13} /> Generate report</button>
                  </div>
                  {generatedReports.length === 0 ? <div className="flex min-h-[96px] items-center justify-center bg-[#fbfdfe] px-4 text-center text-[10px] text-[#8ba0ab]">No generated reports yet.</div> : <div className="max-h-[360px] overflow-x-hidden overflow-y-auto sm:max-h-[420px]"><div className="divide-y divide-[#edf2f4]">{generatedReports.map((report) => <div key={report.id} className="flex flex-wrap items-center justify-between gap-3 p-4"><div className="min-w-0 flex-1"><p className="break-words text-[11px] font-semibold text-[#102a3a]">{report.deviceName} · {report.range.toUpperCase()}</p><p className="mt-1 break-words text-[9px] text-[#8ba0ab]">Generated {formatTooltipTime(report.generatedAt)} · {report.alertCount} alerts</p></div><div className="flex shrink-0 items-center gap-2"><button type="button" onClick={() => navigate(`/reports/${report.id}`)} className="rounded-[8px] px-2.5 py-1.5 text-[10px] font-medium text-[#087f98] hover:bg-[#eef9fb]">View</button><button type="button" onClick={() => handleDeleteReport(report)} className="rounded-[8px] p-1.5 text-[#d83f4d] hover:bg-[#fdecee]" aria-label="Delete report"><Trash2 size={14} /></button></div></div>)}</div></div>}
                </section>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}
