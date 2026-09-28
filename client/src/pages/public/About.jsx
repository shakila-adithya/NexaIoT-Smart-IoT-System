import { Target, Cpu, ShieldCheck } from "lucide-react";
import Card from "../../components/ui/Card.jsx";
import EditorialVisual from "../../components/ui/EditorialVisual.jsx";

export default function About() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-16 sm:py-20 iot-grid public-showcase-page">
      <div className="grid lg:grid-cols-[1fr_0.8fr] gap-10 lg:gap-16 items-center">
        <div className="max-w-2xl">
          <p className="showcase-kicker">NEXAIOT / ABOUT</p>
          <h1 className="text-4xl font-bold text-ink tracking-tight">About NexaIoT</h1>
          <p className="mt-5 text-lg text-muted">
            NexaIoT is a modern IoT monitoring and control platform designed to connect devices, visualize
            sensor data, and simplify device management.
          </p>
        </div>
        <EditorialVisual
          image="https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=1100&q=82"
          imageAlt="Operations team collaborating around connected technology infrastructure"
          detailImage="https://images.unsplash.com/photo-1556761175-b413da4baf72?auto=format&fit=crop&w=700&q=80"
          detailAlt="Team collaborating in a modern workspace"
          label="Cloud-ready intelligence"
        />
      </div>

      <div className="mt-14 grid sm:grid-cols-3 gap-6">
        <Card className="p-6">
          <Target size={20} className="text-primary mb-3" />
          <h3 className="font-semibold text-ink">Our Mission</h3>
          <p className="text-sm text-muted mt-2">
            Make IoT infrastructure understandable and controllable for teams of any size, from student
            projects to production deployments.
          </p>
        </Card>
        <Card className="p-6">
          <Cpu size={20} className="text-primary mb-3" />
          <h3 className="font-semibold text-ink">Our Technology</h3>
          <p className="text-sm text-muted mt-2">
            Built with React, Tailwind CSS, and Recharts on the frontend, with a clean API layer ready to
            connect to any backend.
          </p>
        </Card>
        <Card className="p-6">
          <ShieldCheck size={20} className="text-primary mb-3" />
          <h3 className="font-semibold text-ink">System Capabilities</h3>
          <p className="text-sm text-muted mt-2 ">
            Device management, real-time monitoring, alerting, analytics, and reporting - all in one
            connected experience.
          </p>
        </Card>
      </div>

      <div className="mt-14">
        <h2 className="text-2xl font-bold text-ink mb-3">IoT monitoring, explained simply</h2>
        <p className="text-muted leading-relaxed w-full text-justify hyphens-auto">
          Devices collect readings from their environment such as temperature, humidity, voltage, and more....
          NexaIoT gathers those readings, displays them live on your dashboard, raises alerts when something
          needs attention, and helps you look back at trends over time. This project is a frontend
          demonstration and does not represent any real company or customer.
        </p>
      </div>
    </div>
  );
}
