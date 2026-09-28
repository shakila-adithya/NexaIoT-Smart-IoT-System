import { useState } from "react";
import { Link, NavLink } from "react-router-dom";
import { Satellite, Menu, X } from "lucide-react";
import Button from "../ui/Button.jsx";

const links = [
  { to: "/", label: "Home" },
  { to: "/features", label: "Features" },
  { to: "/solutions", label: "Solutions" },
  { to: "/about", label: "About" },
];

export default function Navbar() {
  const [open, setOpen] = useState(false);

  return (
    <header className="sticky top-0 z-40 bg-white/95 border-b border-border">
      <nav className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2 font-bold text-ink text-lg">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-white">
            <Satellite size={18} />
          </span>
          NexaIoT
        </Link>

        <div className="hidden lg:flex items-center gap-1">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              end={l.to === "/"}
              className={({ isActive }) =>
                `px-3.5 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive ? "text-primary bg-primary/5" : "text-muted hover:text-ink hover:bg-subtle"
                }`
              }
            >
              {l.label}
            </NavLink>
          ))}
        </div>

        <div className="hidden lg:flex items-center gap-3">
          <Button variant="ghost" size="sm">
            Login
          </Button>
          <Button size="sm">
            Get Started
          </Button>
        </div>

        <button
          className="lg:hidden p-2 text-ink"
          onClick={() => setOpen(true)}
          aria-label="Open menu"
          aria-expanded={open}
        >
          <Menu size={22} />
        </button>
      </nav>

      {open && (
        <div className="fixed inset-0 z-50 lg:hidden animate-fade-in">
          <div className="absolute inset-0 bg-slate-900/40" onClick={() => setOpen(false)} />
          <div className="absolute right-0 top-0 h-full w-72 max-w-full overflow-y-auto bg-white shadow-soft p-6 flex flex-col animate-slide-up">
            <div className="flex items-center justify-between mb-6">
              <span className="font-bold text-ink">Menu</span>
              <button onClick={() => setOpen(false)} aria-label="Close menu" className="p-1.5 text-muted">
                <X size={20} />
              </button>
            </div>
            <div className="flex flex-col gap-1">
              {links.map((l) => (
                <NavLink
                  key={l.to}
                  to={l.to}
                  end={l.to === "/"}
                  onClick={() => setOpen(false)}
                  className={({ isActive }) =>
                    `px-3 py-2.5 rounded-lg text-sm font-medium ${isActive ? "text-primary bg-primary/5" : "text-ink"}`
                  }
                >
                  {l.label}
                </NavLink>
              ))}
            </div>
            <div className="mt-6 flex flex-col gap-3">
              <Button as={Link} to="/features" variant="secondary" onClick={() => setOpen(false)}>
                Features
              </Button>
              <Button as={Link} to="/contact" onClick={() => setOpen(false)}>
                Contact Us
              </Button>
            </div>
          </div>
        </div>
      )}
    </header>
  );
}
