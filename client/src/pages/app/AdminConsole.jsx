import { useEffect, useMemo, useState } from "react";
import { Cpu, RefreshCw, Search, ShieldCheck, Trash2, Users } from "lucide-react";
import { deleteAdminDevice, deleteAdminUser, getAdminDevices, getAdminStats, getAdminUsers } from "../../api/adminApi.js";
import Button from "../../components/ui/Button.jsx";
import PageHeader from "../../components/ui/PageHeader.jsx";
import SectionCard from "../../components/ui/SectionCard.jsx";
import { useToast } from "../../hooks/useToast.js";

function dateValue(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleDateString();
}

export default function AdminConsole() {
  const { showToast } = useToast();
  const [users, setUsers] = useState([]);
  const [devices, setDevices] = useState([]);
  const [stats, setStats] = useState(null);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const [deleting, setDeleting] = useState("");

  useEffect(() => {
    let mounted = true;
    Promise.all([getAdminUsers(search), getAdminDevices(search), getAdminStats()])
      .then(([loadedUsers, loadedDevices, loadedStats]) => {
        if (!mounted) return;
        setUsers(Array.isArray(loadedUsers) ? loadedUsers : []);
        setDevices(Array.isArray(loadedDevices) ? loadedDevices : []);
        setStats(loadedStats || null);
        setError("");
      })
      .catch((requestError) => mounted && setError(requestError.message || "Unable to load administrative data."))
      .finally(() => mounted && setLoading(false));
    return () => { mounted = false; };
  }, [refreshKey, search]);

  const summary = useMemo(() => [
    { label: "Registered users", value: stats?.users ?? "—", icon: Users },
    { label: "Registered devices", value: stats?.devices ?? "—", icon: Cpu },
    { label: "Online devices", value: stats?.onlineDevices ?? "—", icon: ShieldCheck },
  ], [stats]);

  function refresh() {
    setLoading(true);
    setRefreshKey((current) => current + 1);
    showToast("Administrative data refreshed.");
  }

  async function removeUser(user) {
    if (deleting || !window.confirm(`Delete ${user.email}? This also deletes all devices and data owned by this user.`)) return;
    setDeleting(`user:${user.id}`);
    try {
      await deleteAdminUser(user.id);
      setUsers((current) => current.filter((item) => item.id !== user.id));
      showToast("User and owned data deleted.");
      setRefreshKey((current) => current + 1);
    } catch (requestError) {
      showToast(requestError.message || "Unable to delete user.", "error");
    } finally { setDeleting(""); }
  }

  async function removeDevice(device) {
    if (deleting || !window.confirm(`Delete ${device.name || device.deviceKey}? This permanently deletes the device data.`)) return;
    setDeleting(`device:${device.id}`);
    try {
      await deleteAdminDevice(device.id);
      setDevices((current) => current.filter((item) => item.id !== device.id));
      showToast("Device and related data deleted.");
      setRefreshKey((current) => current + 1);
    } catch (requestError) {
      showToast(requestError.message || "Unable to delete device.", "error");
    } finally { setDeleting(""); }
  }

  return <div className="w-full px-4 pb-10 pt-7 md:px-8 lg:px-[30px]"><div className="mx-auto w-full max-w-[1240px]">
    <PageHeader tone="security" title="Admin console" description="Manage authorized users and devices across the NexaIoT installation." actions={<Button type="button" variant="secondary" onClick={refresh} loading={loading} icon={RefreshCw}>Refresh</Button>} />
    {error ? <div className="mt-5 rounded-[12px] border border-red-200 bg-red-50 p-4 text-[11px] text-red-600" role="alert">{error}</div> : null}
    <div className="mt-6 grid gap-4 sm:grid-cols-3">{summary.map(({ label, value, icon: Icon }) => <SectionCard key={label} compact><div className="flex items-center justify-between"><div><p className="text-[10px] uppercase tracking-wide text-[#8ba0ab]">{label}</p><p className="mt-2 text-2xl font-semibold text-[#102a3a]">{value}</p></div><Icon size={21} className="text-[#08a9c4]" /></div></SectionCard>)}</div>
    <SectionCard className="mt-4" title="Administrative inventory" description="Search is applied by the backend to the authorized inventory returned for this administrator."><div className="relative"><Search size={15} className="absolute left-3 top-3 text-[#8ba0ab]" /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search users, devices, owners, or status" className="h-10 w-full rounded-[9px] border border-[#dce8ee] pl-9 pr-3 text-[11px] outline-none focus:border-[#08a9c4]" /></div><div className="mt-6 grid gap-6 xl:grid-cols-2"><div><h2 className="text-[13px] font-semibold text-[#102a3a]">Users</h2>{loading ? <p className="mt-3 text-[11px] text-[#6b8290]">Loading users...</p> : users.length === 0 ? <p className="mt-3 text-[11px] text-[#6b8290]">No users matched the search.</p> : <div className="mt-3 divide-y divide-[#edf3f6] rounded-[10px] border border-[#edf3f6]">{users.map((user) => <div key={user.id} className="flex items-center justify-between gap-3 p-3"><div className="min-w-0"><p className="truncate text-[11px] font-semibold text-[#102a3a]">{user.fullName || user.email}</p><p className="truncate text-[10px] text-[#6b8290]">{user.email} · Joined {dateValue(user.createdAt)}</p></div><div className="flex shrink-0 items-center gap-2"><span className="rounded-full bg-[#eef9fb] px-2 py-1 text-[9px] font-semibold text-[#087f98]">{user.role || "USER"}</span><button type="button" aria-label={`Delete ${user.email}`} disabled={Boolean(deleting)} onClick={() => removeUser(user)} className="rounded-[8px] p-1.5 text-[#d83f4d] hover:bg-[#fdecee] disabled:opacity-50"><Trash2 size={14} /></button></div></div>)}</div>}</div><div><h2 className="text-[13px] font-semibold text-[#102a3a]">Devices</h2>{loading ? <p className="mt-3 text-[11px] text-[#6b8290]">Loading devices...</p> : devices.length === 0 ? <p className="mt-3 text-[11px] text-[#6b8290]">No devices matched the search.</p> : <div className="mt-3 divide-y divide-[#edf3f6] rounded-[10px] border border-[#edf3f6]">{devices.map((device) => <div key={device.id} className="p-3"><div className="flex items-center justify-between gap-3"><div className="min-w-0"><p className="truncate text-[11px] font-semibold text-[#102a3a]">{device.name || device.deviceKey}</p><p className="mt-1 truncate text-[10px] text-[#6b8290]">{device.type || "Unknown type"} · Owner {device.ownerEmail || "—"}</p></div><div className="flex shrink-0 items-center gap-2"><span className="rounded-full bg-[#f4f8fb] px-2 py-1 text-[9px] font-semibold text-[#526b79]">{device.status || "UNKNOWN"}</span><button type="button" aria-label={`Delete ${device.name || device.deviceKey}`} disabled={Boolean(deleting)} onClick={() => removeDevice(device)} className="rounded-[8px] p-1.5 text-[#d83f4d] hover:bg-[#fdecee] disabled:opacity-50"><Trash2 size={14} /></button></div></div></div>)}</div>}</div></div></SectionCard>
  </div></div>;
}
