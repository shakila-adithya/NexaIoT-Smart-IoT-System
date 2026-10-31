import { useState } from "react";
import { Trash2 } from "lucide-react";
import { deleteAccount } from "../../api/userApi.js";

export default function DeleteAccountSection({ onDeleted }) {
  const [deleting, setDeleting] = useState(false);
  const remove = async () => {
    const password = window.prompt("Enter your current password to permanently delete your account and owned data:");
    if (!password || !window.confirm("This permanently deletes your account, devices, and owned data. Continue?")) return;
    setDeleting(true);
    try { await deleteAccount(password); onDeleted(); } finally { setDeleting(false); }
  };
  return <section className="rounded-[14px] border border-red-200 bg-red-50 p-5"><div className="flex items-center gap-3"><Trash2 size={17} className="text-red-600" /><div><h2 className="text-[15px] font-semibold text-red-800">Delete account</h2><p className="mt-1 text-[11px] text-red-700">Permanently deletes your account, devices, and owned data.</p></div></div><button type="button" onClick={remove} disabled={deleting} className="mt-4 h-10 rounded-[9px] border border-red-300 px-4 text-[12px] font-medium text-red-700 hover:bg-red-100 disabled:opacity-60">{deleting ? "Deleting..." : "Delete my account"}</button></section>;
}
