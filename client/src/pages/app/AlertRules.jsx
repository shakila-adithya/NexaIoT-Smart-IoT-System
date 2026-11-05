import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, Check, Pencil, Plus, RefreshCw, SlidersHorizontal, Trash2 } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { createAlertRule, deleteAlertRule, getAlertRules, updateAlertRule } from "../../api/alertRulesApi.js";
import { getDevices } from "../../api/devicesApi.js";
import { normalizeCapabilityManifest } from "../../config/capabilityManifest.js";
import { useToast } from "../../hooks/useToast.js";
import Button from "../../components/ui/Button.jsx";
import PageHeader from "../../components/ui/PageHeader.jsx";
import SectionCard from "../../components/ui/SectionCard.jsx";
import Select from "../../components/ui/Select.jsx";
import { validateAutomationRule } from "../../config/automationValidation.js";

const emptyForm = {
  name: "",
  deviceId: "",
  ruleType: "METRIC_THRESHOLD",
  metricKey: "",
  operator: "GREATER_THAN",
  threshold: "",
  offlineDurationMinutes: "5",
  severity: "WARNING",
  enabled: true,
};

const operatorLabels = {
  GREATER_THAN: ">",
  GREATER_THAN_OR_EQUAL: "≥",
  LESS_THAN: "<",
  LESS_THAN_OR_EQUAL: "≤",
};

function deviceName(devices, id) {
  return devices.find((device) => device.id === id)?.name || "Unknown device";
}

function metricCapabilities(device) {
  return normalizeCapabilityManifest(device).filter((capability) => capability.category !== "CONTROL" && capability.dataType === "NUMBER");
}

function metricDisplay(devices, rule) {
  const capability = metricCapabilities(devices.find((device) => device.id === rule.deviceId))
    .find((item) => item.key === rule.metricKey);
  const label = capability?.name || capability?.label || rule.metricKey;
  const unit = capability?.unit ? ` ${capability.unit}` : "";
  return `${label} ${operatorLabels[rule.operator] || rule.operator} ${rule.threshold}${unit}`;
}

function formFromRule(rule) {
  return {
    name: rule.name || "",
    deviceId: rule.deviceId || "",
    ruleType: rule.ruleType || "METRIC_THRESHOLD",
    metricKey: rule.metricKey || "",
    operator: rule.operator || "GREATER_THAN",
    threshold: rule.threshold ?? "",
    offlineDurationMinutes: rule.offlineDurationMinutes ?? "5",
    severity: rule.severity || "WARNING",
    enabled: rule.enabled !== false,
  };
}

