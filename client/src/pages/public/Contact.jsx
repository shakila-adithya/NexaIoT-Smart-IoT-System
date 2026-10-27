import { useState } from "react";
import { Mail, LifeBuoy, MapPin } from "lucide-react";
import Card from "../../components/public/PublicCard.jsx";
import Input from "../../components/ui/Input.jsx";
import Button from "../../components/public/PublicButton.jsx";
import PageHero from "../../components/public/PageHero.jsx";
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
    if (!form.name) errs.name = "Name is required.";
    if (!form.email) errs.email = "Email is required.";
    if (!form.message) errs.message = "Message is required.";
    setErrors(errs);
    if (Object.keys(errs).length === 0) {
      showToast("Form validated. Message delivery is not configured.", "info");
    }
  };

  return (
    <div className="contact-page">
      <PageHero
        ghost="Contact"
        image="https://images.unsplash.com/photo-1556761175-b413da4baf72?auto=format&fit=crop&w=1800&q=82"
        imageAlt="Team meeting to plan connected technology projects"
        title="Get in touch"
        text="Questions about NexaIoT? Send us a message and we'll get back to you."
      />
      <div className="mx-auto max-w-5xl px-6 py-20 sm:px-10">
      <div className="grid lg:grid-cols-[1fr_1.2fr] gap-10">
        <div className="space-y-4">
          {infoCards.map((c) => (
            <Card key={c.title} className="p-5 flex items-center gap-4">
              <span className="public-feature-icon flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full">
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
                className={`w-full rounded-2xl border bg-subtle text-ink placeholder:text-muted px-5 py-3 text-sm outline-none focus:border-primary-light ${
                  errors.message ? "border-danger" : "border-border focus:border-primary"
                }`}
              />
              {errors.message && <p className="mt-1.5 text-xs text-danger">{errors.message}</p>}
            </div>
            <Button type="submit" className="contact-submit-button w-full sm:w-auto">
              Send Message
            </Button>
          </form>
        </Card>
      </div>
      </div>
    </div>
  );
}


