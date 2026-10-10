import { useState } from "react";
import { Pencil, Plus, Trash2, X } from "lucide-react";

const categories = [
  ["MEASUREMENT", "Measurement"],
  ["STATE", "State"],
  ["DEVICE_METRIC", "Device metric"],
  ["CONTROL", "Control"],
];

const dataTypes = [
  ["NUMBER", "Number"],
  ["BOOLEAN", "Boolean"],
  ["STRING", "String"],
  ["ENUM", "Enum"],
];

const controlTypes = {
  BOOLEAN: [["SWITCH", "Switch"]],
};

function createDraft() {
  return {
    name: "",
    key: "digitalOutput",
    category: "MEASUREMENT",
    dataType: "NUMBER",
    unit: "",
    semanticType: "",
    description: "",
    valueKind: "GAUGE",
    readOnly: true,
    chartable: true,
    controlType: "",
    min: "",
    max: "",
    step: "",
    options: [{ label: "", value: "" }],
  };
}

function toCapabilityKey(name) {
  const words = name
    .trim()
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean);

  return words
    .map((word, index) => {
      const lower = word.toLowerCase();
      return index === 0 ? lower : lower.charAt(0).toUpperCase() + lower.slice(1);
    })
    .join("");
}

function labelFor(values, value) {
  return values.find(([optionValue]) => optionValue === value)?.[1] || value;
}

function controlOptions(dataType, category) {
  if (category === "CONTROL") return [["SWITCH", "Switch"]];
  return controlTypes[dataType] || [];
}

function draftFromCapability(capability) {
  return {
    ...createDraft(),
    name: capability.category === "CONTROL"
      ? (capability.key === "power" ? "Power" : "Digital Output")
      : capability.name || "",
    key: capability.category === "CONTROL" && capability.key === "power"
      ? "power"
      : "digitalOutput",
    category: capability.category || "MEASUREMENT",
    dataType: capability.category === "CONTROL" ? "BOOLEAN" : capability.dataType || "NUMBER",
    unit: capability.unit || "",
    semanticType: capability.category === "CONTROL"
      ? (capability.key === "power" ? "power" : "digitalOutput")
      : capability.semanticType || "",
    description: capability.description || "",
    valueKind: capability.valueKind || "GAUGE",
    readOnly: capability.category === "CONTROL" ? false : capability.readOnly !== false,
    chartable: capability.category === "CONTROL" ? false : capability.chartable !== false,
    controlType: capability.category === "CONTROL" ? "SWITCH" : capability.control?.type || "",
    min: capability.constraints?.min ?? "",
    max: capability.constraints?.max ?? "",
    step: capability.constraints?.step ?? "",
    options: capability.options?.length
      ? capability.options.map((option) => ({
          label: option.label || "",
          value: option.value || "",
        }))
      : [{ label: "", value: "" }],
  };
}

function formatCapability(capability) {
  return [
    labelFor(categories, capability.category),
    capability.dataType,
    capability.control?.type ? labelFor(controlOptions(capability.dataType, capability.category), capability.control.type) : null,
    capability.unit || null,
  ].filter(Boolean).join(" · ");
}

