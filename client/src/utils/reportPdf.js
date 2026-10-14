const COLORS = {
  navy: [0.04, 0.14, 0.22], blue: [0.03, 0.36, 0.58], cyan: [0.02, 0.64, 0.76],
  pale: [0.95, 0.98, 0.99], paleCyan: [0.91, 0.98, 0.99], paleBlue: [0.92, 0.96, 1], paleAmber: [1, 0.97, 0.90], palePurple: [0.96, 0.94, 1], slate: [0.29, 0.39, 0.45], muted: [0.52, 0.60, 0.65],
  line: [0.82, 0.88, 0.91], white: [1, 1, 1], amber: [0.87, 0.49, 0.05], red: [0.78, 0.18, 0.20], green: [0.10, 0.48, 0.35], purple: [0.38, 0.30, 0.72],
};

function cleanText(value) { return String(value ?? "").replace(/[^\x20-\x7E°]/g, ""); }
function pdfText(value) { return cleanText(value).replaceAll("\\", "\\\\").replaceAll("(", "\\(").replaceAll(")", "\\)").replaceAll("°", "\\260"); }
function number(value) { return Number.isFinite(value) ? Number(value.toFixed(2)).toString() : "-"; }
function parsedDate(value) { if (!value) return null; const result = new Date(value); return Number.isNaN(result.getTime()) ? null : result; }
function date(value) { const parsed = parsedDate(value); return parsed ? parsed.toLocaleString("en-GB", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", hour12: true }).replace(" am", " AM").replace(" pm", " PM") : "-"; }
function time(value) { const parsed = parsedDate(value); return parsed ? parsed.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" }) : "-"; }
function shortTime(value, includeDate = false) { const parsed = parsedDate(value); if (!parsed) return "-"; const result = parsed.toLocaleString("en-GB", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit", hour12: true }).replace(" am", " AM").replace(" pm", " PM"); return includeDate ? result : result.split(", ").pop(); }
function rangeLabel(range) { return { "1h": "Last 1 hour", "6h": "Last 6 hours", "24h": "Last 24 hours", "7d": "Last 7 days" }[range] || String(range || "-").toUpperCase(); }
function wrap(value, max = 84) { const words = cleanText(value).split(/\s+/); const lines = []; let line = ""; words.forEach((word) => { if (!word) return; if ((line + " " + word).trim().length > max) { lines.push(line); line = word; } else line = `${line} ${word}`.trim(); }); if (line) lines.push(line); return lines.length ? lines : [""]; }
function color(values) { return `${values[0]} ${values[1]} ${values[2]}`; }
function metricValue(metric, field) { const value = number(metric[field]); return value === "-" ? value : `${value}${metric.unit ? ` ${cleanText(metric.unit)}` : ""}`; }
function metricEntries(report) { return Object.entries(report.telemetrySummary || {}); }
function metricAccent(key) { const normalized = String(key).toLowerCase(); if (normalized.includes("humidity") || normalized.includes("moisture")) return { line: [0.30, 0.43, 0.82], fill: COLORS.paleBlue }; if (normalized.includes("battery")) return { line: COLORS.green, fill: [0.92, 0.98, 0.95] }; if (normalized.includes("rssi") || normalized.includes("signal")) return { line: COLORS.purple, fill: COLORS.palePurple }; if (normalized.includes("pressure") || normalized.includes("air")) return { line: COLORS.amber, fill: COLORS.paleAmber }; return { line: COLORS.cyan, fill: COLORS.paleCyan }; }
function trendKeys(report) { return [...new Set((Array.isArray(report.telemetryTrend) ? report.telemetryTrend : []).flatMap((point) => Object.keys(point.metrics || {})))]; }
function metricLabel(report, key) { return report.telemetrySummary?.[key]?.label || key; }
function sameDay(a, b) { const first = parsedDate(a); const second = parsedDate(b); return first && second && first.toDateString() === second.toDateString(); }
function periodText(report) { return sameDay(report.periodStart, report.periodEnd) ? `${date(report.periodStart).split(",")[0]}  ${time(report.periodStart)} - ${time(report.periodEnd)}` : `${date(report.periodStart)} - ${date(report.periodEnd)}`; }

class PdfPage {
  constructor(numberValue) { this.number = numberValue; this.commands = []; this.y = 744; }
  ensure(height) { return this.y - height >= 62; }
  fill(values) { this.commands.push(`${color(values)} rg`); }
  stroke(values) { this.commands.push(`${color(values)} RG`); }
  rect(x, y, width, height, fillColor, strokeColor = null) { if (fillColor) this.fill(fillColor); if (strokeColor) this.stroke(strokeColor); this.commands.push(`${x} ${y} ${width} ${height} re ${fillColor ? "f" : "S"}`); }
  line(x1, y1, x2, y2, strokeColor = COLORS.line) { this.stroke(strokeColor); this.commands.push(`${x1} ${y1} m ${x2} ${y2} l S`); }
  text(value, x, y, size = 9, fillColor = COLORS.navy) { this.fill(fillColor); this.commands.push(`BT /F1 ${size} Tf 1 0 0 1 ${x} ${y} Tm (${pdfText(value)}) Tj ET`); }
  wrapped(value, x, y, width, size = 9, fillColor = COLORS.slate, lineHeight = 13) { const lines = wrap(value, Math.max(12, Math.floor(width / (size * 0.52)))); lines.forEach((line, index) => this.text(line, x, y - index * lineHeight, size, fillColor)); return y - lines.length * lineHeight; }
  section(title, subtitle = "") { if (!this.ensure(subtitle ? 42 : 31)) return false; this.text(title.toUpperCase(), 48, this.y, 10.5, COLORS.navy); this.line(48, this.y - 8, 564, this.y - 8, COLORS.cyan); if (subtitle) this.text(subtitle, 48, this.y - 21, 7.5, COLORS.muted); this.y -= subtitle ? 40 : 28; return true; }
  footer() { this.line(48, 42, 564, 42, COLORS.line); this.text("NexaIoT  -  Smart IoT Management Platform", 48, 27, 7, COLORS.muted); this.text(`Page ${this.number}`, 520, 27, 7, COLORS.muted); }
}

function nextPage(page, pages) { page.footer(); pages.push(page); return new PdfPage(pages.length + 1); }
function panelText(page, message) { page.rect(48, page.y - 48, 516, 48, COLORS.pale, COLORS.line); page.rect(48, page.y - 48, 4, 48, COLORS.cyan); page.text(message, 64, page.y - 27, 9, COLORS.slate); page.y -= 65; }

function overview(page, report) {
  if (!page.section("Report overview")) return;
  const status = String(report.deviceStatus || "-").toUpperCase();
  const cards = [["STATUS", status], ["REPORT RANGE", rangeLabel(report.range)], ["ALERTS", String(report.alertCount ?? 0)], ["LAST SEEN", time(report.lastSeenAt)]];
  cards.forEach(([label, value], index) => { const x = 48 + index * 131; const fill = index === 0 && status === "ONLINE" ? [0.93, 0.98, 0.97] : index === 2 ? COLORS.paleAmber : index === 1 ? COLORS.paleBlue : COLORS.pale; page.rect(x, page.y - 51, 123, 51, fill, COLORS.line); page.text(label, x + 11, page.y - 16, 6.5, COLORS.muted); page.text(value, x + 11, page.y - 36, value.length > 17 ? 8 : 11, index === 2 ? COLORS.amber : index === 0 && status === "OFFLINE" ? COLORS.red : COLORS.blue); });
  page.y -= 67;
}

function deviceOverview(page, report) {
  if (!page.section("Device overview")) return;
  const rows = [["Device name", report.deviceName || "-"], ["Device ID", report.deviceId || "-"], ["Report ID", report.id || "-"], ["Reporting period", periodText(report)]];
  page.rect(48, page.y - 70, 516, 70, COLORS.pale, COLORS.line);
  rows.forEach(([label, value], index) => { const column = index % 2; const row = Math.floor(index / 2); const x = 62 + column * 258; const y = page.y - 18 - row * 29; page.text(label.toUpperCase(), x, y, 6.3, COLORS.muted); page.wrapped(value, x + 78, y, 150, 8.2, COLORS.navy, 10); });
  page.y -= 86;
}

function metricValueWithUnit(report, key, field) { return metricValue(report.telemetrySummary?.[key] || {}, field); }
function telemetryGlance(page, report, pages) {
  if (!page.section("Telemetry at a glance", "Latest readings and persisted report statistics")) return page;
  const metrics = metricEntries(report); if (!metrics.length) { panelText(page, "No telemetry was recorded during this reporting period."); return page; }
  const columns = Math.min(3, Math.max(1, metrics.length)); const gap = 9; const width = (516 - gap * (columns - 1)) / columns;
  metrics.forEach(([key, metric], index) => { if (index > 0 && index % columns === 0) page.y -= 9; if (!page.ensure(84)) { page = nextPage(page, pages); page.section("Telemetry at a glance"); } const column = index % columns; const rowStart = page.y; const x = 48 + column * (width + gap); const accent = metricAccent(key); page.rect(x, rowStart - 84, width, 84, accent.fill, COLORS.line); page.rect(x, rowStart - 84, 4, 84, accent.line); page.text(metricLabel(report, key).toUpperCase(), x + 13, rowStart - 18, 7, COLORS.muted); page.text(metricValue(metric, "latest"), x + 13, rowStart - 42, 15, COLORS.navy); page.text("LATEST", x + width - 45, rowStart - 18, 5.8, accent.line); page.text(`Average  ${metricValueWithUnit(report, key, "average")}`, x + 13, rowStart - 62, 7.4, COLORS.slate); page.text(`Range  ${metricValueWithUnit(report, key, "minimum")} - ${metricValueWithUnit(report, key, "maximum")}`, x + 13, rowStart - 75, 7.2, COLORS.slate); if (column === columns - 1 || index === metrics.length - 1) page.y = rowStart - 99; });
  return page;
}

function chartMetricPoints(report, key) { return (Array.isArray(report.telemetryTrend) ? report.telemetryTrend : []).map((point) => ({ time: point.bucketStart, value: point.metrics?.[key] })).filter((point) => Number.isFinite(point.value)); }
function drawChart(page, report, key, x, top, width, height) {
  const points = chartMetricPoints(report, key); const accent = metricAccent(key); const left = x + 32; const right = x + width - 10; const bottom = top - height + 48; const chartHeight = height - 78; const values = points.map((point) => point.value); const min = Math.min(...values); const max = Math.max(...values); const span = Math.max(1, max - min); const low = min - span * 0.12; const high = max + span * 0.12;
  page.rect(x, top - height, width, height, accent.fill, COLORS.line); page.text(metricLabel(report, key), x + 12, top - 17, 8.5, COLORS.navy); page.text(report.telemetrySummary?.[key]?.unit ? cleanText(report.telemetrySummary[key].unit) : "", x + width - 38, top - 17, 6.5, accent.line);
  [0, 0.5, 1].forEach((ratio) => { const y = bottom - chartHeight * ratio; page.line(left, y, right, y, [0.91, 0.94, 0.95]); const tick = high - (high - low) * ratio; page.text(number(tick), x + 4, y + 2, 5.7, COLORS.muted); });
  if (points.length === 1) { page.text(`Latest ${number(points[0].value)}`, left + 8, bottom + chartHeight / 2, 7.5, COLORS.slate); return; }
  let previous = null; points.forEach((point, index) => { const px = left + (right - left) * index / (points.length - 1); const py = bottom + chartHeight * (point.value - low) / (high - low); if (previous) { page.stroke(accent.line); page.commands.push(`1.8 w ${previous.x} ${previous.y} m ${px} ${py} l S`); } page.fill(accent.line); page.commands.push(`${px - 2} ${py - 2} 4 4 re f`); previous = { x: px, y: py }; if (index === 0 || index === points.length - 1) page.text(shortTime(point.time), px - 15, bottom - 14, 5.8, COLORS.muted); });
}
function telemetryCharts(page, report, pages) {
  const keys = trendKeys(report).filter((key) => chartMetricPoints(report, key).length >= 1); if (!keys.length) { if (!page.section("Telemetry trend", "Persisted time-bucket averages")) return page; panelText(page, "Telemetry trend data is unavailable for this reporting period."); return page; }
  if (!page.ensure(194)) page = nextPage(page, pages); page.section("Telemetry trend", "Persisted time-bucket averages");
  const columns = keys.length === 1 ? 1 : 2; const gap = 12; const width = columns === 1 ? 516 : (516 - gap) / 2; const height = 142;
  keys.forEach((key, index) => { if (index % columns === 0 && !page.ensure(height + 10)) page = nextPage(page, pages); const column = index % columns; const rowTop = page.y; drawChart(page, report, key, 48 + column * (width + gap), rowTop, width, height); if (column === columns - 1 || index === keys.length - 1) page.y = rowTop - height - 55; }); return page;
}

function telemetryTable(page, report, pages) {
  const metrics = metricEntries(report); if (!metrics.length) { if (!page.section("Telemetry statistics", "Detailed persisted summary values")) return page; panelText(page, "No telemetry was recorded during this reporting period."); return page; }
  if (!page.ensure(93)) page = nextPage(page, pages);
  if (!page.section("Telemetry statistics", "Detailed persisted summary values")) return page;
  const columns = [["Metric", 118], ["Samples", 50], ["Minimum", 82], ["Maximum", 82], ["Average", 90], ["Latest", 94]];
  const drawHeader = () => { let x = 48; page.rect(48, page.y - 25, 516, 25, COLORS.navy); columns.forEach(([label, width]) => { page.text(label, x + 7, page.y - 16, 6.8, COLORS.white); x += width; }); page.y -= 25; };
  drawHeader(); metrics.forEach(([key, metric], index) => { if (!page.ensure(26)) { page = nextPage(page, pages); page.section("Telemetry statistics"); drawHeader(); } const rowY = page.y; page.rect(48, rowY - 26, 516, 26, index % 2 ? COLORS.pale : COLORS.white, COLORS.line); const values = [metricLabel(report, key), String(metric.count ?? 0), metricValue(metric, "minimum"), metricValue(metric, "maximum"), metricValue(metric, "average"), metricValue(metric, "latest")]; let x = 48; values.forEach((value, valueIndex) => { page.wrapped(value, x + 7, rowY - 17, columns[valueIndex][1] - 12, 7.2, valueIndex === 0 ? COLORS.navy : COLORS.slate, 8); x += columns[valueIndex][1]; }); page.y -= 26; }); page.y -= 15; return page;
}

function trendTable(page, report, pages) {
  const trend = Array.isArray(report.telemetryTrend) ? report.telemetryTrend : []; const keys = trendKeys(report); if (!trend.length || !keys.length) { if (!page.section("Trend data", "Persisted bucket averages; blank cells indicate unavailable samples")) return page; panelText(page, "Telemetry trend data is unavailable for this reporting period."); return page; }
  if (!page.ensure(90)) page = nextPage(page, pages);
  if (!page.section("Trend data", "Persisted bucket averages; blank cells indicate unavailable samples")) return page;
  const columns = [["Time", 72], ...keys.map((key) => [metricLabel(report, key), Math.max(62, Math.floor(444 / keys.length))])]; const totalWidth = columns.reduce((sum, column) => sum + column[1], 0); const includeDate = !trend.every((point) => sameDay(trend[0].bucketStart, point.bucketStart));
  const drawHeader = () => { page.rect(48, page.y - 24, totalWidth, 24, COLORS.navy); let x = 48; columns.forEach(([label, width]) => { page.wrapped(label, x + 5, page.y - 10, width - 8, 6.2, COLORS.white, 7); x += width; }); page.y -= 24; };
  drawHeader(); trend.forEach((point, index) => { if (!page.ensure(24)) { page = nextPage(page, pages); page.section("Trend data"); drawHeader(); } const rowY = page.y; page.rect(48, rowY - 24, totalWidth, 24, index % 2 ? COLORS.pale : COLORS.white, COLORS.line); const values = [shortTime(point.bucketStart, includeDate), ...keys.map((key) => point.metrics?.[key] === undefined ? "" : metricValue({ latest: point.metrics[key], unit: report.telemetrySummary?.[key]?.unit }, "latest"))]; let x = 48; values.forEach((value, valueIndex) => { page.wrapped(value, x + 5, rowY - 16, columns[valueIndex][1] - 8, 6.8, COLORS.slate, 8); x += columns[valueIndex][1]; }); page.y -= 24; }); page.y -= 15; return page;
}

function alertInsights(page, report) {
  if (!page.section("Alert insights")) return;
  const entries = [["TOTAL ALERTS", String(report.alertCount ?? 0), COLORS.blue], ...Object.entries(report.severityCounts || {}).map(([key, value]) => [key, String(value), key === "CRITICAL" ? COLORS.red : key === "WARNING" ? COLORS.amber : COLORS.blue]), ...Object.entries(report.statusCounts || {}).map(([key, value]) => [key, String(value), key === "RESOLVED" ? COLORS.green : key === "ACKNOWLEDGED" ? COLORS.blue : COLORS.red])];
  if (entries.length === 1 && entries[0][1] === "0") { panelText(page, "No alerts were recorded during this reporting period."); return; }
  const columns = Math.min(4, entries.length); const gap = 9; const width = (516 - gap * (columns - 1)) / columns; entries.forEach(([label, value, accent], index) => { const x = 48 + (index % columns) * (width + gap); if (index > 0 && index % columns === 0) page.y -= 9; const top = page.y; page.rect(x, top - 47, width, 47, COLORS.pale, COLORS.line); page.rect(x, top - 47, 3, 47, accent); page.text(label, x + 11, top - 16, 6.3, COLORS.muted); page.text(value, x + 11, top - 35, 12, COLORS.navy); if (index % columns === columns - 1 || index === entries.length - 1) page.y = top - 62; });
}

function createPages(report) {
  const pages = []; let page = new PdfPage(1); page.rect(0, 688, 612, 104, COLORS.navy); page.rect(0, 684, 612, 4, COLORS.cyan); page.text("NexaIoT", 48, 754, 25, COLORS.white); page.text("Smart IoT Management Platform", 49, 735, 8.5, [0.73, 0.86, 0.90]); page.text("DEVICE REPORT", 390, 755, 9, [0.45, 0.90, 0.96]); page.wrapped(report.deviceName || "Selected device", 390, 738, 170, 12, COLORS.white, 14); page.text("Operational telemetry & alert intelligence", 390, 712, 7, [0.73, 0.86, 0.90]); page.text(`Generated ${date(report.generatedAt)}`, 48, 697, 7.5, [0.73, 0.86, 0.90]); page.y = 660;
  overview(page, report); deviceOverview(page, report); page = telemetryGlance(page, report, pages); page = telemetryCharts(page, report, pages); page = telemetryTable(page, report, pages); page = trendTable(page, report, pages); if (!page.ensure(95)) page = nextPage(page, pages); alertInsights(page, report); page.footer(); pages.push(page); return pages;
}

export function buildReportPdf(report) {
  const pages = createPages(report); const objects = []; objects[1] = "<< /Type /Catalog /Pages 2 0 R >>"; objects[2] = `<< /Type /Pages /Kids [${pages.map((_, index) => `${4 + index * 2} 0 R`).join(" ")}] /Count ${pages.length} >>`; objects[3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>";
  pages.forEach((page, index) => { const pageObject = 4 + index * 2; const contentObject = pageObject + 1; const content = `q\n${page.commands.join("\n")}\nQ`; objects[pageObject] = `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents ${contentObject} 0 R >>`; objects[contentObject] = `<< /Length ${new TextEncoder().encode(content).length} >>\nstream\n${content}\nendstream`; });
  let pdf = "%PDF-1.4\n%\xE2\xE3\xCF\xD3\n"; const offsets = [0]; for (let index = 1; index < objects.length; index += 1) { offsets[index] = new TextEncoder().encode(pdf).length; pdf += `${index} 0 obj\n${objects[index]}\nendobj\n`; } const xref = new TextEncoder().encode(pdf).length; pdf += `xref\n0 ${objects.length}\n0000000000 65535 f \n${offsets.slice(1).map((offset) => `${String(offset).padStart(10, "0")} 00000 n `).join("\n")}\ntrailer\n<< /Size ${objects.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`; return new Blob([pdf], { type: "application/pdf" });
}

export function downloadReportPdf(report, filename) { const url = URL.createObjectURL(buildReportPdf(report)); const link = document.createElement("a"); link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url); }
export function reportFilename(report) { const device = String(report.deviceName || "device").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 60) || "device"; return `nexaiot-report-${device}-${report.range || "range"}-${new Date().toISOString().slice(0, 10)}.pdf`; }
