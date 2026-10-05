import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  Bell,
  Check,
  CheckCircle2,
  ChevronRight,
  ClipboardCheck,
  Filter,
  ListFilter,
  Search,
  SlidersHorizontal,
  Trash2,
  Thermometer,
  XCircle,
} from "lucide-react";

import {
  acknowledgeAlert,
  clearAllAlerts,
  deleteAlert,
  getAlerts,
  resolveAlert,
} from "../../api/alertsApi.js";

const statusTabs = [
  { id: "ALL", label: "All alerts" },
  { id: "UNRESOLVED", label: "Unresolved" },
  { id: "ACKNOWLEDGED", label: "Acknowledged" },
  { id: "RESOLVED", label: "Resolved" },
];

const severityStyles = {
  CRITICAL: {
    badge: "bg-[#fdecee] text-[#d83f4d]",
    icon: "bg-[#fff0f1] text-[#e24e5a]",
    row: "bg-[#fff4f5] border-[#f7d1d5]",
    dot: "bg-[#e24e5a]",
    panel: "border-[#f5cdd1] bg-[#fff7f8] text-[#d83f4d]",
  },
  WARNING: {
    badge: "bg-[#fff1d7] text-[#c67b08]",
    icon: "bg-[#fff7e7] text-[#d49a24]",
    row: "bg-white border-[#dce8ee]",
    dot: "bg-[#e59a22]",
    panel: "border-[#f3dfba] bg-[#fffaf2] text-[#c67b08]",
  },
  INFO: {
    badge: "bg-[#e8f8fb] text-[#087f98]",
    icon: "bg-[#eef5ff] text-[#4d7ee8]",
    row: "bg-white border-[#dce8ee]",
    dot: "bg-[#4d7ee8]",
    panel: "border-[#d7ebf1] bg-[#f7fcfd] text-[#087f98]",
  },
};

const statusStyles = {
  ACTIVE: {
    text: "text-[#d83f4d]",
    badge: "bg-[#fdecee] text-[#d83f4d]",
    row: "border-l-4 border-l-[#e24e5a] bg-[#fff8f8]",
    selected: "ring-1 ring-inset ring-[#e24e5a]/35",
  },
  ACKNOWLEDGED: {
    text: "text-[#2563b8]",
    badge: "bg-[#eef5ff] text-[#2563b8]",
    row: "border-l-4 border-l-[#4d7ee8] bg-[#f7fbff]",
    selected: "ring-1 ring-inset ring-[#4d7ee8]/35",
  },
  RESOLVED: {
    text: "text-[#16815f]",
    badge: "bg-[#e8f7f2] text-[#16815f]",
    row: "border-l-4 border-l-[#16a57a] bg-[#f3fbf7]",
    selected: "ring-1 ring-inset ring-[#16a57a]/35",
  },
};

