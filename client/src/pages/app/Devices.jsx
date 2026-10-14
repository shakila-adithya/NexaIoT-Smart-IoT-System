import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Cpu,
  DoorOpen,
  Fan,
  Gauge,
  Grid2X2,
  List,
  Plus,
  Router,
  Search,
  ThermometerSnowflake,
  Wifi,
  WifiOff,
  Wrench,
} from "lucide-react";

import { controlDevice, deleteDevice, getDevices } from "../../api/devicesApi.js";
import { normalizeCapabilityManifest } from "../../config/capabilityManifest.js";
import DeviceCard from "../../components/device/DeviceCard.jsx";
import DeviceSummaryCard from "../../components/device/DeviceSummaryCard.jsx";
import PageHero from "../../components/common/PageHero.jsx";
import { useToast } from "../../hooks/useToast.js";

function getDeviceIcon(type = "") {
  const value = type.toLowerCase();

  if (value.includes("fan")) return Fan;
  if (value.includes("temperature")) return ThermometerSnowflake;
  if (value.includes("pressure") || value.includes("gauge")) return Gauge;
  if (value.includes("door")) return DoorOpen;
  if (value.includes("router") || value.includes("gateway")) return Router;

  return Cpu;
}

export default function Devices() {
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [devices, setDevices] = useState([]);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [view, setView] = useState("grid");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [controlLoadingId, setControlLoadingId] = useState("");
  const [pendingPower, setPendingPower] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function loadDevices(showLoading = false) {
      try {
        if (showLoading) {
          setLoading(true);
        }

        setError("");

        const data = await getDevices();

        if (!cancelled) {
          setDevices(Array.isArray(data) ? data : []);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err.message || "Unable to load devices.");
        }
      } finally {
        if (showLoading && !cancelled) {
          setLoading(false);
        }
      }
    }

    loadDevices(true);

    const interval = setInterval(() => {
      loadDevices();
    }, 5000);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);
  
  const summary = useMemo(
    () => ({
      all: devices.length,

      online: devices.filter(
        (device) => device.status === "ONLINE",
      ).length,

      offline: devices.filter(
        (device) => device.status === "OFFLINE",
      ).length,

      maintenance: devices.filter(
        (device) => device.status === "MAINTENANCE",
      ).length,
    }),
    [devices],
  );

  const filteredDevices = useMemo(() => {
    const query = search.trim().toLowerCase();

    return devices.filter((device) => {
      const matchesStatus =
        statusFilter === "ALL" ||
        device.status === statusFilter;

      const matchesSearch =
        !query ||
        device.name?.toLowerCase().includes(query) ||
        device.type?.toLowerCase().includes(query) ||
        device.location?.toLowerCase().includes(query);

      return matchesStatus && matchesSearch;
    });
  }, [devices, search, statusFilter]);

  const supportsPowerControl = (device) => normalizeCapabilityManifest(device).some(
    (capability) => capability.category === "CONTROL"
      && ["power", "digitalOutput"].includes(capability.key || capability.semanticType),
  );

  const handlePowerToggle = async (device) => {
    if (!supportsPowerControl(device) || controlLoadingId || device.status !== "ONLINE") return;

    const targetPower = !device.powerOn;
    setControlLoadingId(device.id);
    setPendingPower(targetPower);
    try {
      await controlDevice(device.id, targetPower);
      for (let attempt = 0; attempt < 8; attempt += 1) {
        await new Promise((resolve) => setTimeout(resolve, 250));
        const updatedDevices = await getDevices();
        const updatedDevice = updatedDevices.find((item) => item.id === device.id);
        if (updatedDevice) {
          setDevices(updatedDevices);
          if (updatedDevice.powerOn === targetPower) break;
        }
      }
    } catch (requestError) {
      showToast(requestError.message || "Unable to send device command.", "error");
    } finally {
      setControlLoadingId("");
      setPendingPower(null);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget || deleting) return;
    setDeleting(true);
    try {
      await deleteDevice(deleteTarget.id);
      setDevices((current) => current.filter((device) => device.id !== deleteTarget.id));
      showToast("Device deleted successfully.");
      setDeleteTarget(null);
    } catch (requestError) {
      showToast(requestError.message || "Unable to delete device.", "error");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="w-full px-4 pb-9 pt-[26px] md:px-[30px]">
      <div className="mx-auto w-full max-w-[1144px]">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-[26px] font-normal text-[#102a3a]">
              Devices
            </h1>

            <p className="mt-[5px] text-[12px] text-[#6b8290]">
              Monitor, organize, and control every connected device.
            </p>
          </div>

          <button
            type="button"
            onClick={() => navigate("/devices/register")}
            className="flex h-10 items-center justify-center gap-2 rounded-[10px] bg-[#08a9c4] px-4 text-[12px] font-medium text-white transition hover:bg-[#0798b1]"
          >
            <Plus size={15} />
            Add device
          </button>
        </div>

        <div className="mt-6">
          <PageHero
            eyebrow="Device fleet"
            title="Manage every connected device from one place."
            description={`${summary.all} devices registered · ${summary.online} online · ${summary.offline} offline`}
            image="https://images.unsplash.com/photo-1553406830-ef2513450d76?auto=format&fit=crop&w=1400&q=85"
            imageAlt="Connected IoT and network hardware"
          />
        </div>

        <div className="mt-6 grid w-full gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <DeviceSummaryCard
            icon={Cpu}
            value={summary.all}
            label="All devices"
          />

          <DeviceSummaryCard
            icon={Wifi}
            value={summary.online}
            label="Online"
            iconClassName="text-[#16a57a]"
          />

          <DeviceSummaryCard
            icon={WifiOff}
            value={summary.offline}
            label="Offline"
            iconClassName="text-[#e24e5a]"
          />

          <DeviceSummaryCard
            icon={Wrench}
            value={summary.maintenance}
            label="Maintenance"
            iconClassName="text-[#d49a24]"
          />
        </div>

        <div className="mt-6 flex w-full flex-col gap-3 rounded-[14px] border border-[#dce8ee] bg-white p-3 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search
              size={15}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-[#9aaeba]"
            />

            <input
              type="text"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search devices..."
              className="h-10 w-full rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] pl-9 pr-3 text-[12px] text-[#102a3a] outline-none placeholder:text-[#9aaeba] focus:border-[#08a9c4]"
            />
          </div>

          <select
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
            className="h-10 rounded-[10px] border border-[#dce8ee] bg-white px-3 text-[11px] text-[#6b8290] outline-none focus:border-[#08a9c4]"
          >
            <option value="ALL">All status</option>
            <option value="ONLINE">Online</option>
            <option value="OFFLINE">Offline</option>
            <option value="MAINTENANCE">Maintenance</option>
          </select>

          <div className="flex h-10 items-center rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] p-1">
            <button
              type="button"
              onClick={() => setView("grid")}
              className={`flex h-8 w-8 items-center justify-center rounded-lg transition ${
                view === "grid"
                  ? "bg-white text-[#08a9c4] shadow-sm"
                  : "text-[#9aaeba] hover:text-[#6b8290]"
              }`}
              aria-label="Grid view"
            >
              <Grid2X2 size={15} />
            </button>

            <button
              type="button"
              onClick={() => setView("list")}
              className={`flex h-8 w-8 items-center justify-center rounded-lg transition ${
                view === "list"
                  ? "bg-white text-[#08a9c4] shadow-sm"
                  : "text-[#9aaeba] hover:text-[#6b8290]"
              }`}
              aria-label="List view"
            >
              <List size={15} />
            </button>
          </div>
        </div>

        {loading ? (
          <div className="mt-6 w-full rounded-[14px] border border-[#dce8ee] bg-white p-8 text-center text-[12px] text-[#6b8290]">
            Loading devices...
          </div>
        ) : error ? (
          <div className="mt-6 w-full rounded-[14px] border border-red-200 bg-red-50 p-4 text-[12px] text-red-600">
            {error}
          </div>
        ) : filteredDevices.length === 0 ? (
          <div className="mt-6 w-full rounded-[14px] border border-[#dce8ee] bg-white p-10 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-[12px] bg-[#eef9fb]">
              <Cpu size={24} className="text-[#08a9c4]" />
            </div>

            <h2 className="mt-3 text-[14px] font-semibold text-[#102a3a]">
              No devices found
            </h2>

            <p className="mt-1 text-[11px] text-[#6b8290]">
              Add your first device to start monitoring it.
            </p>

            <button
              type="button"
              onClick={() => navigate("/devices/register")}
              className="mt-4 inline-flex h-9 items-center gap-2 rounded-[9px] bg-[#08a9c4] px-4 text-[11px] font-medium text-white transition hover:bg-[#0798b1]"
            >
              <Plus size={14} />
              Register device
            </button>
          </div>
        ) : (
          <div
            className={
              view === "grid"
                ? "mt-6 grid w-full grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3"
                : "mt-6 grid w-full grid-cols-1 gap-4"
            }
          >
            {filteredDevices.map((device) => (
                <DeviceCard
                key={device.id}
                icon={getDeviceIcon(device.type)}
                name={device.name}
                type={device.type}
                location={device.location}
                status={device.status}
                powerOn={device.powerOn}
                onOpen={() => navigate(`/devices/${device.id}`)}
                onEdit={() => navigate(`/devices/${device.id}`)}
                onDelete={() => setDeleteTarget(device)}
                supportsPowerControl={supportsPowerControl(device)}
                controlLoading={controlLoadingId === device.id}
                pendingPower={controlLoadingId === device.id ? pendingPower : null}
                onToggle={() => handlePowerToggle(device)}
                />
            ))}
          </div>
        )}
      </div>
      {deleteTarget ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#102a3a]/45 px-4 py-5 backdrop-blur-[2px]" onClick={() => !deleting && setDeleteTarget(null)}>
          <div className="w-full max-w-[420px] rounded-[16px] border border-[#e4ecef] bg-white p-5 shadow-[0_28px_80px_rgba(16,42,58,0.28)]" onClick={(event) => event.stopPropagation()}>
            <h2 className="text-[15px] font-semibold text-[#102a3a]">Delete device?</h2>
            <p className="mt-2 text-[11px] leading-5 text-[#6b8290]">This will permanently remove {deleteTarget.name} and its device configuration.</p>
            <div className="mt-5 flex justify-end gap-2">
              <button type="button" disabled={deleting} onClick={() => setDeleteTarget(null)} className="h-9 rounded-[9px] border border-[#dce8ee] px-4 text-[11px] text-[#526b79] disabled:opacity-60">Cancel</button>
              <button type="button" disabled={deleting} onClick={handleDelete} className="h-9 rounded-[9px] bg-[#e24e5a] px-4 text-[11px] font-medium text-white disabled:opacity-60">{deleting ? "Deleting..." : "Delete device"}</button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
