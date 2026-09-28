import {
  Cpu, Radio, LineChart, ToggleRight, Bell, FileBarChart, PieChart, Plug, Users, ShieldCheck,
} from "lucide-react";
import Card from "../../components/ui/Card.jsx";
import EditorialVisual from "../../components/ui/EditorialVisual.jsx";
import { features } from "../../data/mockData.js";

const icons = [Cpu, Radio, LineChart, ToggleRight, Bell, FileBarChart, PieChart, Plug, Users, ShieldCheck];

export default function Features() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-16 sm:py-20 iot-grid public-showcase-page">
      <div className="grid lg:grid-cols-[1fr_0.78fr] gap-10 lg:gap-16 items-center mb-14">
        <div className="max-w-2xl">
          <p className="showcase-kicker">NEXAIOT / FEATURES</p>
          <h1 className="text-4xl font-bold text-ink tracking-tight">Everything you need to run an IoT fleet.</h1>
          <p className="mt-4 text-lg text-muted">
            NexaIoT brings device management, monitoring, and analytics together in one clean, dependable
            platform.
          </p>
        </div>
        <EditorialVisual
          image="https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=1100&q=82"
          imageAlt="Modern operations workspace representing centralized IoT fleet management"
          detailImage="https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=700&q=80"
          detailAlt="Connected sensor hardware"
          label="Live fleet visibility"
        />
      </div>
      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
        {features.map((f, i) => {
          const Icon = icons[i];
          return (
            <Card key={f.title} className="p-6 feature-card">
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary mb-4">
                <Icon size={20} />
              </span>
              <h3 className="font-semibold text-ink">{f.title}</h3>
              <p className="text-sm text-muted mt-1.5 w-full">{f.desc}</p>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
