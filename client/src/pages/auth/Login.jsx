import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Mail, Lock } from "lucide-react";
import AuthShell from "../../components/auth/AuthShell.jsx";
import Input from "../../components/ui/Input.jsx";
import Button from "../../components/ui/Button.jsx";
import { useAuth } from "../../hooks/useAuth.js";
import { validateLogin } from "../../utils/validation.js";
import { useToast } from "../../hooks/useToast.js";

export default function Login() {
  const { login } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: "", password: "", remember: true });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  useEffect(() => {
    if (!location.state?.registrationSuccess) return;

    showToast("Account created successfully. Please sign in.");
    navigate(location.pathname, { replace: true, state: null });
  }, [location, navigate, showToast]);

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
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setFormError(err?.message || "Invalid email or password.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      image="https://images.unsplash.com/photo-1621905252507-b35492cc74b4?auto=format&fit=crop&w=1800&q=88"
      imageTone="bright"
      ghost="Sign in"
      headline="Smarter monitoring, better control."
      blurb="Track your devices, watch real-time data, and control your IoT fleet from anywhere."
      title="Smarter Monitoring, Better Control."
      description="Track your devices, monitor real-time data, and control your IoT devices from anywhere."
      visualContent={(
        <div className="mt-10 grid max-w-sm grid-cols-2 gap-3">
          {["Real-time Monitoring", "Remote Control", "Smart Alerts", "Reports & Analytics"].map((label) => (
            <div key={label} className="rounded-xl border border-white/10 bg-white/10 px-3.5 py-3 text-sm font-medium">
              {label}
            </div>
          ))}
        </div>
      )}
      formContent={(
        <>
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
              <Link to="/forgot-password" className="text-sm text-primary font-medium hover:underline">
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
        </>
      )}
    />
  );
}
