import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ArrowLeft, Lock, ShieldCheck } from "lucide-react";
import AuthShell from "../../components/auth/AuthShell.jsx";
import Input from "../../components/ui/Input.jsx";
import Button from "../../components/ui/Button.jsx";
import { resetPasswordRequest } from "../../api/authApi.js";

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") || "";
  const [form, setForm] = useState({ newPassword: "", confirmPassword: "" });
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    if (!token) {
      setError("This password reset link is invalid or has expired.");
      return;
    }
    if (form.newPassword.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    if (form.newPassword !== form.confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setSubmitting(true);
    try {
      await resetPasswordRequest(token, form.newPassword);
      setSuccess(true);
    } catch {
      setError("This password reset link is invalid or has expired.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      image="https://images.unsplash.com/photo-1485827404703-89b55fcc595e?auto=format&fit=crop&w=1400&q=85"
      imageAlt="Friendly service robot"
      title="Secure access, made simple."
      description="Set a new password and get back to monitoring your connected devices with confidence."
      visualContent={(
        <div className="mt-8 inline-flex items-center gap-2 rounded-xl border border-white/10 bg-white/10 px-3.5 py-3 text-sm font-medium">
          <ShieldCheck size={17} />
          Your account stays protected
        </div>
      )}
      formContent={(
        <>
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <Lock size={22} />
          </div>
          <h2 className="mt-5 text-2xl font-bold text-ink">Reset password</h2>
          <p className="text-sm text-muted mt-1.5">Choose a new password for your NexaIoT account.</p>

          {success ? (
            <div className="mt-6 space-y-4">
              <div className="rounded-lg bg-primary/5 border border-primary/20 px-3.5 py-2.5 text-sm text-ink">
                Your password has been reset successfully.
              </div>
              <Link to="/login" className="inline-flex items-center gap-2 text-sm text-primary font-medium hover:underline">
                <ArrowLeft size={15} />
                Back to Login
              </Link>
            </div>
          ) : (
            <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
              {!token && <p className="text-sm text-danger bg-danger/5 border border-danger/20 rounded-xl px-3.5 py-2.5">This password reset link is invalid or has expired.</p>}
              <Input
                label="New password"
                type="password"
                icon={Lock}
                placeholder="At least 8 characters"
                value={form.newPassword}
                onChange={(event) => setForm((current) => ({ ...current, newPassword: event.target.value }))}
                autoComplete="new-password"
              />
              <Input
                label="Confirm new password"
                type="password"
                icon={Lock}
                placeholder="Repeat your new password"
                value={form.confirmPassword}
                onChange={(event) => setForm((current) => ({ ...current, confirmPassword: event.target.value }))}
                error={error}
                autoComplete="new-password"
              />
              <Button type="submit" className="w-full" disabled={submitting || !token}>
                {submitting ? "Resetting..." : "Reset password"}
              </Button>
              <Link to="/login" className="inline-flex items-center gap-2 text-sm text-primary font-medium hover:underline">
                <ArrowLeft size={15} />
                Back to Login
              </Link>
            </form>
          )}
        </>
      )}
    />
  );
}
