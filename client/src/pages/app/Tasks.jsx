import { useEffect, useMemo, useState } from "react";
import { ClipboardCheck, Pencil, Plus, Trash2, X } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { getAlert } from "../../api/alertsApi.js";
import { getDevices } from "../../api/devicesApi.js";
import { createTask, deleteTask, getTasks, updateTask } from "../../api/tasksApi.js";
import { useToast } from "../../hooks/useToast.js";

const emptyForm = { title: "", description: "", deviceId: "", alertId: "", priority: "MEDIUM", status: "OPEN", dueDate: "" };
const statusLabels = { OPEN: "Open", IN_PROGRESS: "In progress", COMPLETED: "Completed", CANCELLED: "Cancelled" };

function displayDate(value) { if (!value) return "No due date"; const date = new Date(value); return Number.isNaN(date.getTime()) ? "No due date" : `Due ${date.toLocaleDateString(undefined, { month: "short", day: "numeric" })}`; }
function formFromTask(task) { return { title: task.title || "", description: task.description || "", deviceId: task.deviceId || "", alertId: task.alertId || "", priority: task.priority || "MEDIUM", status: task.status || "OPEN", dueDate: task.dueDate ? new Date(task.dueDate).toISOString().slice(0, 10) : "" }; }

export default function Tasks() {
  const [searchParams] = useSearchParams();
  const { showToast } = useToast();
  const [tasks, setTasks] = useState([]); const [devices, setDevices] = useState([]);
  const [form, setForm] = useState(emptyForm); const [editingId, setEditingId] = useState("");
  const [search, setSearch] = useState(""); const [statusFilter, setStatusFilter] = useState("ALL"); const [priorityFilter, setPriorityFilter] = useState("ALL");
  const [loading, setLoading] = useState(true); const [saving, setSaving] = useState(false); const [deletingId, setDeletingId] = useState(""); const [error, setError] = useState(""); const [modalOpen, setModalOpen] = useState(false);

  useEffect(() => {
    let mounted = true;
    Promise.all([getTasks(), getDevices()]).then(async ([loadedTasks, loadedDevices]) => {
      if (!mounted) return;
      const safeDevices = Array.isArray(loadedDevices) ? loadedDevices : [];
      setTasks(Array.isArray(loadedTasks) ? loadedTasks : []); setDevices(safeDevices);
      const alertId = searchParams.get("alertId");
      if (alertId) {
        try {
          const alert = await getAlert(alertId);
          if (mounted) { setForm({ ...emptyForm, title: `${alert.type?.replaceAll("_", " ") || "Alert"} follow-up`, description: alert.message || "", deviceId: alert.deviceId || "", alertId: alert.id || "", priority: alert.severity === "CRITICAL" || alert.severity === "WARNING" ? alert.severity : "MEDIUM" }); setModalOpen(true); }
        } catch { if (mounted) setError("Unable to load the selected alert for task creation."); }
      }
    }).catch((requestError) => mounted && setError(requestError.message || "Unable to load maintenance tasks."))
      .finally(() => mounted && setLoading(false));
    return () => { mounted = false; };
  }, [searchParams]);

  const visibleTasks = useMemo(() => tasks.filter((task) => {
    const query = search.trim().toLowerCase(); const device = devices.find((item) => item.id === task.deviceId);
    return (statusFilter === "ALL" || task.status === statusFilter) && (priorityFilter === "ALL" || task.priority === priorityFilter) && (!query || `${task.title} ${device?.name || ""}`.toLowerCase().includes(query));
  }), [devices, priorityFilter, search, statusFilter, tasks]);

  function newTask() { setEditingId(""); setForm({ ...emptyForm, deviceId: devices[0]?.id || "" }); setError(""); setModalOpen(true); }
  function editTask(task) { setEditingId(task.id); setForm(formFromTask(task)); setError(""); setModalOpen(true); }
  function updateField(event) { setForm((current) => ({ ...current, [event.target.name]: event.target.value })); }
  async function save(event) {
    event.preventDefault(); if (!form.title.trim() || !form.deviceId) { setError("Enter a title and choose a device."); return; }
    try { setSaving(true); setError(""); const payload = { ...form, title: form.title.trim(), dueDate: form.dueDate || null, alertId: form.alertId || null };
      const saved = editingId ? await updateTask(editingId, payload) : await createTask(payload);
      setTasks((current) => editingId ? current.map((task) => task.id === saved.id ? saved : task) : [saved, ...current]); setModalOpen(false); setEditingId(""); showToast(editingId ? "Task updated." : "Task created.");
    } catch (requestError) { setError(requestError.message || "Unable to save the task."); } finally { setSaving(false); }
  }
  async function remove(task) { if (!window.confirm(`Delete task “${task.title}”?`)) return; try { setDeletingId(task.id); await deleteTask(task.id); setTasks((current) => current.filter((item) => item.id !== task.id)); showToast("Task deleted."); } catch (requestError) { setError(requestError.message || "Unable to delete the task."); } finally { setDeletingId(""); } }

  return <div className="w-full px-4 pb-10 pt-[26px] md:px-[30px]"><div className="mx-auto w-full max-w-[1144px]">
    <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div><h1 className="text-[26px] font-normal text-[#102a3a]">Maintenance tasks</h1><p className="mt-1 text-[12px] text-[#6b8290]">Track the work needed to keep your devices healthy.</p></div><button type="button" onClick={newTask} className="inline-flex h-10 items-center justify-center gap-2 rounded-[10px] bg-[#08a9c4] px-4 text-[11px] font-medium text-white"><Plus size={14} /> New task</button></div>
    {error ? <div className="mt-5 rounded-[12px] border border-red-200 bg-red-50 p-4 text-[11px] text-red-600">{error}</div> : null}
    <section className="mt-6 rounded-[14px] border border-[#dce8ee] bg-white shadow-[0_5px_18px_rgba(10,48,72,0.05)]"><div className="flex flex-wrap gap-2 border-b border-[#edf3f6] p-4"><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search tasks or devices..." className="h-9 min-w-[220px] flex-1 rounded-[9px] border border-[#dce8ee] px-3 text-[11px] outline-none focus:border-[#08a9c4]" /><select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} className="h-9 rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[10px]"><option value="ALL">All statuses</option>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><select value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)} className="h-9 rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[10px]"><option value="ALL">All priorities</option>{["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((value) => <option key={value}>{value}</option>)}</select></div>
      {loading ? <div className="p-8 text-[11px] text-[#6b8290]">Loading tasks...</div> : visibleTasks.length === 0 ? <div className="p-12 text-center"><ClipboardCheck size={26} className="mx-auto text-[#9bb0bb]" /><p className="mt-3 text-[12px] font-semibold text-[#102a3a]">{tasks.length ? "No matching tasks." : "No maintenance tasks yet."}</p><p className="mt-1 text-[10px] text-[#6b8290]">Create a task from an alert or start one here.</p></div> : <div className="divide-y divide-[#edf3f6]">{visibleTasks.map((task) => { const device = devices.find((item) => item.id === task.deviceId); return <div key={task.id} className="flex flex-wrap items-center justify-between gap-4 p-4"><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><h2 className="text-[12px] font-semibold text-[#102a3a]">{task.title}</h2><span className="rounded-full bg-[#eef9fb] px-2 py-0.5 text-[9px] font-semibold text-[#087f98]">{task.priority}</span><span className="rounded-full bg-[#e8f7f2] px-2 py-0.5 text-[9px] font-semibold text-[#16815f]">{statusLabels[task.status] || task.status}</span></div><p className="mt-1 text-[10px] text-[#6b8290]">{device?.name || "Unknown device"} · {displayDate(task.dueDate)}{task.alertId ? " · Linked alert" : ""}</p>{task.description ? <p className="mt-2 line-clamp-2 text-[10px] text-[#8397a2]">{task.description}</p> : null}</div><div className="flex items-center gap-1"><button type="button" onClick={() => editTask(task)} className="rounded-[8px] p-2 text-[#6b8290] hover:bg-[#f4f8fb]" aria-label="Edit task"><Pencil size={14} /></button><button type="button" disabled={deletingId === task.id} onClick={() => remove(task)} className="rounded-[8px] p-2 text-[#d83f4d] hover:bg-[#fdecee]" aria-label="Delete task"><Trash2 size={14} /></button></div></div>; })}</div>}
    </section>
    {modalOpen ? <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0b2535]/45 px-4"><div className="w-full max-w-[480px] rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-2xl"><div className="flex items-center justify-between"><h2 className="text-[15px] font-semibold text-[#102a3a]">{editingId ? "Edit task" : "Create maintenance task"}</h2><button type="button" onClick={() => setModalOpen(false)} className="text-[#6b8290]" aria-label="Close"><X size={17} /></button></div><form onSubmit={save} className="mt-4 space-y-3"><label className="block text-[10px] font-medium text-[#526b79]">Title<input name="title" value={form.title} onChange={updateField} maxLength={160} className="mt-1 h-9 w-full rounded-[9px] border border-[#dce8ee] px-3 text-[11px] outline-none focus:border-[#08a9c4]" /></label><label className="block text-[10px] font-medium text-[#526b79]">Device<select name="deviceId" value={form.deviceId} onChange={updateField} disabled={Boolean(editingId)} className="mt-1 h-9 w-full rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[11px]"><option value="">Select a device</option>{devices.map((device) => <option key={device.id} value={device.id}>{device.name}</option>)}</select></label><label className="block text-[10px] font-medium text-[#526b79]">Description<textarea name="description" value={form.description} onChange={updateField} maxLength={2000} rows={3} className="mt-1 w-full rounded-[9px] border border-[#dce8ee] px-3 py-2 text-[11px] outline-none focus:border-[#08a9c4]" /></label><div className="grid grid-cols-2 gap-2"><label className="block text-[10px] font-medium text-[#526b79]">Priority<select name="priority" value={form.priority} onChange={updateField} className="mt-1 h-9 w-full rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[11px]">{["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((value) => <option key={value}>{value}</option>)}</select></label><label className="block text-[10px] font-medium text-[#526b79]">Due date<input name="dueDate" type="date" value={form.dueDate} onChange={updateField} className="mt-1 h-9 w-full rounded-[9px] border border-[#dce8ee] px-3 text-[11px]" /></label></div>{editingId ? <label className="block text-[10px] font-medium text-[#526b79]">Status<select name="status" value={form.status} onChange={updateField} className="mt-1 h-9 w-full rounded-[9px] border border-[#dce8ee] bg-white px-3 text-[11px]">{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label> : null}<div className="flex justify-end gap-2 pt-2"><button type="button" onClick={() => setModalOpen(false)} className="h-9 rounded-[9px] border border-[#dce8ee] px-4 text-[10px] font-semibold text-[#526b79]">Cancel</button><button type="submit" disabled={saving || !devices.length} className="h-9 rounded-[9px] bg-[#08a9c4] px-4 text-[10px] font-semibold text-white disabled:opacity-50">{saving ? "Saving..." : editingId ? "Save changes" : "Create task"}</button></div></form></div></div> : null}
  </div></div>;
}
