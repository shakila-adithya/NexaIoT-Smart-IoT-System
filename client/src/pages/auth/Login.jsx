import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Satellite, Mail, Lock, Thermometer, Radio, Bell, BarChart3 } from "lucide-react";
import Input from "../../components/ui/Input.jsx";
import Button from "../../components/ui/Button.jsx";
import { useAuth } from "../../hooks/useAuth.js";
import { validateLogin } from "../../utils/validation.js";
import { useToast } from "../../context/ToastContext.jsx";

export default function Login() {
  const { login } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: "", password: "", remember: true });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  const submit = async (e) => {
    e.preventDefault();
    const errs = validateLogin(form);
    setErrors(errs);
    setFormError("");
    if (Object.keys(errs).length > 0) return;

    setSubmitting(true);
    try {
      await login(form.email, form.password, form.remember);
      showToast("Welcome back!");
      navigate("/", { replace: true });
    } catch (err) {
      setFormError(err?.message || "Invalid email or password.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen grid lg:grid-cols-2">
      <div className="hidden lg:flex flex-col justify-center bg-slate-950 px-14 text-white relative overflow-hidden">
        <img
          src="https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=1600&q=85"
          alt=""
          aria-hidden="true"
          className="absolute inset-0 h-full w-full object-cover"
        />
        <div className="absolute inset-0 bg-slate-950/70" />
        <div className="relative z-10">
          <Link to="/" className="flex items-center gap-2 font-bold text-xl">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/15">
              <Satellite size={18} />
            </span>
            NexaIoT
          </Link>
          <h1 className="mt-10 text-4xl font-bold leading-tight max-w-md">Smarter Monitoring, Better Control.</h1>
          <p className="mt-4 text-blue-100 max-w-sm">
            Track your devices, monitor real-time data, and control your IoT devices from anywhere.
          </p>
          <div className="mt-10 grid grid-cols-2 gap-4 max-w-sm">
            {[
              { icon: Thermometer, label: "Real-time Monitoring" },
              { icon: Radio, label: "Remote Control" },
              { icon: Bell, label: "Smart Alerts" },
              { icon: BarChart3, label: "Reports & Analytics" },
            ].map((f) => (
              <div key={f.label} className="flex items-center gap-2.5 bg-white/10 rounded-xl px-3.5 py-3">
                <f.icon size={16} />
                <span className="text-sm font-medium">{f.label}</span>
              </div>
            ))}
          </div>
        </div>
        <div className="absolute -bottom-24 -right-24 h-72 w-72 rounded-full bg-white/10" />
        <div className="absolute -top-16 -left-10 h-52 w-52 rounded-full bg-white/10" />
      </div>

      <div className="flex items-center justify-center px-6 py-16">
        <div className="w-full max-w-sm">
          <div className="lg:hidden flex items-center gap-2 font-bold text-ink text-lg mb-8">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-white">
              <Satellite size={18} />
            </span>
            NexaIoT
          </div>
          <h2 className="text-2xl font-bold text-ink">Welcome back</h2>
          <p className="text-sm text-muted mt-1.5">Sign in to your account to continue</p>

          {formError && (
            <p className="mt-4 text-sm text-danger bg-danger/5 border border-danger/20 rounded-xl px-3.5 py-2.5">
              {formError}
            </p>
          )}

          <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
            <Input
              label="Email Address"
              type="email"
              icon={Mail}
              placeholder="Enter your email"
              value={form.email}
              onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
              error={errors.email}
            />
            <Input
              label="Password"
              type="password"
              icon={Lock}
              placeholder="Enter your password"
              value={form.password}
              onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
              error={errors.password}
            />
            <div className="flex items-center justify-between">
              <label className="flex items-center gap-2 text-sm text-ink">
                <input
                  type="checkbox"
                  checked={form.remember}
                  onChange={(e) => setForm((f) => ({ ...f, remember: e.target.checked }))}
                  className="rounded border-border text-primary focus:ring-primary"
                />
                Remember me
              </label>
              <Link to="#" className="text-sm text-primary font-medium hover:underline">
                Forgot password?
              </Link>
            </div>
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? "Signing in..." : "Login"}
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-muted">
            Don't have an account?{" "}
            <Link to="/register" className="text-primary font-medium hover:underline">
              Register
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
