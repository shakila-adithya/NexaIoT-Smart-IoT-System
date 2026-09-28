import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, BarChart3, Battery, Bell, Building2, Cpu, Droplets, Factory, Gauge, Leaf, MapPin, Radio, ShieldCheck, Sprout, Thermometer, Wifi, Zap } from "lucide-react";
import Button from "../../components/ui/Button.jsx";
import Badge from "../../components/ui/Badge.jsx";
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

function Reveal({ children, className = "" }) {
  const ref = useRef(null);
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    const element = ref.current;
    if (!element || window.matchMedia("(prefers-reduced-motion: reduce)").matches) { setVisible(true); return undefined; }
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) { setVisible(true); observer.unobserve(entry.target); }
    }, { threshold: 0.1, rootMargin: "0px 0px -48px" });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return <section ref={ref} className={`home-reveal ${visible ? "is-visible" : ""} ${className}`}>{children}</section>;
}

export default function Home() {
  return <div className="home-page">
    <section className="home-hero">
      <img className="home-hero-image" src="https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?auto=format&fit=crop&w=2000&q=88" alt="Engineer monitoring connected equipment in a modern industrial facility" fetchPriority="high" />
      <div className="home-hero-shade" /><div className="home-hero-grid" aria-hidden="true" />
      <div className="relative z-10 mx-auto flex max-w-7xl flex-col px-4 py-10 sm:px-6">
        <div className="max-w-3xl home-hero-copy">
          <Badge tone="primary">One platform for connected operations</Badge>
          <h1 className="mt-6 text-5xl font-bold leading-[1.02] text-white sm:text-6xl lg:text-7xl">NexaIoT keeps your physical world in view.</h1>
          <p className="mt-6 max-w-2xl text-lg leading-relaxed text-slate-200 sm:text-xl">Bring devices, live sensor data, alerts, and controls into one calm command center built for teams doing real work.</p>
          <div className="mt-9 flex flex-wrap gap-3"><Button as={Link} to="/contact" size="lg" icon={ArrowRight}>Contact our team</Button><Button as={Link} to="/solutions" variant="secondary" size="lg" className="!border-white/30 !bg-white/10 !text-white hover:!bg-white/20">Explore solutions</Button></div>
        </div>
        <div className="home-hero-status" aria-label="Live device fleet status"><div className="flex items-center gap-2 text-sm font-medium text-white"><span className="status-pulse h-2 w-2 rounded-full bg-emerald-400" />Fleet telemetry is live</div><div className="mt-4 grid grid-cols-3 divide-x divide-white/15 text-white"><div className="pr-5"><p className="text-2xl font-semibold">12</p><p className="mt-1 text-xs text-slate-300">Devices online</p></div><div className="px-5"><p className="text-2xl font-semibold">248</p><p className="mt-1 text-xs text-slate-300">Events / minute</p></div><div className="pl-5"><p className="text-2xl font-semibold">99.9%</p><p className="mt-1 text-xs text-slate-300">System uptime</p></div></div></div>
      </div>
    </section>

    <Reveal className="border-b border-border bg-white"><div className="mx-auto grid max-w-7xl gap-12 px-4 py-20 sm:px-6 lg:grid-cols-[0.9fr_1.1fr] lg:items-end lg:py-28"><div><p className="home-eyebrow">From signal to decision</p><h2 className="mt-4 text-3xl font-bold leading-tight text-ink sm:text-4xl">A clearer view of every device, wherever it operates.</h2></div><p className="max-w-2xl text-lg leading-relaxed text-muted">NexaIoT turns scattered readings into a living operational picture. Your team can see what changed, understand why it matters, and respond without switching tools.</p></div><div className="mx-auto grid max-w-7xl gap-px overflow-hidden border-y border-border bg-border sm:grid-cols-3">{[[Radio,"Connect","Bring sensors, controllers, and gateways into one organized fleet."],[BarChart3,"Understand","Watch live conditions and long-term trends in the same place."],[ShieldCheck,"Act","Surface the moments that need attention before they become interruptions."]].map(([Icon,title,text]) => <div key={title} className="home-principle bg-white px-6 py-8 sm:px-8"><Icon size={22} className="text-primary" /><h3 className="mt-6 text-lg font-semibold text-ink">{title} your operation</h3><p className="mt-2 text-sm leading-relaxed text-muted">{text}</p></div>)}</div></Reveal>

    <Reveal className="bg-slate-50 py-20 sm:py-28"><div className="mx-auto grid max-w-7xl gap-12 px-4 sm:px-6 lg:grid-cols-[1.08fr_0.92fr] lg:items-center"><div className="home-operation-image"><img src="https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=1300&q=85" alt="Engineer working with connected industrial equipment" loading="lazy" /><div className="home-operation-callout"><Wifi size={16} className="text-cyan-300" /><span>Securely connected</span></div></div><div><p className="home-eyebrow">Designed for the field</p><h2 className="mt-4 text-3xl font-bold leading-tight text-ink sm:text-4xl">Your operation changes by the minute. Your software should keep pace.</h2><p className="mt-5 text-lg leading-relaxed text-muted">Monitor the details that matter today, then step back to spot the patterns shaping tomorrow.</p><div className="mt-8 divide-y divide-border border-y border-border">{[[Thermometer,"Environmental readings","Temperature, humidity, pressure, and air quality in real time."],[Zap,"Energy visibility","Track consumption, voltage, and equipment health across your sites."],[Bell,"Alerts with context","Know which condition changed, where it happened, and what to do next."]].map(([Icon,title,text]) => <div key={title} className="flex gap-4 py-5"><span className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"><Icon size={19} /></span><div><h3 className="font-semibold text-ink">{title}</h3><p className="mt-1 text-sm leading-relaxed text-muted">{text}</p></div></div>)}</div></div></div></Reveal>

    <Reveal className="home-telemetry-section bg-slate-950 py-20 text-white sm:py-28"><div className="mx-auto max-w-7xl px-4 sm:px-6"><div className="grid gap-10 lg:grid-cols-[0.82fr_1.18fr] lg:items-end"><div><p className="home-eyebrow !text-cyan-300">Live operational picture</p><h2 className="mt-4 text-3xl font-bold leading-tight sm:text-4xl">See the state of your fleet at a glance.</h2><p className="mt-5 max-w-xl text-lg leading-relaxed text-slate-300">Built for scanning, not hunting. Important signals surface fast, while the detail is always one click away.</p></div><div className="home-telemetry-grid">{[[Thermometer,"Temperature","24.6°C","Stable"],[Droplets,"Humidity","41%","Optimal"],[Zap,"Energy draw","1.2 kW","In range"],[Battery,"Fleet battery","88%","Healthy"],[Gauge,"Pressure","101.3 kPa","Normal"],[Cpu,"Connected nodes","12 / 12","All reporting"]].map(([Icon,label,value,state]) => <div key={label} className="home-telemetry-item"><Icon size={18} className="text-cyan-300" /><p className="mt-6 text-xs font-medium uppercase tracking-wide text-slate-400">{label}</p><p className="mt-2 text-2xl font-semibold">{value}</p><p className="mt-3 text-xs text-emerald-300">{state}</p></div>)}</div></div></div></Reveal>

    <Reveal className="bg-white py-20 sm:py-28"><div className="mx-auto max-w-7xl px-4 sm:px-6"><div className="flex flex-col justify-between gap-6 sm:flex-row sm:items-end"><div className="max-w-2xl"><p className="home-eyebrow">Built for real environments</p><h2 className="mt-4 text-3xl font-bold leading-tight text-ink sm:text-4xl">One platform, shaped around the work you do.</h2></div><Button as={Link} to="/solutions" variant="secondary" icon={ArrowRight}>View all solutions</Button></div><div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">{solutions.map((solution) => { const Icon = solutionIcons[solution.key]; return <Link key={solution.key} to="/solutions" className="home-solution group"><img src={solutionImages[solution.key]} alt="" loading="lazy" /><div className="home-solution-shade" /><div className="relative z-10 flex h-full flex-col justify-end p-6 text-white"><Icon size={21} /><h3 className="mt-8 text-xl font-semibold">{solution.title}</h3><p className="mt-2 text-sm leading-relaxed text-slate-200">{solution.blurb}</p><span className="mt-5 inline-flex items-center gap-2 text-sm font-medium">Explore use case <ArrowRight size={15} /></span></div></Link>; })}</div></div></Reveal>

    <Reveal className="bg-slate-50 py-20 sm:py-28"><div className="mx-auto max-w-7xl px-4 sm:px-6"><div className="max-w-2xl"><p className="home-eyebrow">From setup to insight</p><h2 className="mt-4 text-3xl font-bold leading-tight text-ink sm:text-4xl">A simple path to a more responsive operation.</h2></div><ol className="home-steps mt-12">{[["01","Connect your devices","Bring your physical infrastructure into one workspace."],["02","Stream the right data","Capture readings that tell the story of your operation."],["03","Respond with confidence","Turn conditions into informed, timely action."]].map(([number,title,text]) => <li key={number}><span>{number}</span><h3>{title}</h3><p>{text}</p></li>)}</ol></div></Reveal>

    <section className="home-cta"><img src="https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=1800&q=85" alt="Industrial controller in a connected environment" loading="lazy" /><div className="home-cta-shade" /><div className="relative z-10 mx-auto max-w-4xl px-4 py-24 text-center sm:px-6 sm:py-32"><p className="home-eyebrow !text-cyan-200">Ready when you are</p><h2 className="mt-4 text-4xl font-bold leading-tight text-white sm:text-5xl">Make every device easier to understand.</h2><p className="mx-auto mt-5 max-w-2xl text-lg text-slate-200">Start building a clearer picture of your connected operation with NexaIoT.</p><Button as={Link} to="/contact" size="lg" className="mt-9 !bg-white !text-primary hover:!bg-slate-100" icon={ArrowRight}>Contact us</Button></div></section>
  </div>;
}