export default function AlertRules() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [devices, setDevices] = useState([]);
  const [rules, setRules] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState("");
  const [updatingId, setUpdatingId] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    let mounted = true;
    Promise.all([getDevices(), getAlertRules()])
      .then(([loadedDevices, loadedRules]) => {
        if (!mounted) return;
        const safeDevices = Array.isArray(loadedDevices) ? loadedDevices : [];
        setDevices(safeDevices);
        setRules(Array.isArray(loadedRules) ? loadedRules : []);
        setForm((current) => current.deviceId || !safeDevices[0] ? current : { ...current, deviceId: safeDevices[0].id });
      })
      .catch((requestError) => mounted && setError(requestError.message || "Unable to load alert rules."))
      .finally(() => mounted && setLoading(false));
    return () => { mounted = false; };
  }, [refreshKey]);

  const selectedDevice = devices.find((device) => device.id === form.deviceId);
  const metrics = useMemo(() => metricCapabilities(selectedDevice), [selectedDevice]);

  function updateField(event) {
    const { name, value, type, checked } = event.target;
    setForm((current) => ({ ...current, [name]: type === "checkbox" ? checked : value }));
  }

  function startCreate() {
    setEditingId("");
    setError("");
    setForm({ ...emptyForm, deviceId: devices[0]?.id || "", metricKey: metricCapabilities(devices[0])[0]?.key || "" });
  }

  function startEdit(rule) {
    setEditingId(rule.id);
    setError("");
    setForm(formFromRule(rule));
  }

  async function saveRule(event) {
    event.preventDefault();
    const validationError = validateAutomationRule(form, metrics);
    if (validationError) { setError(validationError); return; }
    const payload = {
      name: form.name.trim(),
      deviceId: form.deviceId,
      ruleType: form.ruleType,
      severity: form.severity,
      enabled: form.enabled,
      ...(form.ruleType === "METRIC_THRESHOLD"
        ? { metricKey: form.metricKey, operator: form.operator, threshold: Number(String(form.threshold).trim()) }
        : { offlineDurationMinutes: Number(String(form.offlineDurationMinutes).trim()) }),
    };
    try {
      setSaving(true);
      setError("");
      const saved = editingId ? await updateAlertRule(editingId, payload) : await createAlertRule(payload);
      setRules((current) => editingId ? current.map((rule) => rule.id === saved.id ? saved : rule) : [saved, ...current]);
      setEditingId("");
      setForm({ ...emptyForm, deviceId: devices[0]?.id || "", metricKey: metricCapabilities(devices[0])[0]?.key || "" });
      showToast(editingId ? "Alert rule updated." : "Alert rule created.");
    } catch (requestError) {
      setError(requestError.message || "Unable to save the alert rule.");
    } finally {
      setSaving(false);
    }
  }

  async function toggleRule(rule) {
    if (updatingId) return;
    try {
      setUpdatingId(rule.id);
      setError("");
      const saved = await updateAlertRule(rule.id, { ...formFromRule(rule), enabled: !rule.enabled });
      setRules((current) => current.map((item) => item.id === saved.id ? saved : item));
      showToast(saved.enabled ? "Alert rule enabled." : "Alert rule disabled.");
    } catch (requestError) {
      setError(requestError.message || "Unable to update the alert rule.");
    } finally {
      setUpdatingId("");
    }
  }

  async function removeRule(rule) {
    if (!window.confirm(`Delete alert rule “${rule.name}”?`)) return;
    try {
      setDeletingId(rule.id);
      await deleteAlertRule(rule.id);
      setRules((current) => current.filter((item) => item.id !== rule.id));
      if (editingId === rule.id) startCreate();
      showToast("Alert rule deleted.");
    } catch (requestError) {
      setError(requestError.message || "Unable to delete the alert rule.");
    } finally {
      setDeletingId("");
    }
  }

  return (
    <div className="w-full px-4 pb-10 pt-7 md:px-8 lg:px-[30px]">
      <div className="mx-auto w-full max-w-[1240px]">
        <PageHeader
          tone="automation"
          breadcrumb={<button type="button" onClick={() => navigate("/alerts")} className="inline-flex items-center gap-1.5 font-medium hover:text-[var(--color-primary)]"><ArrowLeft size={13} /> Back to alerts</button>}
          title="Alert rules"
          description="Create device-specific thresholds that generate real alerts."
          actions={<><Button type="button" variant="secondary" onClick={() => setRefreshKey((current) => current + 1)} loading={loading} icon={RefreshCw}>Refresh</Button><Button type="button" onClick={startCreate} icon={Plus}>New rule</Button></>}
        />

        {error ? <div className="mt-5 rounded-[12px] border border-red-200 bg-red-50 p-4 text-[11px] text-red-600">{error}</div> : null}

        <div className="mt-6 grid items-start gap-4 lg:grid-cols-[minmax(0,1.35fr)_minmax(330px,0.8fr)]">
          <SectionCard className="p-0">
            <div className="flex items-center justify-between border-b border-[#edf3f6] px-4 py-3.5"><div><h2 className="topic-heading topic-maintenance text-[13px] font-semibold text-[#102a3a]">Your rules</h2><p className="mt-1 text-[10px] text-[#6b8290]">Rules stay scoped to their selected device.</p></div><SlidersHorizontal size={18} className="text-[var(--topic-maintenance)]" /></div>
            {loading ? <div className="p-5 text-[11px] text-[#6b8290]">Loading rules...</div> : rules.length === 0 ? <div className="p-10 text-center"><SlidersHorizontal size={25} className="mx-auto text-[#9bb0bb]" /><p className="mt-3 text-[12px] font-semibold text-[#102a3a]">No alert rules yet.</p><p className="mt-1 text-[10px] text-[#6b8290]">Create a rule to monitor a device capability.</p></div> : <div className="divide-y divide-[#edf3f6]">{rules.map((rule) => <div key={rule.id} className="p-4"><div className="flex items-start justify-between gap-3"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h3 className="text-[12px] font-semibold text-[#102a3a]">{rule.name}</h3><span className={`rounded-full px-2 py-0.5 text-[9px] font-semibold ${rule.enabled ? "bg-[#e8f8f2] text-[#16815f]" : "bg-[#f4f8fb] text-[#8ba0ab]"}`}>{rule.enabled ? "Enabled" : "Disabled"}</span><span className="rounded-full bg-[#fff5df] px-2 py-0.5 text-[9px] font-semibold text-[#b97805]">{rule.severity}</span></div><p className="mt-1 text-[10px] text-[#6b8290]">{deviceName(devices, rule.deviceId)}</p><p className="mt-2 text-[11px] font-medium text-[#102a3a]">{rule.ruleType === "DEVICE_OFFLINE" ? `Offline for ${rule.offlineDurationMinutes} min` : metricDisplay(devices, rule)}</p></div><div className="flex shrink-0 items-center gap-1"><button type="button" onClick={() => toggleRule(rule)} className="rounded-[8px] px-2 py-1.5 text-[10px] font-medium text-[#087f98] hover:bg-[#eef9fb]">{rule.enabled ? "Disable" : "Enable"}</button><button type="button" onClick={() => startEdit(rule)} className="rounded-[8px] p-1.5 text-[#6b8290] hover:bg-[#f4f8fb]" aria-label="Edit rule"><Pencil size={14} /></button><button type="button" disabled={deletingId === rule.id} onClick={() => removeRule(rule)} className="rounded-[8px] p-1.5 text-[#d83f4d] hover:bg-[#fdecee]" aria-label="Delete rule"><Trash2 size={14} /></button></div></div></div>)}</div>}
          </SectionCard>

          <SectionCard className="p-4">
            <div className="mb-4"><h2 className="text-[13px] font-semibold text-[#102a3a]">{editingId ? "Edit alert rule" : "Create alert rule"}</h2><p className="mt-1 text-[10px] text-[#6b8290]">Enabled rules are evaluated against incoming telemetry.</p></div>
            <form onSubmit={saveRule} className="space-y-3">
              <label className="block text-[10px] font-medium text-[#526b79]">Rule name<input name="name" value={form.name} onChange={updateField} maxLength={100} className="mt-1 h-9 w-full rounded-[9px] border border-[#dce8ee] px-3 text-[11px] outline-none focus:border-[#08a9c4]" placeholder="Temperature too high" /></label>
              <Select name="deviceId" label="Device" value={form.deviceId} onChange={(value) => setForm((current) => ({ ...current, deviceId: value, metricKey: metricCapabilities(devices.find((device) => device.id === value))[0]?.key || "" }))} placeholder="Select a device" options={devices.map((device) => ({ value: device.id, label: device.name }))} />
              <Select name="ruleType" label="Rule type" value={form.ruleType} onChange={(value) => updateField({ target: { name: "ruleType", value } })} options={[{ value: "METRIC_THRESHOLD", label: "Metric threshold" }, { value: "DEVICE_OFFLINE", label: "Device offline" }]} />
              {form.ruleType === "METRIC_THRESHOLD" ? <><Select name="metricKey" label="Metric" value={form.metricKey} onChange={(value) => updateField({ target: { name: "metricKey", value } })} placeholder="Select a numeric capability" options={metrics.map((metric) => ({ value: metric.key, label: `${metric.name || metric.key}${metric.unit ? ` · ${metric.unit}` : ""}` }))} /><div className="grid grid-cols-2 gap-2"><Select name="operator" label="Operator" value={form.operator} onChange={(value) => updateField({ target: { name: "operator", value } })} options={[{ value: "GREATER_THAN", label: "Greater than" }, { value: "GREATER_THAN_OR_EQUAL", label: "Greater than or equal" }, { value: "LESS_THAN", label: "Less than" }, { value: "LESS_THAN_OR_EQUAL", label: "Less than or equal" }]} /><label className="block text-[10px] font-medium text-[#526b79]">Threshold<input name="threshold" type="number" step="any" value={form.threshold} onChange={updateField} className="mt-1 h-9 w-full rounded-[9px] border border-[#dce8ee] px-3 text-[11px] outline-none focus:border-[#08a9c4]" /></label></div></> : <label className="block text-[10px] font-medium text-[#526b79]">Offline duration (minutes)<input name="offlineDurationMinutes" type="number" min="1" max="10080" value={form.offlineDurationMinutes} onChange={updateField} className="mt-1 h-9 w-full rounded-[9px] border border-[#dce8ee] px-3 text-[11px] outline-none focus:border-[#08a9c4]" /></label>}
              <Select name="severity" label="Severity" value={form.severity} onChange={(value) => updateField({ target: { name: "severity", value } })} options={[{ value: "INFO", label: "Info" }, { value: "WARNING", label: "Warning" }, { value: "CRITICAL", label: "Critical" }]} />
              <label className="flex items-center gap-2 pt-1 text-[10px] font-medium text-[#526b79]"><input type="checkbox" name="enabled" checked={form.enabled} onChange={updateField} className="accent-[#08a9c4]" /> Enabled</label>
              <div className="flex gap-2 pt-2"><button type="submit" disabled={saving || devices.length === 0} className="inline-flex h-9 flex-1 items-center justify-center gap-2 rounded-[9px] bg-[#08a9c4] text-[10px] font-semibold text-white hover:bg-[#0799b2] disabled:cursor-not-allowed disabled:opacity-50"><Check size={14} />{saving ? "Saving..." : editingId ? "Save changes" : "Create rule"}</button>{editingId ? <button type="button" onClick={startCreate} className="h-9 rounded-[9px] border border-[#dce8ee] px-3 text-[10px] font-semibold text-[#526b79]">Cancel</button> : null}</div>
            </form>
          </SectionCard>
        </div>
      </div>
    </div>
  );
}
