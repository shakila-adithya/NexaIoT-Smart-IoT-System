import { useState } from "react";
import { Mail, LifeBuoy, MapPin } from "lucide-react";
import Card from "../../components/ui/Card.jsx";
import Input from "../../components/ui/Input.jsx";
import Button from "../../components/ui/Button.jsx";
import EditorialVisual from "../../components/ui/EditorialVisual.jsx";
import { useToast } from "../../hooks/useToast.js";

const infoCards = [
  { icon: Mail, title: "Email", value: "hello@nexaiot.dev" },
  { icon: LifeBuoy, title: "Support", value: "support@nexaiot.dev" },
  { icon: MapPin, title: "Location", value: "Remote-first team" },
];

export default function Contact() {
  const { showToast } = useToast();
  const [form, setForm] = useState({ name: "", email: "", subject: "", message: "" });
  const [errors, setErrors] = useState({});

  const submit = (e) => {
    e.preventDefault();
    const errs = {};
    if (!form.name.trim()) errs.name = "Name is required.";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errs.email = "Enter a valid email address.";
    if (!form.message.trim()) errs.message = "Message is required.";
    setErrors(errs);
    if (Object.keys(errs).length === 0) {
      showToast("Demo validated. No message was sent.");
      setForm({ name: "", email: "", subject: "", message: "" });
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-16 sm:py-20 public-showcase-page">
      <div className="grid lg:grid-cols-[1fr_0.82fr] gap-10 lg:gap-16 items-center mb-12">
        <div className="max-w-xl">
          <p className="showcase-kicker">NEXAIOT / CONTACT</p>
          <h1 className="text-4xl font-bold text-ink tracking-tight">Get in touch</h1>
          <p className="mt-4 text-lg text-muted">
            Try the contact form below. This frontend demo validates your details but does not send messages.
          </p>
        </div>
        <EditorialVisual
          image="https://images.unsplash.com/photo-1556761175-b413da4baf72?auto=format&fit=crop&w=1100&q=82"
          imageAlt="Team meeting to plan connected technology projects"
          detailImage="https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=700&q=80"
          detailAlt="Laptop used for remote technical collaboration"
          label="Talk to our IoT team"
        />
      </div>

      <div className="grid lg:grid-cols-[1fr_1.2fr] gap-10">
        <div className="space-y-4">
          {infoCards.map((c) => (
            <Card key={c.title} className="p-5 flex items-center gap-4">
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary flex-shrink-0">
                <c.icon size={19} />
              </span>
              <div>
                <p className="text-sm font-semibold text-ink">{c.title}</p>
                <p className="text-sm text-muted">{c.value}</p>
              </div>
            </Card>
          ))}
        </div>

        <Card className="p-7">
          <form onSubmit={submit} className="space-y-4" noValidate>
            <div className="grid sm:grid-cols-2 gap-4">
              <Input
                label="Name"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                error={errors.name}
              />
              <Input
                label="Email"
                type="email"
                value={form.email}
                onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                error={errors.email}
              />
            </div>
            <Input
              label="Subject"
              value={form.subject}
              onChange={(e) => setForm((f) => ({ ...f, subject: e.target.value }))}
            />
            <div>
              <label htmlFor="message" className="block text-sm font-medium text-ink mb-1.5">
                Message
              </label>
              <textarea
                id="message"
                rows={5}
                value={form.message}
                onChange={(e) => setForm((f) => ({ ...f, message: e.target.value }))}
                className={`w-full rounded-xl border px-3.5 py-2.5 text-sm outline-none ${
                  errors.message ? "border-danger" : "border-border focus:border-primary"
                }`}
              />
              {errors.message && <p className="mt-1.5 text-xs text-danger">{errors.message}</p>}
            </div>
            <Button type="submit" className="w-full sm:w-auto">
              Validate Message
            </Button>
          </form>
        </Card>
      </div>
    </div>
  );
}
