import { useEffect, useState } from "react";
import { FileArchive, RefreshCw, ShieldCheck, Upload } from "lucide-react";
import { getFirmwarePackages, uploadFirmwarePackage } from "../../api/firmwareApi.js";
import Button from "../../components/ui/Button.jsx";
import PageHeader from "../../components/ui/PageHeader.jsx";
import SectionCard from "../../components/ui/SectionCard.jsx";
import { useToast } from "../../hooks/useToast.js";

function bytes(value) {
  if (!Number.isFinite(value)) return "—";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function dateValue(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString();
}

export default function FirmwareManagement() {
  const { showToast } = useToast();
  const [packages, setPackages] = useState([]);
  const [version, setVersion] = useState("");
  const [deviceTypes, setDeviceTypes] = useState("");
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let mounted = true;
    getFirmwarePackages().then((data) => mounted && setPackages(Array.isArray(data) ? data : [])).catch((requestError) => mounted && setError(requestError.message || "Unable to load firmware packages.")).finally(() => mounted && setLoading(false));
    return () => { mounted = false; };
  }, [refreshKey]);

  async function submit(event) {
    event.preventDefault();
    if (!file || !version.trim() || !deviceTypes.trim()) { setError("Choose a package and provide its version and compatible device types."); return; }
    try {
      setUploading(true); setError("");
      const saved = await uploadFirmwarePackage({ file, version: version.trim(), compatibleDeviceTypes: deviceTypes.trim() });
      setPackages((current) => [saved, ...current]);
      setFile(null); setVersion(""); setDeviceTypes("");
      event.target.reset();
      showToast("Firmware metadata saved.");
    } catch (requestError) { setError(requestError.message || "Unable to save the firmware package."); }
    finally { setUploading(false); }
  }

  return <div className="w-full px-4 pb-10 pt-7 md:px-8 lg:px-[30px]"><div className="mx-auto w-full max-w-[1240px]">
    <PageHeader tone="security" title="Firmware management" description="Store and review firmware packages without claiming device installation or OTA deployment." actions={<Button type="button" variant="secondary" onClick={() => { setLoading(true); setRefreshKey((current) => current + 1); }} loading={loading} icon={RefreshCw}>Refresh</Button>} />
    <div className="mt-6 grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(320px,0.7fr)]"><SectionCard title="Firmware repository" description="Metadata includes a server-calculated SHA-256 digest. The digest does not prove authenticity."><div className="mt-1 flex items-start gap-3 rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] p-3 text-[10px] text-[#526b79]"><ShieldCheck size={16} className="shrink-0 text-[#08a9c4]" /><span>Packages are administrator-only and stored through the backend storage contract. OTA deployment is not available.</span></div>{loading ? <p className="mt-5 text-[11px] text-[#6b8290]">Loading firmware packages...</p> : packages.length === 0 ? <div className="mt-5 rounded-[10px] border border-dashed border-[#dce8ee] p-8 text-center"><FileArchive size={25} className="mx-auto text-[#9bb0bb]" /><p className="mt-2 text-[11px] text-[#6b8290]">No firmware packages registered.</p></div> : <div className="mt-5 divide-y divide-[#edf3f6] rounded-[10px] border border-[#edf3f6]">{packages.map((item) => <div key={item.id} className="p-4"><div className="flex flex-wrap items-center justify-between gap-2"><div><p className="text-[12px] font-semibold text-[#102a3a]">Version {item.version}</p><p className="mt-1 text-[10px] text-[#6b8290]">{item.originalFilename} · {bytes(item.size)} · {dateValue(item.uploadedAt)}</p></div><span className="rounded-full bg-[#eef9fb] px-2 py-1 text-[9px] font-semibold text-[#087f98]">Metadata only</span></div><p className="mt-2 break-all font-mono text-[9px] text-[#8ba0ab]">SHA-256 {item.sha256}</p><p className="mt-1 text-[10px] text-[#526b79]">Compatible types: {item.compatibleDeviceTypes?.join(", ") || "—"}</p></div>)}</div>}</SectionCard><SectionCard title="Register package" description="Accepted extensions: BIN, HEX, UF2, or ZIP. Maximum size: 50 MB."><form onSubmit={submit} className="space-y-3"><label className="block text-[10px] font-medium text-[#526b79]">Version<input value={version} onChange={(event) => setVersion(event.target.value)} maxLength={80} className="mt-1 h-10 w-full rounded-[9px] border border-[#dce8ee] px-3 text-[11px] outline-none focus:border-[#08a9c4]" placeholder="1.0.0" /></label><label className="block text-[10px] font-medium text-[#526b79]">Compatible device types<input value={deviceTypes} onChange={(event) => setDeviceTypes(event.target.value)} className="mt-1 h-10 w-full rounded-[9px] border border-[#dce8ee] px-3 text-[11px] outline-none focus:border-[#08a9c4]" placeholder="temperature-sensor, smart-switch" /><span className="mt-1 block text-[9px] text-[#8ba0ab]">Separate types with commas.</span></label><label className="block text-[10px] font-medium text-[#526b79]">Firmware file<input type="file" accept=".bin,.hex,.uf2,.zip,application/octet-stream,application/zip" onChange={(event) => setFile(event.target.files?.[0] || null)} className="mt-1 block w-full text-[10px] text-[#526b79] file:mr-2 file:rounded file:border-0 file:bg-[#eef9fb] file:px-2 file:py-1 file:text-[10px] file:font-semibold file:text-[#087f98]" /></label>{error ? <p className="rounded-[9px] border border-red-200 bg-red-50 p-3 text-[10px] text-red-600" role="alert">{error}</p> : null}<Button type="submit" loading={uploading} disabled={!file || !version.trim() || !deviceTypes.trim()} icon={Upload}>Register firmware</Button></form></SectionCard></div>
  </div></div>;
}
