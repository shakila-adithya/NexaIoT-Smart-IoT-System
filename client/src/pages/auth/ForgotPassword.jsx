import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, LockKeyhole, Mail } from "lucide-react";
import smartDeviceImage from "../../assets/dashboard/device-status/smart-home-controller.jpg";
import AuthShell from "../../components/auth/AuthShell.jsx";
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
    <AuthShell
      image={smartDeviceImage}
      imageTone="bright"
      ghost="Reset"
      headline="Locked out? It happens."
      blurb="Tell us your email and we'll send a secure link to set a new password."
      title="Secure access starts here."
      description="Reset your password securely and get back to monitoring your IoT devices."
      formContent={(
        <>
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
          <Link to="/" className="auth-back-link mt-4 mx-auto inline-flex items-center gap-2 text-sm text-muted hover:text-primary">
            <ArrowLeft size={15} />
            Back to home
          </Link>
        </>
      )}
    />
  );
}
