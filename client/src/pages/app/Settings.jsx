import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Check, LockKeyhole, Settings as SettingsIcon, UserRound } from "lucide-react";
import { getCurrentUser, updatePreferences } from "../../api/userApi.js";
import PasswordForm from "../../components/account/PasswordForm.jsx";
import DeleteAccountSection from "../../components/account/DeleteAccountSection.jsx";
import ErrorState from "../../components/ui/ErrorState.jsx";
import PageHeader from "../../components/ui/PageHeader.jsx";
import { useAuth } from "../../hooks/useAuth.js";
import { useToast } from "../../hooks/useToast.js";
import {
  DATE_FORMATS,
  DEFAULT_USER_PREFERENCES,
  normalizeUserPreferences,
  TEMPERATURE_UNITS,
  validateUserPreferences,
} from "../../config/userPreferences.js";

function memberSince(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleDateString([], { month: "short", day: "numeric", year: "numeric" });
}

export default function Settings() {
  const { user, updateUser, logout } = useAuth();
  const { showToast } = useToast();
  const [settingsUser, setSettingsUser] = useState(user);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [preferences, setPreferences] = useState(() => normalizeUserPreferences(user));
  const [preferenceErrors, setPreferenceErrors] = useState({});

  useEffect(() => {
    let mounted = true;
    getCurrentUser()
      .then((currentUser) => {
        if (!mounted) return;
        setSettingsUser(currentUser);
        setPreferences(normalizeUserPreferences(currentUser));
        updateUser(currentUser);
      })
      .catch((requestError) => mounted && setError(requestError.message || "Unable to load settings."))
      .finally(() => mounted && setLoading(false));
    return () => { mounted = false; };
  }, [updateUser]);

  const savePreferences = async () => {
    const validationErrors = validateUserPreferences(preferences);
    if (Object.keys(validationErrors).length > 0) {
      setPreferenceErrors(validationErrors);
      return;
    }
    setSaving(true);
    setError("");
    setPreferenceErrors({});
    try {
      const updatedUser = await updatePreferences(preferences);
      setSettingsUser(updatedUser);
      setPreferences(normalizeUserPreferences(updatedUser));
      updateUser(updatedUser);
      showToast("Preferences saved successfully.");
    } catch (requestError) {
      setError(requestError.message || "Unable to save preferences.");
      showToast(requestError.message || "Unable to save preferences.", "error");
    } finally {
      setSaving(false);
    }
  };

  const toggle = (name) => {
    if (saving) return;
    setPreferences((current) => ({ ...current, [name]: !current[name] }));
  };

  const updatePreference = (event) => {
    const { name, value } = event.target;
    setPreferences((current) => ({ ...current, [name]: value }));
    setPreferenceErrors((current) => {
      if (!current[name]) return current;
      const next = { ...current };
      delete next[name];
      return next;
    });
  };


  return (
    <div className="w-full px-4 pb-9 pt-7 md:px-[30px]">
      <div className="mx-auto w-full max-w-[1144px]">
        <PageHeader tone="settings" title="Settings" description="Manage your account, security, and application preferences." />
        {error ? <ErrorState className="mt-6" message={error} /> : null}
        {loading ? <div className="mt-6 flex min-h-10 items-center gap-2 text-[12px] text-[#6b8290]" role="status" aria-live="polite"><span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-[#dce8ee] border-t-[#08a9c4]" aria-hidden="true" />Loading settings...</div> : null}
        {!loading ? <div className="mt-6 grid gap-5 lg:grid-cols-[210px_minmax(0,1fr)]">
          <nav className="h-fit rounded-[14px] border border-[#dce8ee] bg-white p-2 shadow-[0_6px_20px_rgba(10,48,72,0.05)]">
            {[['#profile','Profile',UserRound],['#account','Account',SettingsIcon],['#security','Security',LockKeyhole],['#preferences','System preferences',SettingsIcon]].map(([href, label, Icon], index) => <a key={href} href={href} className={`flex items-center gap-2.5 rounded-[9px] px-3 py-2.5 text-[11px] ${index === 0 ? 'bg-[#e8f8fb] font-semibold text-[#087b9c]' : 'text-[#6b8290] hover:bg-[#f4f8fb]'}`}><Icon size={14} />{label}</a>)}
          </nav>
          <div className="space-y-5">
            <section id="profile" className="rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-[0_6px_20px_rgba(10,48,72,0.05)]"><div className="flex items-center gap-3"><span className="flex h-9 w-9 items-center justify-center rounded-[10px] bg-[#eef9fb] text-[#08a9c4]"><UserRound size={17} /></span><div><h2 className="text-[15px] font-semibold text-[#102a3a]">Profile</h2><p className="mt-1 text-[11px] text-[#6b8290]">Your personal information is managed on the profile page.</p></div></div><Link to="/profile" className="mt-4 inline-flex h-9 items-center rounded-[9px] border border-[#dce8ee] px-3 text-[11px] font-medium text-[#102a3a] hover:bg-[#f8fbfd]">Open profile</Link></section>
            <section id="account" className="rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-[0_6px_20px_rgba(10,48,72,0.05)]"><h2 className="topic-heading topic-account text-[15px] font-semibold text-[#102a3a]">Account</h2><div className="mt-4 grid gap-4 sm:grid-cols-3"><div><div className="text-[10px] uppercase tracking-wide text-[#8ba0ab]">Email</div><div className="mt-1 break-all text-[12px] text-[#102a3a]">{settingsUser?.email || "—"}</div></div><div><div className="text-[10px] uppercase tracking-wide text-[#8ba0ab]">Role</div><div className="mt-1 text-[12px] text-[#102a3a]">{settingsUser?.role || "USER"}</div></div><div><div className="text-[10px] uppercase tracking-wide text-[#8ba0ab]">Member since</div><div className="mt-1 text-[12px] text-[#102a3a]">{memberSince(settingsUser?.createdAt)}</div></div></div></section>
            <section id="security"><PasswordForm onUserUpdated={updateUser} /></section>
            <DeleteAccountSection onDeleted={logout} />
            <section id="preferences" className="rounded-[14px] border border-[#dce8ee] bg-white p-5 shadow-[0_6px_20px_rgba(10,48,72,0.05)]"><h2 className="text-[15px] font-semibold text-[#102a3a]">System preferences</h2><p className="mt-1 text-[11px] text-[#6b8290]">These preferences are persisted to your account. Changes are applied only after the backend confirms the save.</p><div className="mt-4 grid gap-4 sm:grid-cols-2"><label className="text-[11px] font-medium text-[#526d7b]">Temperature unit<select name="temperatureUnit" value={preferences.temperatureUnit || DEFAULT_USER_PREFERENCES.temperatureUnit} onChange={updatePreference} disabled={saving} aria-invalid={Boolean(preferenceErrors.temperatureUnit)} className="mt-1.5 h-10 w-full rounded-[9px] border border-[#dce8ee] bg-[#f8fbfd] px-3 text-[12px] text-[#102a3a] outline-none focus:border-[#08a9c4] disabled:opacity-60">{TEMPERATURE_UNITS.map((value) => <option key={value} value={value}>{value === "CELSIUS" ? "Celsius (°C)" : "Fahrenheit (°F)"}</option>)}</select>{preferenceErrors.temperatureUnit ? <span className="mt-1 block text-[10px] font-normal text-red-600" role="alert">{preferenceErrors.temperatureUnit}</span> : null}</label><label className="text-[11px] font-medium text-[#526d7b]">Date format<select name="dateFormat" value={preferences.dateFormat || DEFAULT_USER_PREFERENCES.dateFormat} onChange={updatePreference} disabled={saving} aria-invalid={Boolean(preferenceErrors.dateFormat)} className="mt-1.5 h-10 w-full rounded-[9px] border border-[#dce8ee] bg-[#f8fbfd] px-3 text-[12px] text-[#102a3a] outline-none focus:border-[#08a9c4] disabled:opacity-60">{DATE_FORMATS.map((value) => <option key={value} value={value}>{value}</option>)}</select>{preferenceErrors.dateFormat ? <span className="mt-1 block text-[10px] font-normal text-red-600" role="alert">{preferenceErrors.dateFormat}</span> : null}</label></div><div className="mt-4 divide-y divide-[#edf2f4]">{[['compactMode','Compact layout where supported','Use denser spacing in views that support compact mode'],['liveChartAnimations','Live chart animations','Animate telemetry chart updates when supported'],['dashboardAutoRefresh','Dashboard auto-refresh','Refresh dashboard device and alert data every 5 seconds']].map(([name, label, description]) => <div key={name} className="flex items-center justify-between gap-4 py-3"><div><div className="text-[12px] font-medium text-[#102a3a]">{label}</div><div className="mt-1 text-[10px] text-[#6b8290]">{description}</div></div><button type="button" role="switch" aria-checked={preferences[name]} disabled={saving} onClick={() => toggle(name)} className={`flex h-6 w-11 shrink-0 items-center rounded-full p-1 transition disabled:cursor-not-allowed disabled:opacity-60 ${preferences[name] ? 'bg-[#08a9c4]' : 'bg-[#c7d6dc]'}`}><span className={`h-4 w-4 rounded-full bg-white shadow-sm transition ${preferences[name] ? 'translate-x-5' : ''}`}><Check size={10} className={preferences[name] ? 'm-[3px] text-[#08a9c4]' : 'hidden'} /></span></button></div>)}</div><button type="button" disabled={saving} onClick={savePreferences} className="mt-4 h-10 rounded-[9px] bg-[#08a9c4] px-4 text-[12px] font-medium text-white hover:bg-[#0799b2] disabled:cursor-not-allowed disabled:opacity-60">{saving ? "Saving..." : "Save preferences"}</button></section>
          </div>
        </div> : null}
      </div>
    </div>
  );
}
