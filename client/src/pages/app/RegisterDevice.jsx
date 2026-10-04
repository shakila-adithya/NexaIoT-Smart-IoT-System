import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Bluetooth,
  Cable,
  Check,
  CircleCheck,
  Cpu,
  MapPin,
  RadioTower,
  Tag,
  Wifi,
  X,
} from "lucide-react";

import { createDevice } from "../../api/devicesApi.js";
import PageHero from "../../components/common/PageHero.jsx";

const connectivityOptions = [
  {
    id: "wifi",
    label: "Wi-Fi",
    description: "Wireless network",
    icon: Wifi,
  },
  {
    id: "bluetooth",
    label: "Bluetooth",
    description: "Short-range connection",
    icon: Bluetooth,
  },
  {
    id: "mqtt",
    label: "MQTT",
    description: "IoT messaging",
    icon: RadioTower,
  },
  {
    id: "ethernet",
    label: "Ethernet",
    description: "Wired network",
    icon: Cable,
  },
];

export default function RegisterDevice() {
  const navigate = useNavigate();

  const [form, setForm] = useState({
    name: "",
    type: "",
    location: "",
  });

  const [connectivity, setConnectivity] = useState("wifi");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const handleChange = (event) => {
    const { name, value } = event.target;

    setForm((current) => ({
      ...current,
      [name]: value,
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    try {
      setSaving(true);
      setError("");

      await createDevice({
        name: form.name.trim(),
        type: form.type.trim(),
        location: form.location.trim(),
      });

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

  return (
    <div className="w-full px-4 pb-10 pt-[26px] md:px-[30px]">
      <div className="mx-auto w-full max-w-[1144px]">
        <PageHero
          eyebrow="Device registration"
          title="Register a new IoT device"
          description="Add your device information and prepare it for connectivity and live monitoring."
          image="https://images.unsplash.com/photo-1581092921461-eab62e97a780?auto=format&fit=crop&w=1400&q=85"
          imageAlt="Technician configuring IoT hardware"
        >
          <button
            type="button"
            onClick={() => navigate("/devices")}
            className="flex h-9 items-center gap-2 rounded-[9px] border border-white/30 bg-white/15 px-4 text-[11px] font-medium text-white transition hover:bg-white/25"
          >
            <X size={14} />
            Cancel
          </button>
        </PageHero>

        <div className="mt-5 rounded-[14px] border border-[#dce8ee] bg-white p-[14px]">
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
        </div>

        <div className="mt-5 grid items-start gap-[18px] xl:grid-cols-[minmax(0,1fr)_340px]">
          <form onSubmit={handleSubmit} className="space-y-4">
            <section className="rounded-[14px] border border-[#dce8ee] bg-white p-5">
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
                  <label className="text-[10px] font-medium text-[#526b79]">
                    Device name
                  </label>

                  <div className="relative mt-2">
                    <Tag
                      size={15}
                      className="absolute left-3 top-1/2 -translate-y-1/2 text-[#9aaeba]"
                    />

                    <input
                      type="text"
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
                  <label className="text-[10px] font-medium text-[#526b79]">
                    Device type
                  </label>

                  <div className="relative mt-2">
                    <Cpu
                      size={15}
                      className="absolute left-3 top-1/2 -translate-y-1/2 text-[#9aaeba]"
                    />

                    <input
                      type="text"
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
                  <label className="text-[10px] font-medium text-[#526b79]">
                    Location
                  </label>

                  <div className="relative mt-2">
                    <MapPin
                      size={15}
                      className="absolute left-3 top-1/2 -translate-y-1/2 text-[#9aaeba]"
                    />

                    <input
                      type="text"
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

            <section className="rounded-[14px] border border-[#dce8ee] bg-white p-5">
              <div>
                <h2 className="text-[15px] font-semibold text-[#102a3a]">
                  Connectivity
                </h2>

                <p className="mt-1 text-[10px] text-[#6b8290]">
                  Choose how this device will communicate with NexaIoT.
                </p>
              </div>

              <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                {connectivityOptions.map((option) => {
                  const Icon = option.icon;
                  const selected = connectivity === option.id;

                  return (
                    <button
                      type="button"
                      key={option.id}
                      onClick={() => setConnectivity(option.id)}
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
                      MQTT and physical-device connection settings will be
                      enabled during the IoT integration stage.
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
              <div className="rounded-[10px] border border-red-200 bg-red-50 p-3 text-[11px] text-red-600">
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

          <aside className="space-y-4">
            <div className="rounded-[14px] border border-[#dce8ee] bg-white p-[18px]">
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

              <div className="mt-4 flex h-[150px] items-center justify-center rounded-[12px] bg-gradient-to-br from-[#effafd] to-[#edf5f8]">
                <div className="flex h-20 w-20 items-center justify-center rounded-[22px] bg-white shadow-[0_8px_25px_rgba(16,42,58,0.08)]">
                  <Cpu size={38} className="text-[#08a9c4]" />
                </div>
              </div>

              <div className="mt-4">
                <h3 className="text-[14px] font-semibold text-[#102a3a]">
                  {form.name || "New IoT Device"}
                </h3>

                <p className="mt-1 text-[10px] text-[#6b8290]">
                  {form.type || "Device type"}
                </p>
              </div>

              <div className="mt-4 flex items-center justify-between gap-3">
                <span className="flex items-center gap-1.5 rounded-full bg-[#edf4f6] px-2.5 py-1 text-[9px] font-medium text-[#6b8290]">
                  <span className="h-1.5 w-1.5 rounded-full bg-[#9aaeba]" />
                  Offline
                </span>

                <span className="flex min-w-0 items-center gap-1.5 text-[9px] text-[#6b8290]">
                  <MapPin size={11} className="shrink-0" />
                  <span className="truncate">
                    {form.location || "No location selected"}
                  </span>
                </span>
              </div>

              <div className="mt-5 grid grid-cols-3 gap-2">
                <div className="rounded-[10px] bg-[#f8fbfd] px-2 py-3 text-center">
                  <div className="text-[11px] font-semibold text-[#102a3a]">
                    —
                  </div>
                  <div className="mt-1 text-[8px] text-[#8397a2]">
                    Reading
                  </div>
                </div>

                <div className="rounded-[10px] bg-[#f8fbfd] px-2 py-3 text-center">
                  <div className="text-[11px] font-semibold text-[#102a3a]">
                    —
                  </div>
                  <div className="mt-1 text-[8px] text-[#8397a2]">
                    Signal
                  </div>
                </div>

                <div className="rounded-[10px] bg-[#f8fbfd] px-2 py-3 text-center">
                  <div className="text-[11px] font-semibold text-[#102a3a]">
                    —
                  </div>
                  <div className="mt-1 text-[8px] text-[#8397a2]">
                    Updated
                  </div>
                </div>
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