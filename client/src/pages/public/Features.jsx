import {
  Cpu, Radio, LineChart, ToggleRight, Bell, FileBarChart, PieChart, Plug, Users, ShieldCheck,
} from "lucide-react";
import Card from "../../components/public/PublicCard.jsx";
import PageHero from "../../components/public/PageHero.jsx";
import { features } from "../../data/mockData.js";

const icons = [Cpu, Radio, LineChart, ToggleRight, Bell, FileBarChart, PieChart, Plug, Users, ShieldCheck];

export default function Features() {
  return (
    <div>
      <PageHero
        ghost="Features"
        image="https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=1800&q=82"
        imageAlt="Modern operations workspace representing centralized IoT fleet management"
        title="Everything you need to run an IoT fleet."
        text="NexaIoT brings device management, monitoring, and analytics together in one clean, dependable platform."
      />
      <div className="mx-auto max-w-7xl px-6 py-20 sm:px-10">
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {features.map((f, i) => {
            const Icon = icons[i];
            return (
              <Card key={f.title} className="p-7">
                <span className="public-feature-icon mb-5 flex h-11 w-11 items-center justify-center rounded-full">
                  <Icon size={19} />
                </span>
                <h3 className="text-lg font-semibold text-ink">{f.title}</h3>
                <p className="text-sm text-muted mt-1.5 leading-relaxed">{f.desc}</p>
              </Card>
            );
          })}
        </div>
      </div>
    </div>
  );
}
