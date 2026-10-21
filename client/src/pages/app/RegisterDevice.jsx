import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Cable,
  Check,
  CircleCheck,
  Cpu,
  MapPin,
  RadioTower,
  Smartphone,
  Tag,
  Wifi,
  X,
} from "lucide-react";

import { createDevice } from "../../api/devicesApi.js";
import { getDeviceTemplates } from "../../api/deviceTemplatesApi.js";
import Button from "../../components/ui/Button.jsx";
import PageHeader from "../../components/ui/PageHeader.jsx";
import SectionCard from "../../components/ui/SectionCard.jsx";
import Select from "../../components/ui/Select.jsx";
import CustomCapabilityBuilder from "../../components/device/CustomCapabilityBuilder.jsx";
import TemplateCapabilityPreview from "../../components/device/TemplateCapabilityPreview.jsx";
import {
  DEVICE_CONNECTIVITY_PROTOCOL,
  formatConnectivityType,
} from "../../config/deviceConnectivity.js";

const connectivityOptions = [
  {
    id: "WIFI",
    label: "Wi-Fi",
    description: "Wireless network",
    icon: Wifi,
  },
  {
    id: "ETHERNET",
    label: "Ethernet",
    description: "Wired network",
    icon: Cable,
  },
  {
    id: "CELLULAR",
    label: "Cellular",
    description: "Mobile network",
    icon: Smartphone,
  },
];

