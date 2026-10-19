import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ImagePlus, Save, Trash2, UserRound } from "lucide-react";
import { deleteProfileImage, getCurrentUser, getProfileImage, updateProfile, uploadProfileImage } from "../../api/userApi.js";
import PasswordForm from "../../components/account/PasswordForm.jsx";
import Button from "../../components/ui/Button.jsx";
import ErrorState from "../../components/ui/ErrorState.jsx";
import PageHeader from "../../components/ui/PageHeader.jsx";
import SectionCard from "../../components/ui/SectionCard.jsx";
import Select from "../../components/ui/Select.jsx";
import { useAuth } from "../../hooks/useAuth.js";
import { useToast } from "../../hooks/useToast.js";

const timeZones = ["UTC", "Asia/Colombo", "Europe/London", "America/New_York", "America/Los_Angeles", "Asia/Tokyo"];

function displayName(user) {
  return user?.fullName || [user?.firstName, user?.lastName].filter(Boolean).join(" ") || "NexaIoT User";
}

function initials(name) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toUpperCase() || "N";
}

function formatMemberSince(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleDateString([], { month: "short", year: "numeric" });
}

function toForm(user) {
  return {
    firstName: user?.firstName || "",
    lastName: user?.lastName || "",
    phoneNumber: user?.phoneNumber || "",
    jobTitle: user?.jobTitle || "",
    department: user?.department || "",
    bio: user?.bio || "",
    timeZone: user?.timeZone || "UTC",
  };
}

