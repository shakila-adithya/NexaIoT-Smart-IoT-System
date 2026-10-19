import { Link } from "react-router-dom";
import { ArrowUpRight, BarChart3, Battery, Bell, Building2, Cpu, Droplets, Factory, Gauge, Leaf, MapPin, Radio, ShieldCheck, Sprout, Thermometer, Wifi, Zap } from "lucide-react";
import Button, { ArrowButton } from "../../components/public/PublicButton.jsx";
import { solutions } from "../../data/mockData.js";

const solutionIcons = { buildings: Building2, agriculture: Sprout, industrial: Factory, energy: Zap, environmental: Leaf, asset: MapPin };
const solutionImages = {
  buildings: "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=900&q=80",
  agriculture: "https://images.unsplash.com/photo-1416879595882-3373a0480b5b?auto=format&fit=crop&w=900&q=80",
  industrial: "https://images.unsplash.com/photo-1764835994645-3faa2c40f708?auto=format&fit=crop&w=900&q=80",
  energy: "https://images.unsplash.com/photo-1473341304170-971dccb5ac1e?auto=format&fit=crop&w=900&q=80",
  environmental: "https://images.unsplash.com/photo-1758129949324-6f27ade9ff1a?auto=format&fit=crop&w=900&q=80",
  asset: "https://images.unsplash.com/photo-1682559736721-c2e77ff4c650?auto=format&fit=crop&w=900&q=80",
};

const heroStats = [
  ["12", "Devices online"],
  ["248", "Events per minute"],
  ["99.9%", "System uptime"],
];

const readings = [
  [Thermometer, "Temperature", "24.6°C", "Stable"],
  [Droplets, "Humidity", "41%", "Optimal"],
  [Zap, "Energy draw", "1.2 kW", "In range"],
  [Battery, "Fleet battery", "88%", "Healthy"],
  [Gauge, "Pressure", "101.3 kPa", "Normal"],
  [Cpu, "Connected nodes", "12 / 12", "All reporting"],
];