function formatType(type = "") {
  return type
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function formatDate(value) {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return "—";

  return new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

function formatRelativeTime(value) {
  if (!value) return "—";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";

  const elapsedMinutes = Math.max(0, Math.round((Date.now() - date) / 60000));

  if (elapsedMinutes < 1) return "Just now";
  if (elapsedMinutes < 60) return `${elapsedMinutes} min ago`;

  const elapsedHours = Math.round(elapsedMinutes / 60);
  if (elapsedHours < 24) return `${elapsedHours} hr ago`;

  const elapsedDays = Math.round(elapsedHours / 24);
  return `${elapsedDays} day${elapsedDays === 1 ? "" : "s"} ago`;
}

function formatValue(value, type) {
  if (value === null || value === undefined) return "—";
  if (type?.includes("TEMPERATURE")) return `${value} °C`;
  if (type?.includes("HUMIDITY")) return `${value} %`;
  if (type?.includes("BATTERY")) return `${value} %`;
  return String(value);
}

function getAlertIcon(type) {
  if (type?.includes("TEMPERATURE")) return Thermometer;
  if (type === "DEVICE_OFFLINE") return XCircle;
  return AlertCircle;
}

function isWithinDateRange(value, range) {
  if (range === "ALL") return true;

  const createdAt = new Date(value).getTime();
  if (Number.isNaN(createdAt)) return false;

  return Date.now() - createdAt <= Number(range) * 24 * 60 * 60 * 1000;
}

function getRecommendation(type) {
  if (type === "TEMPERATURE_HIGH") {
    return "Check the device environment and verify cooling or ventilation if the reading remains above the configured threshold.";
  }

  if (type === "DEVICE_OFFLINE") {
    return "Check the device power, network connection, and MQTT connectivity, then confirm telemetry resumes.";
  }

  if (type === "BATTERY_LOW") {
    return "Check the device power source and replace or recharge the battery when possible.";
  }

  if (type?.includes("HUMIDITY")) {
    return "Inspect the monitored environment and confirm the sensor is positioned correctly.";
  }

  return "Review the device and confirm that the monitored condition has returned to a safe state.";
}

function SummaryCard({ icon: Icon, value, label, note, tone }) {
  const tones = {
    cyan: "bg-[#e8f8fb] text-[#08a9c4]",
    red: "bg-[#fdecee] text-[#e24e5a]",
    amber: "bg-[#fff1d7] text-[#d49a24]",
    green: "bg-[#e8f7f2] text-[#16a57a]",
  };

  return (
    <div className="min-w-0 rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-[17px]">
      <div className="flex items-start justify-between gap-3">
        <div className={`flex h-9 w-9 items-center justify-center rounded-[10px] ${tones[tone]}`}>
          <Icon size={17} />
        </div>
        <span className="truncate text-right text-[9px] text-[#08a9c4]">{note}</span>
      </div>
      <div className="mt-3 text-[24px] font-normal leading-none text-[#102a3a]">{value}</div>
      <div className="mt-2 text-[10px] text-[#6b8290]">{label}</div>
    </div>
  );
}

function AlertRow({ alert, selected, onSelect }) {
  const styles = severityStyles[alert.severity] || severityStyles.INFO;
  const status = statusStyles[alert.status] || statusStyles.ACTIVE;
  const Icon = getAlertIcon(alert.type);

  return (
    <button
      type="button"
      onClick={() => onSelect(alert.id)}
      className={`flex w-full items-center gap-3 border-b px-3.5 py-3 text-left transition last:border-b-0 hover:bg-[#f8fbfd] sm:px-4 ${status.row} ${selected ? status.selected : ""}`}
    >
      <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-[9px] ${styles.icon}`}>
        <Icon size={16} />
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[9px] font-bold ${styles.badge}`}>
            <span className={`h-1.5 w-1.5 rounded-full ${styles.dot}`} />
            {alert.severity || "INFO"}
          </span>
          <span className="truncate text-[10px] font-medium text-[#102a3a]">{formatType(alert.type)}</span>
        </div>
        <div className="mt-1 truncate text-[11px] font-medium text-[#102a3a]">{alert.message || formatType(alert.type)}</div>
        <div className="mt-1 truncate text-[9px] text-[#8397a2]">{alert.deviceName || "Unknown device"}</div>
      </div>

      <div className="hidden shrink-0 text-right sm:block">
        <div className="text-[9px] text-[#8ba0aa]">{formatRelativeTime(alert.createdAt)}</div>
        <span className={`mt-1 inline-flex rounded-full px-2 py-0.5 text-[9px] font-semibold ${status.badge}`}>
          {alert.status === "ACKNOWLEDGED" ? "Acknowledged" : alert.status === "RESOLVED" ? "Resolved" : "Active"}
        </span>
      </div>

      <ChevronRight size={15} className="shrink-0 text-[#9aaeba]" />
    </button>
  );
}

function AlertDetails({ alert, updatingId, deletingId, onAcknowledge, onResolve, onDelete }) {
  if (!alert) {
    return (
      <aside className="flex min-h-[360px] items-center justify-center rounded-[14px] border border-[#dce8ee] bg-white p-5 text-center shadow-[0_5px_18px_rgba(10,48,72,0.05)]">
        <div>
          <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-[12px] bg-[#eef9fb] text-[#08a9c4]"><Bell size={20} /></div>
          <p className="mt-3 text-[12px] font-semibold text-[#102a3a]">Select an alert</p>
          <p className="mt-1 text-[10px] text-[#6b8290]">Choose an alert to view its details.</p>
        </div>
      </aside>
    );
  }

  const styles = severityStyles[alert.severity] || severityStyles.INFO;
  const status = statusStyles[alert.status] || statusStyles.ACTIVE;
  const Icon = getAlertIcon(alert.type);
  const isUpdating = updatingId === alert.id;
  const isDeleting = deletingId === alert.id;

  return (
    <aside className="rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-[19px]">
      <div className="flex items-start justify-between gap-3">
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[9px] font-bold ${styles.badge}`}>
          <span className={`h-1.5 w-1.5 rounded-full ${styles.dot}`} />
          {alert.severity || "INFO"}
        </span>
        <span className={`inline-flex rounded-full px-2.5 py-1 text-[9px] font-semibold ${status.badge}`}>
          {alert.status === "ACKNOWLEDGED" ? "Acknowledged" : alert.status === "RESOLVED" ? "Resolved" : "Active"}
        </span>
      </div>

      <h2 className="mt-3 text-[17px] font-normal leading-6 text-[#102a3a]">{alert.message || formatType(alert.type)}</h2>

      <div className={`mt-4 rounded-[10px] border p-3 text-[10px] leading-4 ${styles.panel}`}>
        <div className="flex items-start gap-2"><Icon size={15} className="mt-0.5 shrink-0" /><span>{getRecommendation(alert.type)}</span></div>
      </div>

      {alert.type !== "DEVICE_OFFLINE" ? (
        <div className="mt-4 rounded-[11px] bg-[#f8fbfd] p-3.5">
          <div className="flex items-end justify-between gap-4">
            <div><div className="text-[8px] uppercase tracking-[0.06em] text-[#9aaeba]">Current reading</div><div className="mt-1 text-[23px] font-normal text-[#e24e5a]">{formatValue(alert.value, alert.type)}</div></div>
            <div className="text-right"><div className="text-[8px] uppercase tracking-[0.06em] text-[#9aaeba]">Safe threshold</div><div className="mt-2 text-[11px] font-semibold text-[#102a3a]">{formatValue(alert.threshold, alert.type)}</div></div>
          </div>
        </div>
      ) : null}

      <dl className="mt-5 space-y-3 text-[10px]">
        <div className="flex justify-between gap-4"><dt className="text-[#8397a2]">Device</dt><dd className="truncate text-right font-semibold text-[#102a3a]">{alert.deviceName || "—"}</dd></div>
        <div className="flex justify-between gap-4"><dt className="text-[#8397a2]">Device key</dt><dd className="truncate text-right font-medium text-[#102a3a]">{alert.deviceKey || "—"}</dd></div>
        <div className="flex justify-between gap-4"><dt className="text-[#8397a2]">Triggered</dt><dd className="text-right font-medium text-[#102a3a]">{formatDate(alert.createdAt)}</dd></div>
        <div className="flex justify-between gap-4"><dt className="text-[#8397a2]">Alert type</dt><dd className="text-right font-medium text-[#102a3a]">{formatType(alert.type)}</dd></div>
        <div className="flex items-center justify-between gap-4"><dt className="text-[#8397a2]">Status</dt><dd className={`rounded-full px-2.5 py-1 text-right text-[9px] font-semibold ${status.badge}`}>{alert.status === "ACKNOWLEDGED" ? "Acknowledged" : alert.status === "RESOLVED" ? "Resolved" : "Active"}</dd></div>
      </dl>

      <div className="mt-5 border-t border-[#edf3f6] pt-4">
        <h3 className="text-[10px] font-bold text-[#102a3a]">Recommended action</h3>
        <p className="mt-2 text-[10px] leading-4 text-[#6b8290]">{getRecommendation(alert.type)}</p>
      </div>

      <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2">
        {alert.status === "ACTIVE" ? (
          <button type="button" disabled={isUpdating} onClick={() => onAcknowledge(alert.id)} className="inline-flex min-h-[44px] w-full items-center justify-center gap-1.5 rounded-[9px] bg-[#08a9c4] px-3.5 text-[10px] font-semibold text-white transition hover:bg-[#0799b2] disabled:cursor-not-allowed disabled:opacity-60">
            <Check size={13} /> Acknowledge
          </button>
        ) : null}

        {alert.status !== "RESOLVED" ? (
          <button type="button" disabled={isUpdating} onClick={() => onResolve(alert.id)} className="inline-flex min-h-[44px] w-full items-center justify-center gap-1.5 rounded-[9px] border border-[#dce8ee] bg-white px-3.5 text-[10px] font-semibold text-[#526b79] transition hover:border-[#08a9c4] hover:text-[#087f98] disabled:cursor-not-allowed disabled:opacity-60">
            <CheckCircle2 size={13} /> {isUpdating ? "Updating..." : "Mark as resolved"}
          </button>
        ) : null}

        <button type="button" disabled title="Task creation will be available in a future release" className="inline-flex min-h-[44px] w-full cursor-not-allowed items-center justify-center gap-1.5 rounded-[9px] border border-[#dce8ee] bg-white px-3.5 text-[10px] font-semibold text-[#9aaeba]">
          <ClipboardCheck size={13} /> Create task
        </button>

        <button
          type="button"
          disabled={isDeleting || isUpdating}
          onClick={() => onDelete(alert.id)}
          className="inline-flex min-h-[44px] w-full items-center justify-center gap-1.5 rounded-[9px] border border-[#f3cdd1] bg-[#fff7f8] px-3.5 text-[10px] font-semibold text-[#d83f4d] transition hover:border-[#e24e5a] hover:bg-[#fdecee] disabled:cursor-not-allowed disabled:opacity-60"
        >
          <XCircle size={13} />
          {isDeleting ? "Deleting..." : "Delete alert"}
        </button>
      </div>
    </aside>
  );
}

export default function Alerts() {
  const [alerts, setAlerts] = useState([]);
  const [selectedAlertId, setSelectedAlertId] = useState("");
  const [statusTab, setStatusTab] = useState("ALL");
  const [search, setSearch] = useState("");
  const [deviceFilter, setDeviceFilter] = useState("ALL");
  const [severityFilter, setSeverityFilter] = useState("ALL");
  const [dateRange, setDateRange] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [updatingId, setUpdatingId] = useState("");
  const [deleteTargetId, setDeleteTargetId] = useState("");
  const [deletingId, setDeletingId] = useState("");
  const [clearAllOpen, setClearAllOpen] = useState(false);
  const [clearingAll, setClearingAll] = useState(false);
  const pollingInFlight = useRef(false);
  const alertsRequestVersion = useRef(0);

  useEffect(() => {
    let mounted = true;

    async function loadAlerts({ initial = false } = {}) {
      if (pollingInFlight.current) return;

      pollingInFlight.current = true;
      const requestVersion = alertsRequestVersion.current;

      try {
        if (initial) {
          setLoading(true);
          setError("");
        }

        const data = await getAlerts();
        const loadedAlerts = Array.isArray(data) ? data : [];

        if (!mounted || requestVersion !== alertsRequestVersion.current) return;

        setAlerts(loadedAlerts);

        setSelectedAlertId((currentSelectedId) => {
          if (
            currentSelectedId &&
            loadedAlerts.some((alert) => alert.id === currentSelectedId)
          ) {
            return currentSelectedId;
          }

          return loadedAlerts[0]?.id || "";
        });
      } catch (err) {
        if (initial && mounted) {
          setError(err.message || "Unable to load alerts.");
        } else if (mounted) {
          console.error("Unable to refresh alerts:", err);
        }
      } finally {
        pollingInFlight.current = false;

        if (initial && mounted) {
          setLoading(false);
        }
      }
    }

    loadAlerts({ initial: true });

    const intervalId = window.setInterval(() => {
      loadAlerts();
    }, 5000);

    return () => {
      mounted = false;
      window.clearInterval(intervalId);
      pollingInFlight.current = false;
    };
  }, []);

  const deviceOptions = useMemo(() => {
    const devices = new Map();
    alerts.forEach((alert) => {
      const key = alert.deviceId || alert.deviceKey || alert.deviceName;
      if (key && !devices.has(key)) devices.set(key, alert.deviceName || alert.deviceKey || key);
    });
    return Array.from(devices, ([value, label]) => ({ value, label }));
  }, [alerts]);

  const counts = useMemo(() => {
    const unresolved = alerts.filter((alert) => alert.status !== "RESOLVED");
    return {
      totalActive: unresolved.length,
      critical: unresolved.filter((alert) => alert.severity === "CRITICAL").length,
      warnings: unresolved.filter((alert) => alert.severity === "WARNING").length,
      resolved: alerts.filter((alert) => alert.status === "RESOLVED").length,
      all: alerts.length,
      acknowledged: alerts.filter((alert) => alert.status === "ACKNOWLEDGED").length,
    };
  }, [alerts]);

  const filteredAlerts = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();

    return alerts.filter((alert) => {
      const matchesStatus = statusTab === "ALL" || (statusTab === "UNRESOLVED" && alert.status === "ACTIVE") || alert.status === statusTab;
      const matchesDevice = deviceFilter === "ALL" || alert.deviceId === deviceFilter || alert.deviceKey === deviceFilter || alert.deviceName === deviceFilter;
      const matchesSeverity = severityFilter === "ALL" || alert.severity === severityFilter;
      const searchable = [alert.deviceName, alert.deviceKey, alert.message, alert.type].filter(Boolean).join(" ").toLowerCase();

      return matchesStatus && matchesDevice && matchesSeverity && isWithinDateRange(alert.createdAt, dateRange) && (!normalizedSearch || searchable.includes(normalizedSearch));
    });
  }, [alerts, dateRange, deviceFilter, search, severityFilter, statusTab]);

  const selectedAlert = filteredAlerts.find((alert) => alert.id === selectedAlertId) || filteredAlerts[0] || null;

  useEffect(() => {
    if (selectedAlert && selectedAlert.id !== selectedAlertId) setSelectedAlertId(selectedAlert.id);
    if (!selectedAlert && selectedAlertId) setSelectedAlertId("");
  }, [selectedAlert, selectedAlertId]);

  async function updateAlert(id, action) {
    try {
      setUpdatingId(id);
      setActionError("");
      const updatedAlert = action === "acknowledge" ? await acknowledgeAlert(id) : await resolveAlert(id);
      setAlerts((currentAlerts) => currentAlerts.map((alert) => alert.id === updatedAlert.id ? updatedAlert : alert));
      setSelectedAlertId(updatedAlert.id);
    } catch (err) {
      setActionError(err.message || "Unable to update the alert.");
    } finally {
      setUpdatingId("");
    }
  }

  async function handleDeleteAlert() {
    if (!deleteTargetId || deletingId) return;

    try {
      setDeletingId(deleteTargetId);
      setActionError("");

      await deleteAlert(deleteTargetId);

      setAlerts((currentAlerts) =>
        currentAlerts.filter((alert) => alert.id !== deleteTargetId),
      );
      setDeleteTargetId("");
    } catch (err) {
      setActionError(err.message || "Unable to delete the alert.");
    } finally {
      setDeletingId("");
    }
  }

  async function handleClearAllAlerts() {
    if (clearingAll || alerts.length === 0) return;

    try {
      setClearingAll(true);
      setActionError("");

      await clearAllAlerts();

      alertsRequestVersion.current += 1;
      setAlerts([]);
      setSelectedAlertId("");
      setClearAllOpen(false);
    } catch (err) {
      setActionError(err.message || "Unable to clear alert history.");
    } finally {
      setClearingAll(false);
    }
  }

  return (
    <div className="w-full px-4 pb-10 pt-[26px] md:px-[30px]">
      <div className="mx-auto w-full max-w-[1144px]">
        <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-[26px] font-normal leading-tight text-[#102a3a]">Alerts</h1>
            <p className="mt-[5px] text-[12px] text-[#6b8290]">Review, acknowledge, and resolve events across your network.</p>
          </div>

          <div className="flex flex-wrap gap-2.5">
            <button type="button" disabled title="Alert rules will be available in a future release" className="inline-flex h-10 cursor-not-allowed items-center gap-2 rounded-[10px] border border-[#dce8ee] bg-white px-4 text-[11px] font-medium text-[#9aaeba]"><SlidersHorizontal size={14} /> Alert rules</button>
            <button type="button" disabled title="Mark all read will be available in a future release" className="inline-flex h-10 cursor-not-allowed items-center gap-2 rounded-[10px] bg-[#08a9c4] px-4 text-[11px] font-medium text-white opacity-55"><Check size={14} /> Mark all read</button>
            <button type="button" disabled={loading || alerts.length === 0 || clearingAll} onClick={() => setClearAllOpen(true)} title={alerts.length === 0 ? "There is no alert history to clear" : "Clear all alert history"} className="inline-flex h-10 items-center gap-2 rounded-[10px] border border-[#f3cdd1] bg-[#fff7f8] px-4 text-[11px] font-medium text-[#d83f4d] transition hover:border-[#e24e5a] hover:bg-[#fdecee] disabled:cursor-not-allowed disabled:opacity-50"><Trash2 size={14} /> Clear alert history</button>
          </div>
        </section>

        <section className="mt-6 grid gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
          <SummaryCard icon={Bell} value={counts.totalActive} label="Total active" note={counts.totalActive > 0 ? "Across your network" : "No active alerts"} tone="cyan" />
          <SummaryCard icon={AlertCircle} value={counts.critical} label="Critical" note={counts.critical > 0 ? "Immediate action" : "No critical alerts"} tone="red" />
          <SummaryCard icon={AlertCircle} value={counts.warnings} label="Warnings" note={counts.warnings > 0 ? "Needs review" : "No warnings"} tone="amber" />
          <SummaryCard icon={CheckCircle2} value={counts.resolved} label="Resolved" note={counts.resolved > 0 ? "Resolved alerts" : "No resolved alerts"} tone="green" />
        </section>

        <section className="mt-6 rounded-[14px] border border-[#dce8ee] bg-white p-3 shadow-[0_5px_18px_rgba(10,48,72,0.05)] sm:p-[14px]">
          <div className="grid gap-2.5 xl:grid-cols-[minmax(240px,1fr)_180px_170px_170px_auto]">
            <label className="relative block">
              <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-[#8298a4]" />
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search alerts or devices..." className="h-10 w-full rounded-[9px] border border-[#dce8ee] bg-white pl-9 pr-3 text-[11px] text-[#102a3a] outline-none placeholder:text-[#9aaeba] focus:border-[#08a9c4] focus:ring-2 focus:ring-[#08a9c4]/10" />
            </label>

            <select value={deviceFilter} onChange={(event) => setDeviceFilter(event.target.value)} className="h-10 rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[10px] text-[#526b79] outline-none focus:border-[#08a9c4]"><option value="ALL">All devices</option>{deviceOptions.map((device) => <option key={device.value} value={device.value}>{device.label}</option>)}</select>
            <select value={severityFilter} onChange={(event) => setSeverityFilter(event.target.value)} className="h-10 rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[10px] text-[#526b79] outline-none focus:border-[#08a9c4]"><option value="ALL">All severities</option><option value="INFO">Info</option><option value="WARNING">Warning</option><option value="CRITICAL">Critical</option></select>
            <select value={dateRange} onChange={(event) => setDateRange(event.target.value)} className="h-10 rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[10px] text-[#526b79] outline-none focus:border-[#08a9c4]"><option value="ALL">All time</option><option value="1">Last 24 hours</option><option value="7">Last 7 days</option><option value="30">Last 30 days</option></select>
            <button type="button" disabled title="More filters will be available in a future release" className="inline-flex h-10 cursor-not-allowed items-center justify-center gap-2 rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[10px] font-medium text-[#9aaeba]"><Filter size={14} /> More filters</button>
          </div>

          <div className="mt-3 flex items-center gap-1 overflow-x-auto border-t border-[#edf3f6] pt-3">
            {statusTabs.map((tab) => {
              const count = tab.id === "ALL" ? counts.all : tab.id === "UNRESOLVED" ? counts.totalActive : tab.id === "ACKNOWLEDGED" ? counts.acknowledged : counts.resolved;
              const selected = statusTab === tab.id;
              return <button type="button" key={tab.id} onClick={() => setStatusTab(tab.id)} className={`relative flex h-9 shrink-0 items-center gap-1.5 px-3 text-[10px] transition ${selected ? "font-bold text-[#087f98]" : "font-medium text-[#7d929e] hover:text-[#102a3a]"}`}>{tab.label}<span className={`rounded-full px-1.5 py-0.5 text-[9px] ${selected ? "bg-[#e8f8fb] text-[#087f98]" : "bg-[#f4f8fb] text-[#8ba0aa]"}`}>{count}</span>{selected ? <span className="absolute inset-x-3 bottom-0 h-0.5 rounded-full bg-[#08a9c4]" /> : null}</button>;
            })}
          </div>
        </section>

        {error ? <div className="mt-5 rounded-[12px] border border-red-200 bg-red-50 p-4 text-[11px] text-red-600">{error}</div> : null}
        {actionError ? <div className="mt-5 rounded-[12px] border border-red-200 bg-red-50 p-4 text-[11px] text-red-600">{actionError}</div> : null}

        {loading ? (
          <div className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,1.7fr)_minmax(320px,0.9fr)]"><div className="h-[380px] animate-pulse rounded-[14px] border border-[#dce8ee] bg-white" /><div className="h-[500px] animate-pulse rounded-[14px] border border-[#dce8ee] bg-white" /></div>
        ) : (
          <section className="mt-6 grid items-start gap-4 lg:grid-cols-[minmax(0,1.7fr)_minmax(320px,0.9fr)]">
            <div className="overflow-hidden rounded-[14px] border border-[#dce8ee] bg-white shadow-[0_5px_18px_rgba(10,48,72,0.05)]">
              <div className="flex items-center justify-between gap-3 border-b border-[#edf3f6] px-4 py-3.5"><div className="flex items-center gap-2"><h2 className="text-[13px] font-semibold text-[#102a3a]">Recent alerts</h2><span className="rounded-full bg-[#fdecee] px-2 py-0.5 text-[9px] font-semibold text-[#d83f4d]">{counts.totalActive} active</span></div><span className="hidden items-center gap-1 text-[9px] text-[#7d929e] sm:flex"><ListFilter size={12} /> Newest first</span></div>
              {filteredAlerts.length > 0 ? filteredAlerts.map((alert) => <AlertRow key={alert.id} alert={alert} selected={selectedAlert?.id === alert.id} onSelect={setSelectedAlertId} />) : <div className="px-5 py-16 text-center"><CheckCircle2 size={26} className="mx-auto text-[#16a57a]" /><p className="mt-3 text-[12px] font-semibold text-[#102a3a]">No matching alerts</p><p className="mt-1 text-[10px] text-[#6b8290]">Try changing the filters to see more alerts.</p></div>}
              {filteredAlerts.length > 0 && counts.totalActive === 0 ? <div className="flex items-center gap-3 border-t border-[#d9eee7] bg-[#eaf8f3] px-4 py-3"><CheckCircle2 size={18} className="text-[#16a57a]" /><div><p className="text-[10px] font-medium text-[#16815f]">No other urgent alerts</p><p className="text-[9px] text-[#6b8290]">All monitored conditions are within safe limits.</p></div></div> : null}
            </div>

            <AlertDetails alert={selectedAlert} updatingId={updatingId} deletingId={deletingId} onAcknowledge={(id) => updateAlert(id, "acknowledge")} onResolve={(id) => updateAlert(id, "resolve")} onDelete={setDeleteTargetId} />
          </section>
        )}

        {deleteTargetId ? (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0b2535]/45 px-4">
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="delete-alert-title"
              className="w-full max-w-[390px] rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-2xl"
            >
              <div className="flex h-10 w-10 items-center justify-center rounded-[11px] bg-[#fdecee] text-[#e24e5a]">
                <XCircle size={20} />
              </div>

              <h2 id="delete-alert-title" className="mt-4 text-[15px] font-semibold text-[#102a3a]">
                Delete this alert?
              </h2>

              <p className="mt-2 text-[11px] leading-5 text-[#6b8290]">
                This permanently removes the alert from your alert history.
                This action cannot be undone.
              </p>

              <div className="mt-5 flex justify-end gap-2.5">
                <button
                  type="button"
                  disabled={Boolean(deletingId)}
                  onClick={() => setDeleteTargetId("")}
                  className="h-9 rounded-[9px] border border-[#dce8ee] bg-white px-4 text-[10px] font-semibold text-[#526b79] transition hover:bg-[#f8fbfd] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  Cancel
                </button>

                <button
                  type="button"
                  disabled={Boolean(deletingId)}
                  onClick={handleDeleteAlert}
                  className="h-9 rounded-[9px] bg-[#e24e5a] px-4 text-[10px] font-semibold text-white transition hover:bg-[#d83f4d] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {deletingId ? "Deleting..." : "Delete alert"}
                </button>
              </div>
            </div>
          </div>
        ) : null}

        {clearAllOpen ? (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0b2535]/45 px-4">
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="clear-alert-history-title"
              className="w-full max-w-[420px] rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-2xl"
            >
              <div className="flex h-10 w-10 items-center justify-center rounded-[11px] bg-[#fdecee] text-[#e24e5a]">
                <Trash2 size={20} />
              </div>

              <h2 id="clear-alert-history-title" className="mt-4 text-[15px] font-semibold text-[#102a3a]">
                Clear all alert history?
              </h2>

              <p className="mt-2 text-[11px] leading-5 text-[#6b8290]">
                This permanently deletes all alerts in your history, including active, acknowledged, and resolved alerts. This action cannot be undone.
              </p>
              <p className="mt-2 text-[11px] leading-5 text-[#6b8290]">
                Alerts may appear again if a monitored condition is still active.
              </p>

              <div className="mt-5 flex justify-end gap-2.5">
                <button
                  type="button"
                  disabled={clearingAll}
                  onClick={() => setClearAllOpen(false)}
                  className="h-9 rounded-[9px] border border-[#dce8ee] bg-white px-4 text-[10px] font-semibold text-[#526b79] transition hover:bg-[#f8fbfd] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  Cancel
                </button>

                <button
                  type="button"
                  disabled={clearingAll}
                  onClick={handleClearAllAlerts}
                  className="h-9 rounded-[9px] bg-[#e24e5a] px-4 text-[10px] font-semibold text-white transition hover:bg-[#d83f4d] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {clearingAll ? "Clearing..." : "Clear all alerts"}
                </button>
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}
