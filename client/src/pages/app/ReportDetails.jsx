import { useEffect, useState } from "react";
import { ArrowLeft, FileText } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { getGeneratedReport } from "../../api/reportsApi.js";
import { downloadReportPdf, reportFilename } from "../../utils/reportPdf.js";

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
    <div className="w-full px-4 pb-10 pt-[26px] md:px-[30px]">
      <div className="mx-auto w-full max-w-[900px]">
        <button type="button" onClick={() => navigate("/reports")} className="mb-4 inline-flex items-center gap-1.5 text-[10px] font-medium text-[#6b8290] hover:text-[#087f98]"><ArrowLeft size={13} /> Back to reports</button>
        {error ? <div className="rounded-[12px] border border-red-200 bg-red-50 p-4 text-[11px] text-red-600">{error}</div> : !report ? <div className="rounded-[14px] border border-[#dce8ee] bg-white p-8 text-[11px] text-[#6b8290]">Loading report...</div> : (
          <>
            <div className="rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-[0_5px_18px_rgba(10,48,72,0.05)]">
              <div className="flex items-start justify-between gap-3">
                <div className="flex min-w-0 flex-1 items-start gap-3"><span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] bg-[#e8f8fb] text-[#08a9c4]"><FileText size={18} /></span><div className="min-w-0"><h1 className="break-words text-[22px] font-normal text-[#102a3a]">{report.deviceName} report</h1><p className="mt-1 text-[10px] text-[#6b8290]">{report.range.toUpperCase()} · Generated {date(report.generatedAt)}</p></div></div>
                <button type="button" disabled={exporting} onClick={() => { setExporting(true); try { downloadReportPdf(report, reportFilename(report)); } finally { setExporting(false); } }} className="inline-flex h-9 shrink-0 items-center gap-1.5 rounded-[9px] bg-[#08a9c4] px-3.5 text-[10px] font-medium text-white disabled:opacity-55"><FileText size={13} /> {exporting ? "Exporting..." : "Export PDF"}</button>
              </div>
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
