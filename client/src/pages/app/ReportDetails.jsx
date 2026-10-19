import { useEffect, useState } from "react";
import { ArrowLeft, FileText } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { getGeneratedReport } from "../../api/reportsApi.js";
import { downloadReportPdf, reportFilename } from "../../utils/reportPdf.js";
import Button from "../../components/ui/Button.jsx";
import ErrorState from "../../components/ui/ErrorState.jsx";
import PageHeader from "../../components/ui/PageHeader.jsx";
import StatusBadge from "../../components/ui/StatusBadge.jsx";

function value(metricValue, unit = "") {
  return Number.isFinite(metricValue)
    ? `${Number(metricValue.toFixed(2))}${unit ? ` ${unit}` : ""}`
    : "—";
}

function date(valueToFormat) {
  if (!valueToFormat) return "—";
  const parsed = new Date(valueToFormat);
  return Number.isNaN(parsed.getTime()) ? "—" : parsed.toLocaleString();
}

export default function ReportDetails() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [report, setReport] = useState(null);
  const [error, setError] = useState("");
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    let mounted = true;

    getGeneratedReport(id)
      .then((data) => mounted && setReport(data))
      .catch((requestError) => mounted && setError(requestError.message || "Unable to load report."));

    return () => {
      mounted = false;
    };
  }, [id]);

  const trend = Array.isArray(report?.telemetryTrend) ? report.telemetryTrend : [];
  const trendKeys = [...new Set(trend.flatMap((point) => Object.keys(point.metrics || {})))];

  return (
    <div className="w-full px-4 pb-10 pt-7 md:px-8 lg:px-[30px]">
      <div className="mx-auto w-full max-w-[1080px]">
        {error ? <ErrorState message={error} /> : !report ? <div className="rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] p-8 text-sm text-[var(--color-text-muted)]">Loading report...</div> : (
          <>
            <PageHeader
              tone="analytics"
              breadcrumb={<button type="button" onClick={() => navigate("/reports")} className="inline-flex items-center gap-1.5 font-medium hover:text-[var(--color-primary)]"><ArrowLeft size={13} /> Back to reports</button>}
              title={`${report.deviceName} report`}
              description={`${report.range.toUpperCase()} · Generated ${date(report.generatedAt)}`}
              status={<StatusBadge tone={report.deviceStatus === "ONLINE" ? "success" : "neutral"}>{report.deviceStatus || "Status unavailable"}</StatusBadge>}
              actions={<Button type="button" loading={exporting} loadingLabel="Exporting..." onClick={() => { setExporting(true); try { downloadReportPdf(report, reportFilename(report)); } finally { setExporting(false); } }} icon={FileText}>Export PDF</Button>}
            />
            <div className="mt-6 rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-card)]">
              <p className="text-xs text-[var(--color-text-muted)]">Persisted report snapshot</p>
              <div className="mt-5 grid gap-3 sm:grid-cols-3"><div><p className="text-[9px] text-[#8ba0ab]">Status</p><p className="mt-1 text-[11px] font-semibold text-[#102a3a]">{report.deviceStatus || "—"}</p></div><div><p className="text-[9px] text-[#8ba0ab]">Period</p><p className="mt-1 text-[11px] font-semibold text-[#102a3a]">{date(report.periodStart)} – {date(report.periodEnd)}</p></div><div><p className="text-[9px] text-[#8ba0ab]">Alerts</p><p className="mt-1 text-[11px] font-semibold text-[#102a3a]">{report.alertCount}</p></div></div>
            </div>

            <section className="mt-4 rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-[0_5px_18px_rgba(10,48,72,0.05)]"><h2 className="text-[14px] font-semibold text-[#102a3a]">Telemetry summary</h2>{Object.keys(report.telemetrySummary || {}).length === 0 ? <p className="mt-4 text-[11px] text-[#6b8290]">No telemetry recorded during this period.</p> : <div className="mt-4 grid gap-3 sm:grid-cols-2">{Object.entries(report.telemetrySummary).map(([key, metric]) => <div key={key} className="rounded-[11px] border border-[#e7eff3] bg-[#f8fbfd] p-3.5"><p className="text-[11px] font-semibold text-[#102a3a]">{metric.label || key}</p><p className="mt-2 text-[18px] font-semibold text-[#102a3a]">{value(metric.latest, metric.unit)}</p><div className="mt-2 grid grid-cols-3 gap-2 text-[9px] text-[#6b8290]"><span>Min<br /><b>{value(metric.minimum, metric.unit)}</b></span><span>Max<br /><b>{value(metric.maximum, metric.unit)}</b></span><span>Avg<br /><b>{value(metric.average, metric.unit)}</b></span></div><p className="mt-2 text-[9px] text-[#8ba0ab]">{metric.count} samples</p></div>)}</div>}</section>

            <section className="mt-4 rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-[0_5px_18px_rgba(10,48,72,0.05)]"><h2 className="text-[14px] font-semibold text-[#102a3a]">Telemetry trend</h2>{trend.length === 0 || trendKeys.length === 0 ? <p className="mt-4 rounded-[10px] border border-dashed border-[#dce8ee] bg-[#f8fbfd] p-4 text-[11px] text-[#6b8290]">Telemetry trend data is not available for this historical report.</p> : <div className="mt-4 overflow-x-auto"><table className="w-full min-w-[520px] text-left text-[10px]"><thead><tr className="border-b border-[#dce8ee] text-[#6b8290]"><th className="px-2 py-2 font-semibold">Time</th>{trendKeys.map((key) => <th key={key} className="px-2 py-2 font-semibold">{report.telemetrySummary?.[key]?.label || key}{report.telemetrySummary?.[key]?.unit ? ` (${report.telemetrySummary[key].unit})` : ""}</th>)}</tr></thead><tbody>{trend.map((point) => <tr key={point.bucketStart} className="border-b border-[#edf3f6] last:border-0"><td className="px-2 py-2 text-[#6b8290]">{date(point.bucketStart)}</td>{trendKeys.map((key) => <td key={key} className="px-2 py-2 font-medium text-[#102a3a]">{point.metrics?.[key] === undefined ? "—" : value(point.metrics[key])}</td>)}</tr>)}</tbody></table></div>}</section>

            <section className="mt-4 rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-[0_5px_18px_rgba(10,48,72,0.05)]"><h2 className="text-[14px] font-semibold text-[#102a3a]">Alert summary</h2><p className="mt-2 text-[11px] text-[#6b8290]">Severity: {Object.entries(report.severityCounts || {}).map(([key, count]) => `${key} ${count}`).join(" · ") || "No alerts"}</p><p className="mt-2 text-[11px] text-[#6b8290]">Status: {Object.entries(report.statusCounts || {}).map(([key, count]) => `${key} ${count}`).join(" · ") || "No alerts"}</p></section>
          </>
        )}
      </div>
    </div>
  );
}
