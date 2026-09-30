import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Satellite, Mail, Lock, User } from "lucide-react";
import Input from "../../components/ui/Input.jsx";
import Button from "../../components/ui/Button.jsx";
import { useAuth } from "../../hooks/useAuth.js";
import { validateRegister } from "../../utils/validation.js";
import { useToast } from "../../context/ToastContext.jsx";

export default function Register() {
  const { register } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: "", email: "", password: "", confirmPassword: "", agree: false });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const errs = validateRegister(form);
    setErrors(errs);
    if (Object.keys(errs).length > 0) return;

    setSubmitting(true);
    try {
      await register(form);
      showToast("Account created successfully");
      navigate("/", { replace: true });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen grid lg:grid-cols-2">
      <div className="hidden lg:flex flex-col justify-center bg-slate-950 px-14 text-white relative overflow-hidden">
        <img
          src="https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?auto=format&fit=crop&w=1600&q=85"
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
          <h1 className="mt-10 text-4xl font-bold leading-tight max-w-md">Build Your Smart Future.</h1>
          <p className="mt-4 text-blue-100 max-w-sm">
            Create your account to start monitoring and controlling your IoT devices in minutes.
          </p>
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
          <h2 className="text-2xl font-bold text-ink">Create your account</h2>
          <p className="text-sm text-muted mt-1.5">Join us and get started today</p>

          <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
            <Input
              label="Full Name"
              icon={User}
              placeholder="Enter your full name"
              value={form.fullName}
              onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
              error={errors.fullName}
            />
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
              placeholder="Create a password"
              value={form.password}
              onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
              error={errors.password}
            />
            <Input
              label="Confirm Password"
              type="password"
              icon={Lock}
              placeholder="Confirm your password"
              value={form.confirmPassword}
              onChange={(e) => setForm((f) => ({ ...f, confirmPassword: e.target.value }))}
              error={errors.confirmPassword}
            />
            <div>
              <label className="flex items-start gap-2 text-sm text-ink">
                <input
                  type="checkbox"
                  checked={form.agree}
                  onChange={(e) => setForm((f) => ({ ...f, agree: e.target.checked }))}
                  className="mt-0.5 rounded border-border text-primary focus:ring-primary"
                />
                <span>
                  I agree to the{" "}
                  <Link to="/terms" className="text-primary hover:underline">
                    Terms
                  </Link>{" "}
                  and{" "}
                  <Link to="/privacy" className="text-primary hover:underline">
                    Privacy Policy
                  </Link>
                </span>
              </label>
              {errors.agree && <p className="mt-1.5 text-xs text-danger">{errors.agree}</p>}
            </div>
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? "Creating account..." : "Create Account"}
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-muted">
            Already have an account?{" "}
            <Link to="/login" className="text-primary font-medium hover:underline">
              Login
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