export default function CustomCapabilityBuilder({ capabilities, onChange }) {
  const [draft, setDraft] = useState(createDraft);
  const [editingIndex, setEditingIndex] = useState(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [validationError, setValidationError] = useState("");

  const openNew = () => {
    setDraft(createDraft());
    setEditingIndex(null);
    setValidationError("");
    setEditorOpen(true);
  };

  const openEdit = (index) => {
    setDraft(draftFromCapability(capabilities[index]));
    setEditingIndex(index);
    setValidationError("");
    setEditorOpen(true);
  };

  const closeEditor = () => {
    setEditorOpen(false);
    setValidationError("");
  };

  const updateDraft = (field, value) => {
    setDraft((current) => ({ ...current, [field]: value }));
  };

  const changeCategory = (category) => {
    setDraft((current) => ({
      ...current,
      category,
      readOnly: category === "CONTROL" ? false : true,
      chartable: category === "CONTROL" ? false : true,
      name: category === "CONTROL" ? "Digital Output" : current.name,
      key: category === "CONTROL" ? "digitalOutput" : current.key,
      semanticType: category === "CONTROL" ? "digitalOutput" : current.semanticType,
      dataType: category === "CONTROL" ? "BOOLEAN" : current.dataType,
      controlType: category === "CONTROL" ? "SWITCH" : "",
      valueKind: category === "CONTROL" || current.dataType !== "NUMBER" ? "" : "GAUGE",
    }));
  };

  const changeDataType = (dataType) => {
    setDraft((current) => ({
      ...current,
      dataType: current.category === "CONTROL" ? "BOOLEAN" : dataType,
      valueKind: current.category === "CONTROL" || dataType !== "NUMBER" ? "" : "GAUGE",
      controlType: current.category === "CONTROL" ? "SWITCH" : "",
      options: dataType === "ENUM" ? current.options : [{ label: "", value: "" }],
    }));
  };

  const validateDraft = () => {
    const key = draft.category === "CONTROL" ? draft.key : toCapabilityKey(draft.name);
    if (!draft.name.trim()) return "Enter a capability name.";
    if (!key) return "Enter a capability name using letters or numbers.";
    if (capabilities.some((capability, index) => capability.key === key && index !== editingIndex)) {
      return `The generated key "${key}" is already used. Choose a different name.`;
    }

    if (draft.category === "CONTROL"
      && (draft.dataType !== "BOOLEAN" || draft.controlType !== "SWITCH")) {
      return "V1 supports only Digital Output / Power switch controls.";
    }

    if (draft.dataType === "ENUM") {
      const options = draft.options.map((option) => ({
        label: option.label.trim(),
        value: option.value.trim(),
      }));
      if (options.length === 0 || options.some((option) => !option.label || !option.value)) {
        return "Add at least one enum option with a label and value.";
      }
      if (new Set(options.map((option) => option.value)).size !== options.length) {
        return "Enum option values must be unique.";
      }
    }

    return "";
  };

  const saveCapability = () => {
    const error = validateDraft();
    if (error) {
      setValidationError(error);
      return;
    }

    const key = draft.category === "CONTROL" ? draft.key : toCapabilityKey(draft.name);
    const capability = {
      key,
      name: draft.category === "CONTROL"
        ? (draft.key === "power" ? "Power" : "Digital Output")
        : draft.name.trim(),
      category: draft.category,
      dataType: draft.category === "CONTROL" ? "BOOLEAN" : draft.dataType,
      readOnly: draft.category === "CONTROL" ? false : draft.readOnly,
      chartable: draft.category === "CONTROL" ? false : draft.chartable,
    };

    ["unit", "semanticType", "description"].forEach((field) => {
      if (draft.category !== "CONTROL" && draft[field].trim()) capability[field] = draft[field].trim();
    });

    if (draft.category !== "CONTROL" && draft.dataType === "NUMBER") {
      capability.valueKind = draft.valueKind || "GAUGE";
    }

    if (draft.category === "CONTROL") {
      capability.semanticType = draft.key === "power" ? "power" : "digitalOutput";
      capability.control = { type: "SWITCH" };
    }

    if (draft.dataType === "ENUM") {
      capability.options = draft.options.map((option) => ({
        label: option.label.trim(),
        value: option.value.trim(),
      }));
    }

    const nextCapabilities = [...capabilities];
    if (editingIndex === null) nextCapabilities.push(capability);
    else nextCapabilities[editingIndex] = capability;
    onChange(nextCapabilities);
    closeEditor();
  };

  const updateOption = (index, field, value) => {
    setDraft((current) => ({
      ...current,
      options: current.options.map((option, optionIndex) => (
        optionIndex === index ? { ...option, [field]: value } : option
      )),
    }));
  };

  return (
    <section className="rounded-[14px] border border-[#dce8ee] bg-white p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-[15px] font-semibold text-[#102a3a]">Custom capabilities</h2>
          <p className="mt-1 text-[10px] text-[#6b8290]">
            Define the measurements, states, and controls supported by this device.
          </p>
        </div>
        <button
          type="button"
          onClick={openNew}
          className="inline-flex h-9 items-center justify-center gap-1.5 rounded-[9px] bg-[#08a9c4] px-3.5 text-[10px] font-medium text-white transition hover:bg-[#0799b2]"
        >
          <Plus size={14} /> Add capability
        </button>
      </div>

      {capabilities.length > 0 ? (
        <div className="mt-4 space-y-2">
          {capabilities.map((capability, index) => (
            <div key={capability.key} className="flex items-center gap-3 rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] p-3">
              <div className="min-w-0 flex-1">
                <p className="truncate text-[11px] font-semibold text-[#102a3a]">{capability.name}</p>
                <p className="mt-1 truncate text-[9px] text-[#6b8290]">{formatCapability(capability)}</p>
              </div>
              <button type="button" onClick={() => openEdit(index)} aria-label={`Edit ${capability.name}`} className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] text-[#6b8290] transition hover:bg-white hover:text-[#08a9c4]"><Pencil size={14} /></button>
              <button type="button" onClick={() => onChange(capabilities.filter((_, capabilityIndex) => capabilityIndex !== index))} aria-label={`Remove ${capability.name}`} className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] text-[#b35b64] transition hover:bg-white hover:text-[#d83f4d]"><Trash2 size={14} /></button>
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-4 rounded-[10px] border border-dashed border-[#dce8ee] bg-[#f8fbfd] p-5 text-center text-[10px] text-[#6b8290]">
          Add at least one capability to describe this custom device.
        </div>
      )}

      {editorOpen ? (
        <div className="mt-4 rounded-[12px] border border-[#b9dfe6] bg-[#eef9fb] p-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 className="text-[12px] font-semibold text-[#102a3a]">{editingIndex === null ? "Add capability" : "Edit capability"}</h3>
              <p className="mt-1 text-[9px] text-[#6b8290]">The capability key is generated from the name.</p>
            </div>
            <button type="button" onClick={closeEditor} aria-label="Close capability editor" className="text-[#6b8290] hover:text-[#102a3a]"><X size={16} /></button>
          </div>

          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="text-[9px] font-medium text-[#526b79]">Name<input value={draft.name} onChange={(event) => updateDraft("name", event.target.value)} disabled={draft.category === "CONTROL"} placeholder="e.g. Water Level" className="mt-1.5 h-9 w-full rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[11px] text-[#102a3a] outline-none focus:border-[#08a9c4] disabled:bg-[#f8fbfd] disabled:text-[#6b8290]" /></label>
            <label className="text-[9px] font-medium text-[#526b79]">Generated key<div className="mt-1.5 flex h-9 items-center rounded-[9px] border border-[#dce8ee] bg-[#f8fbfd] px-3 font-mono text-[10px] text-[#6b8290]">{draft.category === "CONTROL" ? draft.key : toCapabilityKey(draft.name) || "—"}</div></label>
            <label className="text-[9px] font-medium text-[#526b79]">Category<select value={draft.category} onChange={(event) => changeCategory(event.target.value)} className="mt-1.5 h-9 w-full rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[11px] text-[#102a3a] outline-none focus:border-[#08a9c4]">{categories.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            <label className="text-[9px] font-medium text-[#526b79]">Data type<select value={draft.dataType} onChange={(event) => changeDataType(event.target.value)} disabled={draft.category === "CONTROL"} className="mt-1.5 h-9 w-full rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[11px] text-[#102a3a] outline-none focus:border-[#08a9c4] disabled:bg-[#f8fbfd] disabled:text-[#6b8290]">{(draft.category === "CONTROL" ? [["BOOLEAN", "Boolean"]] : dataTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            <label className="text-[9px] font-medium text-[#526b79]">Unit <span className="font-normal text-[#9aadb6]">(optional)</span><input value={draft.unit} onChange={(event) => updateDraft("unit", event.target.value)} placeholder="e.g. % or °C" className="mt-1.5 h-9 w-full rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[11px] text-[#102a3a] outline-none focus:border-[#08a9c4]" /></label>
            <label className="text-[9px] font-medium text-[#526b79]">Semantic type <span className="font-normal text-[#9aadb6]">(optional)</span><input value={draft.semanticType} onChange={(event) => updateDraft("semanticType", event.target.value)} placeholder="e.g. waterLevel" className="mt-1.5 w-full rounded-[9px] border border-[#dce8ee] bg-white px-3 py-2 text-[11px] text-[#102a3a] outline-none focus:border-[#08a9c4]" /></label>
          </div>

          {draft.category !== "CONTROL" && draft.dataType === "NUMBER" ? (
            <div className="mt-3 rounded-[9px] border border-[#dce8ee] bg-white p-3">
              <p className="text-[9px] font-semibold uppercase tracking-[0.06em] text-[#6b8290]">Numeric behavior</p>
              <div className="mt-2 flex flex-wrap gap-4 text-[10px] text-[#526b79]">
                {[["GAUGE", "Gauge", "Current value such as temperature or voltage"], ["COUNTER", "Counter", "Accumulated value such as total energy"]].map(([value, label, description]) => <label key={value} className="flex items-center gap-2" title={description}><input type="radio" name="custom-value-kind" checked={draft.valueKind === value} onChange={() => updateDraft("valueKind", value)} className="accent-[#08a9c4]" />{label}</label>)}
              </div>
            </div>
          ) : null}

          {draft.category === "CONTROL" ? (
            <div className="mt-3 rounded-[9px] border border-[#dce8ee] bg-white p-3">
              <label className="text-[9px] font-medium text-[#526b79]">Control type<select value={draft.controlType} disabled className="mt-1.5 h-9 w-full rounded-[9px] border border-[#dce8ee] bg-[#f8fbfd] px-3 text-[11px] text-[#6b8290] outline-none">{controlOptions(draft.dataType, draft.category).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            </div>
          ) : null}

          {draft.dataType === "ENUM" ? (
            <div className="mt-3 rounded-[9px] border border-[#dce8ee] bg-white p-3">
              <div className="flex items-center justify-between"><p className="text-[9px] font-semibold uppercase tracking-[0.06em] text-[#6b8290]">Enum options</p><button type="button" onClick={() => setDraft((current) => ({ ...current, options: [...current.options, { label: "", value: "" }] }))} className="text-[9px] font-medium text-[#087b9c]">+ Add option</button></div>
              <div className="mt-2 space-y-2">{draft.options.map((option, index) => <div key={`${index}-${option.value}`} className="flex gap-2"><input value={option.label} onChange={(event) => updateOption(index, "label", event.target.value)} placeholder="Label" className="h-8 min-w-0 flex-1 rounded-[8px] border border-[#dce8ee] px-2 text-[10px] outline-none focus:border-[#08a9c4]" /><input value={option.value} onChange={(event) => updateOption(index, "value", event.target.value)} placeholder="Value" className="h-8 min-w-0 flex-1 rounded-[8px] border border-[#dce8ee] px-2 font-mono text-[10px] outline-none focus:border-[#08a9c4]" /><button type="button" disabled={draft.options.length === 1} onClick={() => setDraft((current) => ({ ...current, options: current.options.filter((_, optionIndex) => optionIndex !== index) }))} aria-label="Remove enum option" className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] text-[#b35b64] disabled:cursor-not-allowed disabled:opacity-30"><Trash2 size={13} /></button></div>)}</div>
            </div>
          ) : null}

          <label className="mt-3 block text-[9px] font-medium text-[#526b79]">Description <span className="font-normal text-[#9aadb6]">(optional)</span><textarea value={draft.description} onChange={(event) => updateDraft("description", event.target.value)} rows={2} className="mt-1.5 w-full rounded-[9px] border border-[#dce8ee] bg-white px-3 py-2 text-[10px] text-[#102a3a] outline-none focus:border-[#08a9c4]" /></label>

          {draft.category !== "CONTROL" ? <div className="mt-3 flex flex-wrap gap-4 text-[10px] text-[#526b79]"><label className="flex items-center gap-2"><input type="checkbox" checked={draft.readOnly} onChange={(event) => updateDraft("readOnly", event.target.checked)} className="accent-[#08a9c4]" />Read only</label><label className="flex items-center gap-2"><input type="checkbox" checked={draft.chartable} onChange={(event) => updateDraft("chartable", event.target.checked)} className="accent-[#08a9c4]" />Available in charts</label></div> : <p className="mt-3 text-[9px] text-[#6b8290]">Controls are writable and are excluded from charts.</p>}

          {validationError ? <p className="mt-3 rounded-[8px] bg-red-50 px-3 py-2 text-[10px] text-red-600">{validationError}</p> : null}
          <div className="mt-4 flex justify-end gap-2"><button type="button" onClick={closeEditor} className="h-9 rounded-[9px] border border-[#dce8ee] bg-white px-3.5 text-[10px] text-[#526b79]">Cancel</button><button type="button" onClick={saveCapability} className="h-9 rounded-[9px] bg-[#08a9c4] px-3.5 text-[10px] font-medium text-white">{editingIndex === null ? "Add capability" : "Save capability"}</button></div>
        </div>
      ) : null}
    </section>
  );
}
