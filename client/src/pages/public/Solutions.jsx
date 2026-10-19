import { Building2, Sprout, Factory, Zap, Leaf, MapPin } from "lucide-react";
import Card from "../../components/public/PublicCard.jsx";
import Badge from "../../components/public/PublicBadge.jsx";
import PageHero from "../../components/public/PageHero.jsx";
import { solutions } from "../../data/mockData.js";
import solutionsHeroImage from "../../assets/dashboard/device-status/industrial-robot-arm.jpg";

const icons = { buildings: Building2, agriculture: Sprout, industrial: Factory, energy: Zap, environmental: Leaf, asset: MapPin };
const solutionVisuals = {
  buildings: {
    src: "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=900&q=80",
    alt: "Modern glass office building representing a connected smart building",
  },
  agriculture: {
    src: "https://images.unsplash.com/photo-1416879595882-3373a0480b5b?auto=format&fit=crop&w=900&q=80",
    alt: "Sensor-ready greenhouse plants for precision agriculture monitoring",
  },
  industrial: {
    src: "https://images.unsplash.com/photo-1764835994645-3faa2c40f708?auto=format&fit=crop&w=900&q=80",
    alt: "Industrial machinery on a modern production floor",
  },
  energy: {
    src: "https://images.unsplash.com/photo-1473341304170-971dccb5ac1e?auto=format&fit=crop&w=900&q=80",
    alt: "Electricity transmission lines for energy monitoring",
  },
  environmental: {
    src: "https://images.unsplash.com/photo-1758129949324-6f27ade9ff1a?auto=format&fit=crop&w=900&q=80",
    alt: "Weather monitoring station collecting environmental sensor readings",
  },
  asset: {
    src: "https://images.unsplash.com/photo-1682559736721-c2e77ff4c650?auto=format&fit=crop&w=900&q=80",
    alt: "High-speed network equipment used to connect and track critical assets",
  },
};

export default function Solutions() {
  return (
    <div>
      <PageHero
        ghost="Solutions"
        image={solutionsHeroImage}
        imageAlt="Robotic automation equipment operating inside an industrial production facility"
        title="Solutions built for how you work."
        text="Whether you manage a building, a farm, or a factory floor, NexaIoT adapts to the metrics that matter most."
      />
      <div className="mx-auto max-w-7xl px-6 py-20 sm:px-10">
        <div className="grid md:grid-cols-2 gap-4">
          {solutions.map((s) => {
            const Icon = icons[s.key];
            const visual = solutionVisuals[s.key];
            return (
              <Card key={s.key} className="overflow-hidden tile-zoom !rounded-3xl">
                <div className="relative h-52 overflow-hidden">
                  <img src={visual.src} alt={visual.alt} className="tile-photo h-full w-full object-cover" loading="lazy" />
                  <span className="public-solution-icon absolute left-5 top-5 flex h-11 w-11 items-center justify-center rounded-full">
                    <Icon size={18} />
                  </span>
                </div>
                <div className="p-7">
                  <h3 className="text-2xl font-semibold text-ink">{s.title}</h3>
                  <p className="text-sm text-muted mt-2 leading-relaxed">{s.blurb}</p>
                  <div className="mt-5 flex flex-wrap gap-2">
                    {s.metrics.map((m) => (
                      <Badge key={m} tone="primary">{m}</Badge>
                    ))}
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      </div>
    </div>
  );
}