function formatCapabilityLabel(capability) {
  const value = capability?.name || capability?.key || capability?.semanticType || "Capability";
  return value
    .replaceAll("_", " ")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

const capabilityChipStyles = [
  "bg-[#e8f8fb] text-[#087f98]",
  "bg-[#eaf8f3] text-[#16805f]",
  "bg-[#fff7e5] text-[#a56b00]",
  "bg-[#f1edff] text-[#7358d4]",
  "bg-[#eef4ff] text-[#416bc4]",
];

export default function RegisterDevice() {
  const navigate = useNavigate();

  const [form, setForm] = useState({
    name: "",
    type: "",
    location: "",
  });
  const [customCapabilities, setCustomCapabilities] = useState([]);
  const [registrationMode, setRegistrationMode] = useState("template");
  const registrationModeRef = useRef("template");
  const [templates, setTemplates] = useState([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState("");
  const [templatesLoading, setTemplatesLoading] = useState(true);
  const [templatesError, setTemplatesError] = useState("");

  const [connectivity, setConnectivity] = useState("WIFI");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let mounted = true;

    async function loadTemplates() {
      try {
        setTemplatesLoading(true);
        setTemplatesError("");

        const response = await getDeviceTemplates();
        if (!mounted) return;

        const loadedTemplates = Array.isArray(response) ? response : [];
        setTemplates(loadedTemplates);

        if (loadedTemplates.length > 0) {
          const firstTemplate = loadedTemplates[0];
          setSelectedTemplateId(firstTemplate.templateId);
          if (registrationModeRef.current === "template") {
            setForm((current) => ({
              ...current,
              type: firstTemplate.name || current.type,
            }));
          }
        } else {
          setTemplatesError("No device templates are available right now.");
        }
      } catch {
        if (!mounted) return;
        setTemplatesError("Unable to load device templates right now.");
      } finally {
        if (mounted) setTemplatesLoading(false);
      }
    }

    loadTemplates();

    return () => {
      mounted = false;
    };
  }, []);

  const selectedTemplate = templates.find(
    (template) => template.templateId === selectedTemplateId,
  );

  const handleChange = (event) => {
    const { name, value } = event.target;

    if (name === "type" && registrationMode === "template") return;

    setForm((current) => ({
      ...current,
      [name]: value,
    }));
  };

  const handleModeChange = (mode) => {
    registrationModeRef.current = mode;
    setRegistrationMode(mode);

    if (mode === "template" && selectedTemplate) {
      setForm((current) => ({
        ...current,
        type: selectedTemplate.name,
      }));
    } else if (mode === "manual") {
      setForm((current) => ({
        ...current,
        type: "",
      }));
    }
  };

  const handleTemplateChange = (templateId) => {
    const template = templates.find((item) => item.templateId === templateId);

    setSelectedTemplateId(templateId);
    if (template) {
      setForm((current) => ({
        ...current,
        type: template.name,
      }));
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    try {
      setSaving(true);
      setError("");

      const payload = {
        name: form.name.trim(),
        type: form.type.trim(),
        location: form.location.trim(),
        connectivity: {
          type: connectivity,
          protocol: DEVICE_CONNECTIVITY_PROTOCOL,
        },
      };

      if (registrationMode === "template") {
        if (!selectedTemplateId) {
          throw new Error("Select a device template before registering.");
        }
        payload.templateId = selectedTemplateId;
      } else {
        if (customCapabilities.length === 0) {
          throw new Error("Add at least one custom capability before registering.");
        }
        payload.capabilityManifest = {
          version: 1,
          capabilities: customCapabilities,
        };
      }

      await createDevice(payload);

      navigate("/devices");
    } catch (err) {
      setError(err.message || "Unable to register device.");
    } finally {
      setSaving(false);
    }
  };

  const completedFields = [
    Boolean(form.name.trim()),
    Boolean(form.type.trim()),
    Boolean(form.location.trim()),
  ];

  const previewCapabilities = registrationMode === "template"
    ? selectedTemplate?.capabilityManifest?.capabilities || []
    : customCapabilities;
  const visiblePreviewCapabilities = previewCapabilities.slice(0, 4);
  const remainingPreviewCapabilities = Math.max(0, previewCapabilities.length - visiblePreviewCapabilities.length);
  const previewType = form.type.trim() || (registrationMode === "manual" ? "Custom Device" : "Device type");
  const connectivityLabel = `${formatConnectivityType(connectivity)} · ${DEVICE_CONNECTIVITY_PROTOCOL}`;

  return (
    <div className="w-full px-4 pb-10 pt-7 md:px-8 lg:px-[30px]">
      <div className="mx-auto w-full max-w-[1240px]">
        <PageHeader
          tone="device"
          breadcrumb={<button type="button" onClick={() => navigate("/devices")} className="font-medium transition hover:text-[var(--color-primary)]">← Back to devices</button>}
          title="Register a new IoT device"
          description="Add device information, configure capabilities, and prepare monitoring settings."
          actions={<Button type="button" variant="secondary" onClick={() => navigate("/devices")} icon={X}>Cancel</Button>}
        />

        <SectionCard className="mt-6 p-4" compact>
          <div className="flex items-center">
            <div className="flex min-w-0 flex-1 items-center gap-2.5">
              <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[#08a9c4] text-[10px] font-bold text-white">
                1
              </div>

              <span className="whitespace-nowrap text-[10px] font-bold text-[#102a3a]">
                Device details
              </span>

              <div className="mx-2 h-px flex-1 bg-[#08a9c4]" />
            </div>

            <div className="flex min-w-0 flex-1 items-center gap-2.5">
              <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-[#dce8ee] bg-white text-[10px] font-bold text-[#6b8290]">
                2
              </div>

              <span className="whitespace-nowrap text-[10px] text-[#6b8290]">
                Connectivity
              </span>

              <div className="mx-2 h-px flex-1 bg-[#dce8ee]" />
            </div>

            <div className="flex items-center gap-2.5">
              <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-[#dce8ee] bg-white text-[10px] font-bold text-[#6b8290]">
                3
              </div>

              <span className="whitespace-nowrap text-[10px] text-[#6b8290]">
                Monitoring
              </span>
            </div>
          </div>
        </SectionCard>

        <div className="mt-6 grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_360px]">
          <form onSubmit={handleSubmit} className="space-y-4">
            <section className="rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-card)]">
              <div>
                <h2 className="text-[15px] font-semibold text-[#102a3a]">
                  Device details
                </h2>

                <p className="mt-1 text-[10px] text-[#6b8290]">
                  Enter the basic information used to identify this device.
                </p>
              </div>

              <div className="mt-5 grid gap-4 md:grid-cols-2">
                <div>
                  <label htmlFor="register-device-name" className="text-[10px] font-medium text-[#526b79]">
                    Device name
                  </label>

                  <div className="relative mt-2">
                    <Tag
                      size={15}
                      className="absolute left-3 top-1/2 -translate-y-1/2 text-[#9aaeba]"
                    />

                    <input
                      type="text"
                      id="register-device-name"
                      name="name"
                      value={form.name}
                      onChange={handleChange}
                      placeholder="e.g. Greenhouse Sensor"
                      className="h-10 w-full rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] pl-9 pr-3 text-[12px] text-[#102a3a] outline-none placeholder:text-[#9aaeba] focus:border-[#08a9c4] focus:ring-2 focus:ring-[#08a9c4]/10"
                      required
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="register-device-type" className="text-[10px] font-medium text-[#526b79]">
                    Device type
                  </label>

                  <div className="relative mt-2">
                    <Cpu
                      size={15}
                      className="absolute left-3 top-1/2 -translate-y-1/2 text-[#9aaeba]"
                    />

                    <input
                      type="text"
                      id="register-device-type"
                      name="type"
                      value={form.type}
                      onChange={handleChange}
                      placeholder="e.g. Temperature Sensor"
                      className="h-10 w-full rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] pl-9 pr-3 text-[12px] text-[#102a3a] outline-none placeholder:text-[#9aaeba] focus:border-[#08a9c4] focus:ring-2 focus:ring-[#08a9c4]/10"
                      required
                    />
                  </div>
                </div>

                <div className="md:col-span-2">
                  <label htmlFor="register-device-location" className="text-[10px] font-medium text-[#526b79]">
                    Location
                  </label>

                  <div className="relative mt-2">
                    <MapPin
                      size={15}
                      className="absolute left-3 top-1/2 -translate-y-1/2 text-[#9aaeba]"
                    />

                    <input
                      type="text"
                      id="register-device-location"
                      name="location"
                      value={form.location}
                      onChange={handleChange}
                      placeholder="e.g. Greenhouse 01"
                      className="h-10 w-full rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] pl-9 pr-3 text-[12px] text-[#102a3a] outline-none placeholder:text-[#9aaeba] focus:border-[#08a9c4] focus:ring-2 focus:ring-[#08a9c4]/10"
                      required
                    />
                  </div>
                </div>
              </div>
            </section>

            <section className="rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-card)]">
              <div>
                <h2 className="text-[15px] font-semibold text-[#102a3a]">
                  Registration mode
                </h2>
                <p className="mt-1 text-[10px] text-[#6b8290]">
                  Start with a built-in template or use the existing manual setup.
                </p>
              </div>

              <div className="mt-4 grid gap-2 sm:grid-cols-2">
                {[
                  ["template", "Use a Device Template", "Backend-defined capabilities"],
                  ["manual", "Configure Manually", "Legacy capability selection"],
                ].map(([mode, label, description]) => (
                  <button
                    key={mode}
                    type="button"
                    onClick={() => handleModeChange(mode)}
                    aria-pressed={registrationMode === mode}
                    className={`rounded-[10px] border px-3 py-3 text-left transition ${
                      registrationMode === mode
                        ? "border-[#08a9c4] bg-[#eef9fb]"
                        : "border-[#dce8ee] bg-[#f8fbfd] hover:border-[#b9d7df]"
                    }`}
                  >
                    <span className="text-[10px] font-semibold text-[#102a3a]">
                      {label}
                    </span>
                    <span className="mt-1 block text-[9px] text-[#6b8290]">
                      {description}
                    </span>
                  </button>
                ))}
              </div>
            </section>

            {registrationMode === "template" ? (
              <section className="rounded-[14px] border border-[#dce8ee] bg-white p-5">
                <div>
                  <h2 className="text-[15px] font-semibold text-[#102a3a]">
                    Device template
                  </h2>
                  <p className="mt-1 text-[10px] text-[#6b8290]">
                    Choose a built-in profile for this device.
                  </p>
                </div>

                {templatesLoading ? (
                  <div className="mt-4 h-10 animate-pulse rounded-[10px] bg-[#f1f6f8]" />
                ) : templatesError ? (
                  <div className="mt-4 rounded-[10px] border border-amber-200 bg-amber-50 p-3 text-[10px] text-amber-700">
                    {templatesError} You can switch to manual configuration.
                  </div>
                ) : templates.length === 0 ? (
                  <div className="mt-4 rounded-[10px] border border-dashed border-[#dce8ee] bg-[#f8fbfd] p-4 text-[10px] text-[#6b8290]">
                    No device templates are available. Switch to manual configuration.
                  </div>
                ) : (
                  <>
                    <div className="mt-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_160px]">
                      <Select value={selectedTemplateId} onChange={handleTemplateChange} aria-label="Device template" options={templates.map((template) => ({ value: template.templateId, label: template.name }))} buttonClassName="h-10 text-[11px]" />
                      <div className="flex items-center rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] px-3 text-[9px] text-[#6b8290]">
                        {selectedTemplate?.category || "Template"}
                      </div>
                    </div>

                    <TemplateCapabilityPreview template={selectedTemplate} />
                  </>
                )}
              </section>
            ) : (
              <CustomCapabilityBuilder
                capabilities={customCapabilities}
                onChange={setCustomCapabilities}
              />
            )}

            <section className="rounded-[14px] border border-[#dce8ee] bg-white p-5">
              <div>
                <h2 className="text-[15px] font-semibold text-[#102a3a]">
                  Connectivity
                </h2>

                <p className="mt-1 text-[10px] text-[#6b8290]">
                  Choose how this device reaches the network. NexaIoT uses MQTT as its device protocol.
                </p>
              </div>

              <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {connectivityOptions.map((option) => {
                  const Icon = option.icon;
                  const selected = connectivity === option.id;

                  return (
                    <button
                      type="button"
                      key={option.id}
                      onClick={() => setConnectivity(option.id)}
                      aria-pressed={selected}
                      className={`relative rounded-[12px] border p-3 text-left transition ${
                        selected
                          ? "border-[#08a9c4] bg-[#eef9fb]"
                          : "border-[#dce8ee] bg-white hover:border-[#b9d7df]"
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <Icon
                          size={18}
                          className={
                            selected
                              ? "text-[#08a9c4]"
                              : "text-[#6b8290]"
                          }
                        />

                        {selected ? (
                          <CircleCheck
                            size={16}
                            className="text-[#08a9c4]"
                          />
                        ) : null}
                      </div>

                      <div className="mt-3 text-[11px] font-semibold text-[#102a3a]">
                        {option.label}
                      </div>

                      <div className="mt-1 text-[9px] text-[#8397a2]">
                        {option.description}
                      </div>
                    </button>
                  );
                })}
              </div>

              <div className="mt-4 flex items-center gap-2 rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] px-3 py-2.5 text-[10px] text-[#526d7b]">
                <RadioTower size={14} className="text-[#08a9c4]" />
                <span>Protocol: <strong className="font-semibold text-[#102a3a]">{DEVICE_CONNECTIVITY_PROTOCOL}</strong></span>
              </div>

              <div className="mt-4 rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] px-4 py-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-8 w-8 items-center justify-center rounded-[9px] bg-[#e7f7f9] text-[#08a9c4]">
                    <RadioTower size={16} />
                  </div>

                  <div>
                    <p className="text-[11px] font-medium text-[#102a3a]">
                      Connectivity setup
                    </p>

                    <p className="mt-0.5 text-[9px] text-[#6b8290]">
                      Physical-device provisioning will be handled in a later integration stage.
                    </p>
                  </div>
                </div>
              </div>
            </section>

            <section className="rounded-[14px] border border-[#dce8ee] bg-white p-5">
              <div>
                <h2 className="text-[15px] font-semibold text-[#102a3a]">
                  Monitoring defaults
                </h2>

                <p className="mt-1 text-[10px] text-[#6b8290]">
                  Monitoring features will become available after sensor
                  integration.
                </p>
              </div>

              <div className="mt-5 grid gap-3 md:grid-cols-3">
                {[
                  ["Sensor readings", "Live telemetry"],
                  ["Device alerts", "Threshold monitoring"],
                  ["Remote control", "Actuator commands"],
                ].map(([title, description]) => (
                  <div
                    key={title}
                    className="rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] p-3"
                  >
                    <div className="flex items-center gap-2">
                      <Check size={14} className="text-[#16a57a]" />

                      <span className="text-[10px] font-medium text-[#102a3a]">
                        {title}
                      </span>
                    </div>

                    <p className="mt-1.5 pl-[22px] text-[9px] text-[#8397a2]">
                      {description}
                    </p>
                  </div>
                ))}
              </div>
            </section>

            {error ? (
              <div className="rounded-[10px] border border-red-200 bg-red-50 p-3 text-[11px] text-red-600" role="alert" aria-live="assertive">
                {error}
              </div>
            ) : null}

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-[9px] text-[#8397a2]">
                Device name, type and location are required.
              </p>

              <div className="flex gap-2.5">
                <button
                  type="button"
                  onClick={() => navigate("/devices")}
                  className="h-10 rounded-[10px] border border-[#dce8ee] bg-white px-5 text-[12px] text-[#102a3a] transition hover:bg-[#f8fbfd]"
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  disabled={saving}
                  className="h-10 rounded-[10px] bg-[#08a9c4] px-5 text-[12px] font-medium text-white transition hover:bg-[#0799b2] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {saving ? "Registering..." : "Register device"}
                </button>
              </div>
            </div>
          </form>

          <aside className="space-y-4 lg:sticky lg:top-[88px]">
            <div className="rounded-[14px] border border-[#dce8ee] bg-white p-[18px] shadow-[var(--shadow-card)]">
              <div className="flex items-start justify-between">
                <div>
                  <h2 className="text-[15px] font-semibold text-[#102a3a]">
                    Device preview
                  </h2>

                  <p className="mt-1 text-[10px] text-[#6b8290]">
                    Preview before registration
                  </p>
                </div>

                <span className="flex items-center gap-1.5 rounded-full bg-[#fff7e5] px-2.5 py-1 text-[9px] font-medium text-[#c98914]">
                  <span className="h-1.5 w-1.5 rounded-full bg-[#d49a24]" />
                  New
                </span>
              </div>

              <div className="device-preview-visual mt-4 flex h-[156px] items-center justify-center rounded-[16px]" aria-label="Device visual preview">
                <div className="device-preview-icon flex h-[82px] w-[82px] items-center justify-center rounded-[24px]">
                  <Cpu size={40} strokeWidth={1.7} aria-hidden="true" />
                </div>
              </div>

              <div className="mt-4">
                <h3 className="text-[14px] font-semibold text-[#102a3a]">
                  {form.name || "New IoT Device"}
                </h3>

                <p className="mt-1 text-[10px] text-[#6b8290]">
                  {previewType}
                </p>
              </div>

              <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
                <span className="flex items-center gap-1.5 rounded-full bg-[#edf4f6] px-2.5 py-1 text-[9px] font-medium text-[#6b8290]">
                  <span className="h-1.5 w-1.5 rounded-full bg-[#9aaeba]" />
                  Not registered
                </span>

                <span className="flex min-w-0 items-center gap-1.5 text-[9px] text-[#6b8290]">
                  <MapPin size={11} className="shrink-0" />
                  <span className="truncate">
                    {form.location || "No location selected"}
                  </span>
                </span>
              </div>

              <div className="mt-4 rounded-[10px] border border-[#e0edf1] bg-[#f8fbfd] px-3 py-2.5">
                <div className="flex items-center gap-2 text-[10px] font-medium text-[#526b79]">
                  <RadioTower size={14} className="shrink-0 text-[#08a9c4]" aria-hidden="true" />
                  <span>Connectivity</span>
                  <span className="ml-auto text-[10px] font-semibold text-[#102a3a]">{connectivityLabel}</span>
                </div>
              </div>

              <div className="mt-4">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-[10px] font-semibold uppercase tracking-[0.08em] text-[#8397a2]">Capabilities</p>
                  <span className="text-[9px] text-[#8397a2]">{previewCapabilities.length} selected</span>
                </div>
                {visiblePreviewCapabilities.length > 0 ? (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {visiblePreviewCapabilities.map((capability, index) => (
                      <span key={capability.key || capability.name} className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-[9px] font-medium ${capabilityChipStyles[index % capabilityChipStyles.length]}`}>
                        <Check size={11} aria-hidden="true" />
                        {formatCapabilityLabel(capability)}
                      </span>
                    ))}
                    {remainingPreviewCapabilities > 0 ? (
                      <span className="rounded-full bg-[#f0f4f6] px-2 py-1 text-[9px] font-medium text-[#6b8290]">+{remainingPreviewCapabilities} more</span>
                    ) : null}
                  </div>
                ) : (
                  <p className="mt-2 text-[10px] text-[#8397a2]">Capabilities will appear here as configured.</p>
                )}
              </div>
            </div>

            <div className="rounded-[14px] border border-[#dce8ee] bg-white p-[18px]">
              <h2 className="text-[14px] font-semibold text-[#102a3a]">
                Registration checklist
              </h2>

              <p className="mt-1 text-[10px] text-[#6b8290]">
                Complete the required information.
              </p>

              <div className="mt-4 space-y-4">
                {[
                  ["Device name", completedFields[0]],
                  ["Device type", completedFields[1]],
                  ["Device location", completedFields[2]],
                ].map(([label, complete]) => (
                  <div key={label} className="flex items-center gap-2.5">
                    <CircleCheck
                      size={16}
                      className={
                        complete
                          ? "text-[#16a57a]"
                          : "text-[#c8d6dc]"
                      }
                    />

                    <span
                      className={`text-[10px] ${
                        complete
                          ? "font-medium text-[#102a3a]"
                          : "text-[#6b8290]"
                      }`}
                    >
                      {label}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-[14px] border border-[#cfe7ed] bg-[#eef9fb] p-4">
              <h3 className="text-[11px] font-semibold text-[#087b9c]">
                Device registration
              </h3>

              <p className="mt-2 text-[9px] leading-4 text-[#527683]">
                After registration, NexaIoT generates a unique device key.
                Hardware connectivity will be configured during the MQTT stage.
              </p>
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}
