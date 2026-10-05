import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getAlerts } from "../../api/alertsApi.js";

const severityStyles = {
  CRITICAL: {
    container: "border-[#f7d4d7] bg-[#fff7f8]",
    badge: "bg-[#fdecee] text-[#d83f4d]",
    dot: "bg-[#e24e5a]",
  },
  WARNING: {
    container: "border-[#f5e1bd] bg-[#fffaf2]",
    badge: "bg-[#fff1d7] text-[#c67b08]",
    dot: "bg-[#e59a22]",
  },
  INFO: {
    container: "border-[#d7ebf1] bg-[#f7fcfd]",
    badge: "bg-[#e8f8fb] text-[#087f98]",
    dot: "bg-[#08a9c4]",
  },
};

const statusStyles = {
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

function formatType(type) {
  return (type || "Alert")
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function formatRelativeTime(value) {
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

export default function LatestAlerts() {
  const navigate = useNavigate();
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const pollingInFlight = useRef(false);

  useEffect(() => {
    let cancelled = false;

    async function loadAlerts({ initial = false } = {}) {
      if (pollingInFlight.current) return;

      pollingInFlight.current = true;

      try {
        const data = await getAlerts();

        if (cancelled) return;

        setAlerts(Array.isArray(data) ? data : []);
        setError("");
      } catch (err) {
        if (!cancelled && initial) {
          setError(err.message || "Unable to load alerts.");
        }
      } finally {
        pollingInFlight.current = false;

        if (!cancelled && initial) {
          setLoading(false);
        }
      }
    }

    loadAlerts({ initial: true });
    const intervalId = window.setInterval(() => loadAlerts(), 5000);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
      pollingInFlight.current = false;
    };
  }, []);

  const latestAlerts = [...alerts]
    .sort((first, second) => new Date(second.createdAt) - new Date(first.createdAt))
    .slice(0, 5);

  return (
    <section className="rounded-[14px] border border-[#dce8ee] bg-white p-[18px] shadow-[0_6px_20px_rgba(10,48,72,0.07)]">
      <div className="flex items-center justify-between">
        <h2 className="text-[15px] font-semibold text-[#102a3a]">
          Latest alerts
        </h2>

        <button
          type="button"
          onClick={() => navigate("/alerts")}
          className="text-[11px] font-medium text-[#08a9c4]"
        >
          See all
        </button>
      </div>

      <div className="mt-[13px] space-y-3">
        {loading ? (
          <div className="space-y-3" aria-label="Loading alerts">
            {[1, 2, 3].map((item) => (
              <div key={item} className="h-[76px] animate-pulse rounded-[10px] bg-[#f8fbfd]" />
            ))}
          </div>
        ) : error ? (
          <div className="rounded-[10px] bg-[#f8fbfd] p-4 text-[10px] text-[#6b8290]">
            Unable to load alerts right now.
          </div>
        ) : latestAlerts.length === 0 ? (
          <div className="rounded-[10px] bg-[#f8fbfd] p-4 text-[10px] text-[#6b8290]">
            No alerts yet
          </div>
        ) : latestAlerts.map((alert) => {
          const style = severityStyles[alert.severity] || severityStyles.INFO;
          const status = statusStyles[alert.status] || statusStyles.ACTIVE;

          return (
            <div
              key={alert.id}
              className={`rounded-[10px] border p-3 ${status.container}`}
            >
              <div className="flex items-center justify-between">
                <span
                  className={`inline-flex h-[22px] items-center gap-1.5 rounded-full px-2 text-[10px] font-semibold ${style.badge}`}
                >
                  <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} />
                  {alert.severity || "INFO"}
                </span>

                <span className={`text-[9px] font-medium ${status.text}`}>
                  {alert.status === "ACKNOWLEDGED" ? "Acknowledged" : alert.status === "RESOLVED" ? "Resolved" : "Active"}
                </span>
              </div>

              <div className="mt-2 text-[11px] font-medium text-[#102a3a]">
                {formatType(alert.type)}
              </div>

              <div className="mt-1 text-[9px] text-[#8397a2]">
                {alert.deviceName || "Unknown device"} · {formatRelativeTime(alert.createdAt)}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