export default function Profile() {
  const { user, updateUser, profileImageUrl } = useAuth();
  const { showToast } = useToast();
  const [profile, setProfile] = useState(user);
  const [form, setForm] = useState(() => toForm(user));
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [selectedImage, setSelectedImage] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [imageSaving, setImageSaving] = useState(false);
  const fileInputRef = useRef(null);

  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
  }, [previewUrl]);

  useEffect(() => {
    let mounted = true;
    getCurrentUser()
      .then((currentUser) => {
        if (!mounted) return;
        setProfile(currentUser);
        setForm(toForm(currentUser));
        updateUser(currentUser);
      })
      .catch((requestError) => mounted && setError(requestError.message || "Unable to load your profile."))
      .finally(() => mounted && setLoading(false));
    return () => { mounted = false; };
  }, [updateUser]);

  const name = useMemo(() => displayName(profile || user), [profile, user]);

  const updateField = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }));

  const handleImageChange = (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (!["image/jpeg", "image/png"].includes(file.type)) {
      showToast("Only JPEG and PNG images are supported.", "error");
      return;
    }
    if (file.size === 0 || file.size > 5 * 1024 * 1024) {
      showToast("Profile image must be between 1 byte and 5 MB.", "error");
      return;
    }
    setSelectedImage(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  const handleImageUpload = async () => {
    if (!selectedImage) return;
    setImageSaving(true);
    try {
      const updatedUser = await uploadProfileImage(selectedImage);
      const blob = await getProfileImage();
      const imageUrl = URL.createObjectURL(blob);
      updateUser(updatedUser, imageUrl);
      setProfile(updatedUser);
      setSelectedImage(null);
      setPreviewUrl(null);
      showToast("Profile photo updated successfully.");
    } catch (requestError) {
      showToast(requestError.message || "Unable to upload your profile photo.", "error");
    } finally {
      setImageSaving(false);
    }
  };

  const handleImageDelete = async () => {
    if (!profileImageUrl || !window.confirm("Remove your profile photo?")) return;
    setImageSaving(true);
    try {
      const updatedUser = await deleteProfileImage();
      updateUser(updatedUser, null);
      setProfile(updatedUser);
      showToast("Profile photo removed.");
    } catch (requestError) {
      showToast(requestError.message || "Unable to remove your profile photo.", "error");
    } finally {
      setImageSaving(false);
    }
  };

  const avatarUrl = previewUrl || profileImageUrl;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      const updatedUser = await updateProfile(form);
      setProfile(updatedUser);
      setForm(toForm(updatedUser));
      updateUser(updatedUser);
      showToast("Profile updated successfully.");
    } catch (requestError) {
      setError(requestError.message || "Unable to update your profile.");
      showToast(requestError.message || "Unable to update your profile.", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="w-full px-4 pb-9 pt-7 md:px-[30px]">
      <div className="mx-auto w-full max-w-[1144px]">
        <PageHeader tone="account" title="User profile" description="Manage your personal information and account security." />

        {loading ? <div className="mt-6 flex min-h-10 items-center gap-2 text-[12px] text-[#6b8290]" role="status" aria-live="polite"><span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-[#dce8ee] border-t-[#08a9c4]" aria-hidden="true" />Loading profile...</div> : null}
        {error ? <ErrorState className="mt-6" message={error} /> : null}

        {!loading ? <>
          <SectionCard className="mt-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
              <div className="relative shrink-0">
                {avatarUrl ? <img src={avatarUrl} alt={`${name} profile`} className="h-16 w-16 rounded-full object-cover" /> : <div className="flex h-16 w-16 items-center justify-center rounded-full bg-gradient-to-br from-[#08a9c4] to-[#087b9c] text-xl font-bold text-white">{initials(name)}</div>}
                {previewUrl ? <span className="absolute -bottom-1 -right-1 rounded-full bg-[#f59e0b] px-1.5 py-0.5 text-[8px] font-semibold text-white">Preview</span> : null}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2"><h2 className="text-[18px] font-semibold text-[#102a3a]">{name}</h2><span className="rounded-full bg-[#e8f8fb] px-2.5 py-1 text-[9px] font-semibold uppercase tracking-wide text-[#087b9c]">{profile?.role || "USER"}</span></div>
                <p className="mt-1 text-[12px] text-[#6b8290]">{profile?.email || "—"}</p>
                <p className="mt-2 text-[10px] text-[#8397a2]">{profile?.timeZone || "UTC"} · Member since {formatMemberSince(profile?.createdAt)}</p>
              </div>
              <div className="flex flex-wrap gap-2 sm:justify-end">
                <input ref={fileInputRef} type="file" accept="image/jpeg,image/png" onChange={handleImageChange} className="hidden" />
                <button type="button" onClick={() => fileInputRef.current?.click()} disabled={imageSaving} className="inline-flex h-9 items-center gap-1.5 rounded-[9px] border border-[#dce8ee] px-3 text-[11px] font-medium text-[#102a3a] hover:bg-[#f8fbfd] disabled:opacity-60"><ImagePlus size={14} />Change photo</button>
                {selectedImage ? <button type="button" onClick={handleImageUpload} disabled={imageSaving} className="inline-flex h-9 items-center rounded-[9px] bg-[#08a9c4] px-3 text-[11px] font-medium text-white hover:bg-[#0799b2] disabled:opacity-60">{imageSaving ? "Uploading..." : "Upload photo"}</button> : null}
                {profileImageUrl ? <button type="button" onClick={handleImageDelete} disabled={imageSaving} className="inline-flex h-9 items-center gap-1.5 rounded-[9px] border border-red-200 px-3 text-[11px] font-medium text-red-600 hover:bg-red-50 disabled:opacity-60"><Trash2 size={14} />Remove</button> : null}
                <Link to="/settings" className="self-center text-[11px] font-medium text-[#08a9c4] hover:text-[#0799b2]">Account settings</Link>
              </div>
            </div>
          </SectionCard>

          <form onSubmit={handleSubmit} className="mt-5 rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-card)]">
            <div className="flex items-center gap-3"><span className="topic-icon-account flex h-9 w-9 items-center justify-center rounded-[10px]"><UserRound size={17} /></span><div><h2 className="topic-heading topic-account text-[15px] font-semibold text-[#102a3a]">Personal information</h2><p className="mt-1 text-[11px] text-[#6b8290]">Keep your profile details up to date.</p></div></div>
            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              {[['firstName','First name'],['lastName','Last name'],['phoneNumber','Phone number'],['jobTitle','Job title'],['department','Department']].map(([nameLabel, label]) => (
                <label key={nameLabel} className="text-[11px] font-medium text-[#526d7b]">{label}<input name={nameLabel} value={form[nameLabel]} onChange={updateField} className="mt-1.5 h-10 w-full rounded-[9px] border border-[#dce8ee] bg-[#f8fbfd] px-3 text-[12px] text-[#102a3a] outline-none focus:border-[#08a9c4] focus:ring-2 focus:ring-[#08a9c4]/10" /></label>
              ))}
              <label className="text-[11px] font-medium text-[#526d7b]">Email<input value={profile?.email || ""} readOnly className="mt-1.5 h-10 w-full cursor-not-allowed rounded-[9px] border border-[#dce8ee] bg-[#eef4f6] px-3 text-[12px] text-[#6b8290]" /></label>
              <Select name="timeZone" label="Time zone" value={form.timeZone} onChange={(value) => updateField({ target: { name: "timeZone", value } })} options={timeZones.map((zone) => ({ value: zone, label: zone }))} />
              <label className="text-[11px] font-medium text-[#526d7b] sm:col-span-2">Bio<textarea name="bio" value={form.bio} onChange={updateField} rows={3} className="mt-1.5 w-full resize-y rounded-[9px] border border-[#dce8ee] bg-[#f8fbfd] px-3 py-2 text-[12px] text-[#102a3a] outline-none focus:border-[#08a9c4]" /></label>
            </div>
            <Button type="submit" disabled={saving} loading={saving} loadingLabel="Saving..." icon={Save} className="mt-5" >Save changes</Button>
          </form>

          <div className="mt-5"><PasswordForm onUserUpdated={updateUser} /></div>
        </> : null}
      </div>
    </div>
  );
}