export default function Home() {
  return (
    <div>
      {/* Hero */}
      <section className="p-3 sm:p-4">
        <div className="frame flex min-h-[max(700px,calc(100svh-1.5rem))] flex-col justify-end">
          <img
            className="frame-photo"
            src="https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?auto=format&fit=crop&w=2000&q=88"
            alt="Engineer monitoring connected equipment in a modern industrial facility"
            fetchPriority="high"
          />
          <div className="frame-grade" />

          <h1 className="absolute left-1/2 top-[15%] -translate-x-1/2 text-center">
            <span aria-hidden="true" className="ghost-word enter-ghost text-[clamp(3.5rem,15.5vw,15rem)]">NexaIoT</span>
            <span className="sr-only">NexaIoT: smart device monitoring and control</span>
          </h1>

          <div className="relative z-10 mx-auto grid w-full max-w-7xl gap-10 px-6 pb-10 text-white sm:px-10 lg:grid-cols-[1fr_auto] lg:items-end">
            <div>
              <dl className="enter-rise enter-d1 flex flex-wrap gap-x-12 gap-y-5">
                {heroStats.map(([value, label]) => (
                  <div key={label} className="flex flex-col-reverse">
                    <dt className="mt-1 text-sm text-white/65">{label}</dt>
                    <dd className="text-4xl font-semibold tracking-tight sm:text-5xl">{value}</dd>
                  </div>
                ))}
              </dl>

              <div className="enter-rise enter-d2 mt-9 flex flex-col gap-5 sm:flex-row sm:items-center">
                <div className="flex items-center gap-2">
                  <Button as={Link} to="/register" variant="white" size="lg">Get started</Button>
                  <ArrowButton as={Link} to="/solutions" label="Explore solutions" />
                </div>
                <p className="max-w-sm text-sm leading-relaxed text-white/75">
                  Bring devices, live sensor data, alerts, and controls into one calm command center built for teams doing real work.
                </p>
              </div>
            </div>

            <div className="hero-status-card enter-rise enter-d3 glass w-full rounded-2xl p-3 sm:w-72" aria-label="Live fleet status">
              <div className="relative h-32 overflow-hidden rounded-xl">
                <img className="tile-photo h-full w-full object-cover" src="https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=600&q=75" alt="" />
                <span className="absolute left-2.5 top-2.5 flex items-center gap-1.5 rounded-full bg-white/90 px-2.5 py-1 text-xs font-semibold text-ink shadow-sm">
                  <span className="status-pulse h-1.5 w-1.5 rounded-full bg-success" /> Live
                </span>
              </div>
              <div className="mt-3 flex items-end justify-between px-1">
                <div>
                  <p className="text-xs text-muted">Office sensor</p>
                  <p className="text-2xl font-semibold leading-none tracking-tight text-ink">24.6°C</p>
                </div>
                <svg viewBox="0 0 120 40" className="h-10 w-28" fill="none" aria-hidden="true">
                  <defs>
                    <linearGradient id="spark" x1="0" x2="1"><stop offset="0" stopColor="#0B7F92" stopOpacity=".55" /><stop offset="1" stopColor="#14A8BE" /></linearGradient>
                  </defs>
                  <path className="spark-line" d="M2 30 C14 28 18 14 30 18 S48 34 60 22 S80 6 92 14 S110 10 118 5" stroke="url(#spark)" strokeWidth="2.5" strokeLinecap="round" />
                </svg>
              </div>
              <div className="mx-1 mt-3 flex items-center justify-between text-xs text-muted">
                <span>Updated just now</span><span className="text-success">Stable</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Statement */}
      <section className="mx-auto max-w-7xl px-6 py-24 sm:px-10 lg:py-32">
        <div className="grid gap-10 lg:grid-cols-[1.25fr_0.75fr] lg:items-end">
          <h2 className="text-4xl font-medium leading-[1.06] tracking-tight text-ink sm:text-5xl lg:text-[3.75rem]">
            A clearer view of every device, wherever it operates.
          </h2>
          <p className="text-lg leading-relaxed text-muted">
            NexaIoT turns scattered readings into a living operational picture. Your team can see what changed, understand why it matters, and respond without switching tools.
          </p>
        </div>

        <div className="mt-16 grid divide-y divide-border border-y border-border sm:grid-cols-3 sm:divide-x sm:divide-y-0">
          {[
            [Radio, "Connect", "Bring sensors, controllers, and gateways into one organized fleet."],
            [BarChart3, "Understand", "Watch live conditions and long-term trends in the same place."],
            [ShieldCheck, "Act", "Surface the moments that need attention before they become interruptions."],
          ].map(([Icon, title, text]) => (
            <div key={title} className="py-8 sm:px-8 sm:first:pl-0 sm:last:pr-0">
              <span className="glass flex h-11 w-11 items-center justify-center rounded-full text-primary"><Icon size={19} /></span>
              <h3 className="mt-6 text-xl font-semibold text-ink">{title} your operation</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted">{text}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Field */}
      <section className="mx-auto max-w-7xl px-3 sm:px-4">
        <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="frame min-h-[28rem] tile-zoom">
            <img className="frame-photo !filter-none" style={{ filter: "saturate(1.08) contrast(1.05) brightness(.82)" }} src="https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=1300&q=85" alt="Connected industrial sensor hardware" loading="lazy" />
            <div className="frame-grade" />
            <div className="glass-dark absolute bottom-5 right-5 flex items-center gap-2 rounded-full px-4 py-2.5 text-sm font-medium">
              <Wifi size={16} /> Securely connected
            </div>
          </div>
          <div className="glass flex flex-col justify-center rounded-3xl p-8 sm:p-12">
            <h2 className="text-3xl font-medium leading-tight text-ink sm:text-4xl">
              Your operation changes by the minute. Your software should keep pace.
            </h2>
            <p className="mt-4 text-muted leading-relaxed">Monitor the details that matter today, then step back to spot the patterns shaping tomorrow.</p>
            <div className="mt-8 divide-y divide-border border-y border-border">
              {[
                [Thermometer, "Environmental readings", "Temperature, humidity, pressure, and air quality in real time."],
                [Zap, "Energy visibility", "Track consumption, voltage, and equipment health across your sites."],
                [Bell, "Alerts with context", "Know which condition changed, where it happened, and what to do next."],
              ].map(([Icon, title, text]) => (
                <div key={title} className="flex gap-4 py-5">
                  <span className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/15 text-primary"><Icon size={18} /></span>
                  <div>
                    <h3 className="font-semibold text-ink">{title}</h3>
                    <p className="mt-1 text-sm leading-relaxed text-muted">{text}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Telemetry */}
      <section className="mx-auto max-w-7xl px-6 py-24 sm:px-10 lg:py-32">
        <div className="grid gap-10 lg:grid-cols-[0.8fr_1.2fr] lg:items-end">
          <div>
            <h2 className="text-3xl font-medium leading-tight text-ink sm:text-4xl">See the state of your fleet at a glance.</h2>
            <p className="mt-4 max-w-md text-lg leading-relaxed text-muted">Built for scanning, not hunting. Important signals surface fast, while the detail is always one click away.</p>
          </div>
          <div className="glass grid grid-cols-2 gap-px overflow-hidden rounded-3xl bg-border sm:grid-cols-3">
            {readings.map(([Icon, label, value, state]) => (
              <div key={label} className="bg-[var(--bg-subtle)] p-6">
                <Icon size={18} className="text-primary" />
                <p className="mt-6 text-3xl font-semibold tracking-tight text-ink">{value}</p>
                <p className="mt-1 text-sm text-muted">{label}</p>
                <p className="mt-3 text-xs font-medium text-success">{state}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Solutions */}
      <section className="mx-auto max-w-7xl px-6 pb-24 sm:px-10 lg:pb-32">
        <div className="flex flex-col justify-between gap-6 sm:flex-row sm:items-end">
          <h2 className="max-w-2xl text-3xl font-medium leading-tight text-ink sm:text-4xl">One platform, shaped around the work you do.</h2>
          <Button as={Link} to="/solutions" variant="secondary" iconEnd={ArrowUpRight}>View all solutions</Button>
        </div>
        <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {solutions.map((solution) => {
            const Icon = solutionIcons[solution.key];
            return (
              <Link key={solution.key} to="/solutions" className="tile-zoom group relative block aspect-[4/5] overflow-hidden rounded-3xl bg-neutral-900">
                <img className="tile-photo absolute inset-0 h-full w-full object-cover" src={solutionImages[solution.key]} alt="" loading="lazy" />
                <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/20 to-transparent" />
                <span className="solution-card-icon absolute left-5 top-5 flex h-11 w-11 items-center justify-center rounded-full"><Icon size={18} /></span>
                <span className="solution-card-arrow absolute right-5 top-5 flex h-11 w-11 items-center justify-center rounded-full"><ArrowUpRight size={18} /></span>
                <div className="absolute inset-x-0 bottom-0 p-6 text-white">
                  <h3 className="text-2xl font-semibold">{solution.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-white/75">{solution.blurb}</p>
                </div>
              </Link>
            );
          })}
        </div>
      </section>

      {/* Steps (a real sequence) */}
      <section className="mx-auto max-w-7xl px-6 pb-24 sm:px-10 lg:pb-32">
        <h2 className="max-w-2xl text-3xl font-medium leading-tight text-ink sm:text-4xl">A simple path to a more responsive operation.</h2>
        <ol className="mt-12 grid divide-y divide-border border-y border-border md:grid-cols-3 md:divide-x md:divide-y-0">
          {[
            ["01", "Connect your devices", "Bring your physical infrastructure into one workspace."],
            ["02", "Stream the right data", "Capture readings that tell the story of your operation."],
            ["03", "Respond with confidence", "Turn conditions into informed, timely action."],
          ].map(([number, title, text]) => (
            <li key={number} className="py-10 md:px-8 md:first:pl-0 md:last:pr-0">
              <span className="block text-6xl font-semibold tracking-tighter text-transparent [-webkit-text-stroke:1px_rgb(var(--muted)/0.6)]">{number}</span>
              <h3 className="mt-8 text-xl font-semibold text-ink">{title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted">{text}</p>
            </li>
          ))}
        </ol>
      </section>

      {/* CTA */}
      <section className="p-3 sm:p-4">
        <div className="frame flex min-h-[520px] flex-col items-center justify-end text-center">
          <img className="frame-photo" src="https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=1800&q=85" alt="Industrial controller in a connected environment" loading="lazy" />
          <div className="frame-grade" />
          <span aria-hidden="true" className="ghost-word absolute left-1/2 top-[14%] -translate-x-1/2 text-[clamp(4rem,17vw,15rem)]">Go live</span>
          <div className="relative z-10 px-6 pb-14 text-white">
            <h2 className="mx-auto max-w-2xl text-3xl font-semibold leading-tight sm:text-5xl">Make every device easier to understand.</h2>
            <p className="mx-auto mt-4 max-w-xl text-lg text-white/75">Start building a clearer picture of your connected operation with NexaIoT.</p>
            <div className="mt-8 flex items-center justify-center gap-2">
              <Button as={Link} to="/register" variant="white" size="lg">Get started</Button>
              <ArrowButton as={Link} to="/features" label="See features" />
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
