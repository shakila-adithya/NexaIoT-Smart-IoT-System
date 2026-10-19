import { Target, Cpu, ShieldCheck } from "lucide-react";
import Card from "../../components/public/PublicCard.jsx";
import PageHero from "../../components/public/PageHero.jsx";

export default function About() {
  return (
    <div>
      <PageHero
        ghost="About"
        image="https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=1800&q=82"
        imageAlt="Operations team collaborating around connected technology infrastructure"
        title="About NexaIoT"
        text="A modern IoT monitoring and control platform designed to connect devices, visualize sensor data, and simplify device management."
      />
      <div className="mx-auto max-w-5xl px-6 py-20 sm:px-10">
      <div className="grid sm:grid-cols-3 gap-4">
        <Card className="p-6">
          <Target size={20} className="text-primary mb-4" />
          <h3 className="font-semibold text-ink">Our Mission</h3>
          <p className="text-sm text-muted mt-2">
            Make IoT infrastructure understandable and controllable for teams of any size, from student
            projects to production deployments.
          </p>
        </Card>
        <Card className="p-6">
          <Cpu size={20} className="text-primary mb-4" />
          <h3 className="font-semibold text-ink">Our Technology</h3>
          <p className="text-sm text-muted mt-2">
            Built with React, Tailwind CSS, and Recharts on the frontend, with a clean API layer ready to
            connect to any backend.
          </p>
        </Card>
        <Card className="p-6">
          <ShieldCheck size={20} className="text-primary mb-4" />
          <h3 className="font-semibold text-ink">System Capabilities</h3>
          <p className="text-sm text-muted mt-2 ">
            Device management, real-time monitoring, alerting, analytics, and reporting - all in one
            connected experience.
          </p>
        </Card>
      </div>

      <div className="mt-20">
        <h2 className="text-3xl font-medium text-ink mb-4">IoT monitoring, explained simply</h2>
        <p className="text-muted leading-relaxed max-w-3xl">
          Devices collect readings from their environment such as temperature, humidity, voltage, and more....
          NexaIoT gathers those readings, displays them live on your dashboard, raises alerts when something
          needs attention, and helps you look back at trends over time. This project is a frontend
          demonstration and does not represent any real company or customer.
        </p>
      </div>
      </div>
    </div>
  );
}


