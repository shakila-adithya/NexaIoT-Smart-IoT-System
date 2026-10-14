import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, LockKeyhole, Mail, Satellite } from "lucide-react";
import Input from "../../components/ui/Input.jsx";
import Button from "../../components/ui/Button.jsx";
import { forgotPasswordRequest } from "../../api/authApi.js";
import { isValidEmail } from "../../utils/validation.js";

export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    if (!email.trim()) {
      setError("Email is required.");
      return;
    }
    if (!isValidEmail(email.trim())) {
      setError("Enter a valid email address.");
      return;
    }

    setSubmitting(true);
    try {
      await forgotPasswordRequest(email.trim());
      setSuccess(true);
    } catch {
      setError("Unable to process your request right now. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen grid lg:grid-cols-2">
      <div className="hidden lg:flex flex-col justify-center bg-slate-950 px-14 text-white relative overflow-hidden">
        <img
          src="https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=1600&q=85"
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
          <h1 className="mt-10 text-4xl font-bold leading-tight max-w-md">Secure access starts here.</h1>
          <p className="mt-4 text-blue-100 max-w-sm">
            Reset your password securely and get back to monitoring your IoT devices.
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
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <LockKeyhole size={22} />
          </div>
          <h2 className="mt-5 text-2xl font-bold text-ink">Forgot password?</h2>
          <p className="text-sm text-muted mt-1.5">Enter your email to receive secure reset instructions.</p>

          {success ? (
            <div className="mt-6 rounded-lg bg-primary/5 border border-primary/20 px-3.5 py-2.5 text-sm text-ink">
              If an account exists for this email, a password reset link has been sent.
            </div>
          ) : (
            <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
              <Input
                label="Email Address"
                type="email"
                icon={Mail}
                placeholder="Enter your email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                error={error}
                autoComplete="email"
              />
              <Button type="submit" className="w-full" disabled={submitting}>
                {submitting ? "Sending..." : "Send reset link"}
              </Button>
            </form>
          )}

          <p className="mt-6 text-center text-sm text-muted">
            Remember your password?{" "}
            <Link to="/login" className="text-primary font-medium hover:underline">
              Login
            </Link>
          </p>
          <Link to="/" className="mt-4 mx-auto inline-flex items-center gap-2 text-sm text-muted hover:text-ink">
            <ArrowLeft size={15} />
            Back to home
          </Link>
        </div>
      </div>
    </div>
  );
}
