import { Link } from "react-router-dom";
import { Satellite } from "lucide-react";

const columns = [
  {
    title: "Product",
    links: [
      { to: "/features", label: "Features" },
      { to: "/solutions", label: "Solutions" },
    ],
  },
  {
    title: "Company",
    links: [
      { to: "/about", label: "About" },
      { to: "/contact", label: "Contact" },
    ],
  },
  {
    title: "Legal",
    links: [
      { to: "/privacy", label: "Privacy Policy" },
      { to: "/terms", label: "Terms of Service" },
    ],
  },
];

export default function Footer() {
  return (
    <footer className="site-footer bg-subtle border-t border-border">
      <div className="site-footer-inner max-w-7xl mx-auto px-4 sm:px-6 py-9 grid gap-7 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <Link to="/" className="flex items-center gap-2 font-bold text-ink text-lg">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-white">
              <Satellite size={18} />
            </span>
            NexaIoT
          </Link>
          <p className="mt-3 text-sm text-muted max-w-xs">
            Connect devices, monitor sensors, and manage your IoT infrastructure from one intelligent platform.
          </p>
          <span className="footer-status mt-5"><i /> All systems operational</span>
        </div>
        {columns.map((col) => (
          <div key={col.title}>
            <h4 className="text-sm font-semibold text-ink mb-3">{col.title}</h4>
            <ul className="space-y-2">
              {col.links.map((l) => (
                <li key={l.to}>
                  <Link to={l.to} className="text-sm text-muted hover:text-primary">
                    {l.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="site-footer-bottom border-t border-border py-3 px-4 sm:px-6">
        <div className="max-w-7xl mx-auto flex flex-col gap-2 text-xs text-muted sm:flex-row sm:items-center sm:justify-between">
          <span>© {new Date().getFullYear()} NexaIoT. All rights reserved.</span>
          <span className="footer-tagline">Smart IoT device management platform</span>
        </div>
      </div>
    </footer>
  );
}
