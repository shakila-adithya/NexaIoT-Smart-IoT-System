import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Mail, Lock, User } from "lucide-react";
import AuthShell from "../../components/auth/AuthShell.jsx";
import Input from "../../components/ui/Input.jsx";
import Button from "../../components/ui/Button.jsx";
import { useAuth } from "../../hooks/useAuth.js";
import { validateRegister } from "../../utils/validation.js";

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({
    fullName: "",
    email: "",
    password: "",
    confirmPassword: "",
    agree: false,
  });

  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const submit = async (e) => {
    e.preventDefault();

    setFormError("");

    const errs = validateRegister(form);
    setErrors(errs);

    if (Object.keys(errs).length > 0) {
      return;
    }

    setSubmitting(true);

    try {
      await register(form);
      navigate("/login", {
        replace: true,
        state: { registrationSuccess: true },
      });
    } catch (err) {
      setFormError(
        err?.message || "Unable to create account. Please try again."
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      image="https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?auto=format&fit=crop&w=1600&q=85"
      ghost="Join"
      headline="Build your smart future."
      blurb="Create your account to start monitoring and controlling your IoT devices in minutes."
      title="Build Your Smart Future."
      description="Create your account to start monitoring and controlling your IoT devices in minutes."
      formContent={(
        <>
          <h2 className="text-2xl font-bold text-ink">
            Create your account
          </h2>

          <p className="text-sm text-muted mt-1.5">
            Join us and get started today
          </p>

          {formError && (
            <div className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">
              {formError}
            </div>
          )}

          <form
            onSubmit={submit}
            className="mt-6 space-y-4"
            noValidate
          >
            <Input
              label="Full Name"
              icon={User}
              placeholder="Enter your full name"
              value={form.fullName}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  fullName: e.target.value,
                }))
              }
              error={errors.fullName}
            />

            <Input
              label="Email Address"
              type="email"
              icon={Mail}
              placeholder="Enter your email"
              value={form.email}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  email: e.target.value,
                }))
              }
              error={errors.email}
            />

            <Input
              label="Password"
              type="password"
              icon={Lock}
              placeholder="Create a password"
              value={form.password}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  password: e.target.value,
                }))
              }
              error={errors.password}
            />

            <Input
              label="Confirm Password"
              type="password"
              icon={Lock}
              placeholder="Confirm your password"
              value={form.confirmPassword}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  confirmPassword: e.target.value,
                }))
              }
              error={errors.confirmPassword}
            />

            <div>
              <label className="flex items-start gap-2 text-sm text-ink">
                <input
                  type="checkbox"
                  checked={form.agree}
                  onChange={(e) =>
                    setForm((f) => ({
                      ...f,
                      agree: e.target.checked,
                    }))
                  }
                  className="mt-0.5 rounded border-border text-primary focus:ring-primary"
                />

                <span>
                  I agree to the{" "}
                  <Link
                    to="/terms"
                    className="text-primary hover:underline"
                  >
                    Terms
                  </Link>{" "}
                  and{" "}
                  <Link
                    to="/privacy"
                    className="text-primary hover:underline"
                  >
                    Privacy Policy
                  </Link>
                </span>
              </label>

              {errors.agree && (
                <p className="mt-1.5 text-xs text-danger">
                  {errors.agree}
                </p>
              )}
            </div>

            <Button
              type="submit"
              className="w-full"
              disabled={submitting}
            >
              {submitting
                ? "Creating account..."
                : "Create Account"}
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-muted">
            Already have an account?{" "}
            <Link
              to="/login"
              className="text-primary font-medium hover:underline"
            >
              Login
            </Link>
          </p>
        </>
      )}
    />
  );
}
