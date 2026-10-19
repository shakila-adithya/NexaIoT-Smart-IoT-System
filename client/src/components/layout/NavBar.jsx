import { useState } from "react";
import { Link, NavLink } from "react-router-dom";
import { Satellite, Menu, X } from "lucide-react";
import Button from "../ui/Button.jsx";

const links = [
  { to: "/", label: "Home" },
  { to: "/features", label: "Features" },
  { to: "/solutions", label: "Solutions" },
  { to: "/about", label: "About" },
  { to: "/contact", label: "Contact" },
];

export default function Navbar() {
  const [open, setOpen] = useState(false);

  return (
    <header className="public-navbar fixed inset-x-0 top-0 z-40 px-5 pt-6 sm:px-8 sm:pt-7 pointer-events-none">
      <nav className="mx-auto grid max-w-7xl grid-cols-[1fr_auto] items-center gap-4 lg:grid-cols-[1fr_auto_1fr]">
        <Link to="/" className="pointer-events-auto inline-flex items-center gap-2.5 text-lg font-semibold text-white" aria-label="NexaIoT home">
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-[#5EE0EE] to-[#14A0B6] text-[#04202c]"><Satellite size={16} /></span>
          NexaIoT
        </Link>
        <div className="pointer-events-auto hidden items-center gap-1 rounded-full glass-dark p-1.5 lg:flex">
          {links.map((link) => <NavLink key={link.to} to={link.to} end={link.to === "/"} className={({ isActive }) => `rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${isActive ? "bg-white/20 text-white" : "text-white/70 hover:text-white"}`}>{link.label}</NavLink>)}
        </div>
        <div className="pointer-events-auto hidden items-center justify-self-end gap-3 lg:flex"><Link to="/login" className="rounded-full px-3 py-2 text-sm font-medium text-white/85 hover:text-white">Log in</Link><Button as={Link} to="/register" variant="primary" size="sm" className="!rounded-full !bg-white !text-slate-950 hover:!bg-white/90">Sign up</Button></div>
        <button type="button" className="pointer-events-auto flex h-11 w-11 items-center justify-center rounded-full glass-dark lg:hidden" onClick={() => setOpen(true)} aria-label="Open menu" aria-expanded={open}><Menu size={20} className="text-white" /></button>
      </nav>
      {open ? <div className="pointer-events-auto fixed inset-0 z-50 animate-fade-in lg:hidden"><div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setOpen(false)} /><div className="absolute inset-x-3 top-3 glass-dark rounded-3xl bg-[#06141f]/95 p-6 animate-slide-up"><div className="mb-6 flex items-center justify-between"><span className="font-semibold text-white">Menu</span><button type="button" onClick={() => setOpen(false)} aria-label="Close menu" className="flex h-10 w-10 items-center justify-center rounded-full bg-white/10 text-white"><X size={20} /></button></div><div className="flex flex-col gap-1">{links.map((link) => <NavLink key={link.to} to={link.to} end={link.to === "/"} onClick={() => setOpen(false)} className={({ isActive }) => `rounded-2xl px-4 py-3 text-lg font-medium ${isActive ? "bg-white/15 text-white" : "text-white/75"}`}>{link.label}</NavLink>)}</div><div className="mt-6 flex gap-3"><Button as={Link} to="/login" variant="secondary" className="flex-1 !rounded-full !border-white/20 !bg-white/10 !text-white" onClick={() => setOpen(false)}>Log in</Button><Button as={Link} to="/register" className="flex-1 !rounded-full" onClick={() => setOpen(false)}>Sign up</Button></div></div></div> : null}
    </header>
  );
}
