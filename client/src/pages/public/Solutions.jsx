import { Building2, Sprout, Factory, Zap, Leaf, MapPin } from "lucide-react";
import Card from "../../components/ui/Card.jsx";
import Badge from "../../components/ui/Badge.jsx";
import EditorialVisual from "../../components/ui/EditorialVisual.jsx";
import { solutions } from "../../data/mockData.js";

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
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-16 sm:py-20 iot-grid public-showcase-page">
      <div className="grid lg:grid-cols-[1fr_0.82fr] gap-10 lg:gap-16 items-center mb-14">
        <div className="max-w-2xl">
          <p className="showcase-kicker">NEXAIOT / SOLUTIONS</p>
          <h1 className="text-4xl font-bold text-ink tracking-tight">Solutions built for how you work.</h1>
          <p className="mt-4 text-lg text-muted">
            Whether you manage a building, a farm, or a factory floor, NexaIoT adapts to the metrics that
            matter most.
          </p>
        </div>
        <EditorialVisual
          image="https://images.unsplash.com/photo-1550751827-4bd374c3f58b?auto=format&fit=crop&w=1200&q=82"
          imageAlt="Digital network visualization representing connected IoT environments"
          detailImage="https://images.unsplash.com/photo-1473341304170-971dccb5ac1e?auto=format&fit=crop&w=700&q=80"
          detailAlt="Energy infrastructure connected to a monitoring network"
          label="24/7 telemetry"
        />
      </div>
      <div className="grid md:grid-cols-2 gap-6">
          {solutions.map((s) => {
            const Icon = icons[s.key];
            const visual = solutionVisuals[s.key];
            return (
              <Card key={s.key} className="overflow-hidden group solution-card">
                <img
                  src={visual.src}
                  alt={visual.alt}
                  className="h-40 w-full object-cover object-center transition-transform duration-500 group-hover:scale-[1.03]"
                  loading="lazy"
                />
                <div className="p-7">
                  <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-primary mb-5">
                    <Icon size={22} />
                  </span>
                  <h3 className="text-xl font-semibold text-ink">{s.title}</h3>
                  <p className="text-sm text-muted mt-2">{s.blurb}</p>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {s.metrics.map((m) => (
                      <Badge key={m} tone="primary">
                        {m}
                      </Badge>
                    ))}
                  </div>
                </div>
              </Card>
            );
          })}
      </div>
    </div>
  );
}
