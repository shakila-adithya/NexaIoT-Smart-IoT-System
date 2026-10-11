import { useState } from "react";
import { LockKeyhole, Save } from "lucide-react";
import { changePassword } from "../../api/userApi.js";
import { useToast } from "../../hooks/useToast.js";

export default function PasswordForm({ onUserUpdated }) {
  const { showToast } = useToast();
  const [form, setForm] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });
  const [saving, setSaving] = useState(false);

  const updateField = (event) => {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (form.newPassword.length < 8) {
      showToast("New password must contain at least 8 characters.", "error");
      return;
    }
    if (form.newPassword !== form.confirmPassword) {
      showToast("New passwords do not match.", "error");
      return;
    }

    setSaving(true);
    try {
      const updatedUser = await changePassword(form);
      onUserUpdated(updatedUser);
      setForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
      showToast("Password changed successfully.");
    } catch (error) {
      showToast(error.message || "Unable to change your password.", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-[0_6px_20px_rgba(10,48,72,0.05)]">
      <div className="flex items-center gap-3">
        <span className="flex h-9 w-9 items-center justify-center rounded-[10px] bg-[#eef9fb] text-[#08a9c4]"><LockKeyhole size={17} /></span>
        <div>
          <h2 className="text-[15px] font-semibold text-[#102a3a]">Change password</h2>
          <p className="mt-1 text-[11px] text-[#6b8290]">Keep your account secure with a strong password.</p>
        </div>
      </div>
      <div className="mt-5 grid gap-4 sm:grid-cols-3">
        {[
          ["currentPassword", "Current password"],
          ["newPassword", "New password"],
          ["confirmPassword", "Confirm new password"],
        ].map(([name, label]) => (
          <label key={name} className="text-[11px] font-medium text-[#526d7b]">
            {label}
            <input name={name} type="password" value={form[name]} onChange={updateField} required className="mt-1.5 h-10 w-full rounded-[9px] border border-[#dce8ee] bg-[#f8fbfd] px-3 text-[12px] text-[#102a3a] outline-none focus:border-[#08a9c4] focus:ring-2 focus:ring-[#08a9c4]/10" />
          </label>
        ))}
      </div>
      <button type="submit" disabled={saving} className="mt-5 inline-flex h-10 items-center gap-2 rounded-[9px] bg-[#08a9c4] px-4 text-[12px] font-medium text-white transition hover:bg-[#0799b2] disabled:cursor-not-allowed disabled:opacity-60">
        <Save size={14} />
        {saving ? "Saving..." : "Update password"}
      </button>
    </form>
  );
}
