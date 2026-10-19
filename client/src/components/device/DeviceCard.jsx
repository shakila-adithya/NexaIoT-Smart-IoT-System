import { useEffect, useRef, useState } from "react";
import { Activity, MapPin, MoreHorizontal, Pencil, Power, Trash2 } from "lucide-react";
import StatusBadge from "../ui/StatusBadge.jsx";

export default function DeviceCard({
  icon: Icon,
  name,
  type,
  location,
  status = "OFFLINE",
  powerOn = false,
  supportsPowerControl = false,
  controlLoading = false,
  pendingPower = null,
  onOpen,
  onToggle,
  onEdit,
  onDelete,
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);
  const isOnline = status === "ONLINE";
  const isMaintenance = status === "MAINTENANCE";
  const powerPending = controlLoading && pendingPower !== null;

  const statusDotClass = isOnline
    ? "bg-[#16a57a]"
    : isMaintenance
      ? "bg-[#d49a24]"
      : "bg-[#e24e5a]";

  const handleKeyDown = (event) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      onOpen?.();
    }
  };

  useEffect(() => {
    if (!menuOpen) return undefined;

    const closeMenu = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) setMenuOpen(false);
    };
    const closeOnEscape = (event) => {
      if (event.key === "Escape") setMenuOpen(false);
    };

    document.addEventListener("mousedown", closeMenu);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeMenu);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [menuOpen]);

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={handleKeyDown}
      className="group flex min-h-[286px] w-full cursor-pointer flex-col rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-card)] transition-[transform,box-shadow,border-color,background-color] duration-200 ease-out hover:-translate-y-1 hover:border-[var(--color-primary)]/40 hover:bg-[var(--color-surface-elevated)] hover:shadow-[var(--shadow-elevated)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]/35 focus-visible:ring-offset-2 motion-reduce:transform-none motion-reduce:transition-none"
    >
      <div className="flex items-start justify-between">
        <div className="flex h-10 w-10 items-center justify-center rounded-[var(--radius-md)] bg-[var(--color-primary-soft)] text-[var(--color-primary)]">
          <Icon size={19} strokeWidth={1.8} />
        </div>

        <div ref={menuRef} className="relative">
          <button type="button" onClick={(event) => { event.stopPropagation(); setMenuOpen((open) => !open); }} className="flex h-9 w-9 items-center justify-center rounded-[var(--radius-md)] text-[var(--color-text-muted)] transition hover:bg-[var(--color-surface-subtle)] hover:text-[var(--color-text-primary)] focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]/35" aria-label="Device options" aria-expanded={menuOpen}>
            <MoreHorizontal size={18} />
          </button>
          {menuOpen ? (
            <div className="surface-glass dropdown-surface absolute right-0 top-full z-20 mt-1.5 w-40 p-1.5">
              <button type="button" onClick={(event) => { event.stopPropagation(); setMenuOpen(false); onOpen?.(); }} className="dropdown-action flex w-full items-center gap-2 rounded-[var(--radius-sm)] px-2.5 py-2 text-left text-xs text-[var(--color-text-secondary)] hover:bg-[var(--color-primary-soft)] hover:text-[var(--color-text-primary)]"><Activity size={13} />Open device</button>
              <button type="button" onClick={(event) => { event.stopPropagation(); setMenuOpen(false); onEdit?.(); }} className="dropdown-action flex w-full items-center gap-2 rounded-[var(--radius-sm)] px-2.5 py-2 text-left text-xs text-[var(--color-text-secondary)] hover:bg-[var(--color-primary-soft)] hover:text-[var(--color-text-primary)]"><Pencil size={13} />Edit device</button>
              <button type="button" onClick={(event) => { event.stopPropagation(); setMenuOpen(false); onDelete?.(); }} className="dropdown-action flex w-full items-center gap-2 rounded-[var(--radius-sm)] px-2.5 py-2 text-left text-xs text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)]"><Trash2 size={13} />Delete device</button>
            </div>
          ) : null}
        </div>
      </div>

      <div className="mt-4">
        <h3 className="truncate text-[15px] font-semibold text-[var(--color-text-primary)]">
          {name}
        </h3>

        <p className="mt-1 truncate text-xs text-[var(--color-text-muted)]">
          {type}
        </p>
      </div>

      <div className="mt-4 flex items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-2 text-xs text-[var(--color-text-muted)]">
          <MapPin size={13} className="shrink-0" />
          <span className="truncate">{location}</span>
        </div>

        <div className="flex shrink-0 items-center gap-1.5">
          <span
            className={`h-[7px] w-[7px] rounded-full ${statusDotClass}`}
          />

          <StatusBadge tone={isOnline ? "success" : isMaintenance ? "warning" : "danger"}>{status}</StatusBadge>
        </div>
      </div>

      <div className={`mt-auto flex items-center justify-between rounded-[var(--radius-md)] border px-3 py-3 transition-colors duration-200 ${powerOn ? "border-[var(--color-primary)]/20 bg-[var(--color-primary-soft)]" : "border-[var(--color-border)] bg-[var(--color-surface-subtle)]"}`}>
        <div className="flex items-center gap-2">
          <Power size={13} className={powerOn ? "text-[var(--color-primary)]" : "text-[var(--color-text-muted)]"} />
          <span className="text-xs text-[var(--color-text-secondary)]">Power</span>
        </div>

        <div className="flex items-center gap-2.5">
          {supportsPowerControl ? (
            <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold transition-colors duration-200 ${powerPending ? "bg-[var(--color-warning-soft)] text-[var(--color-warning)]" : powerOn ? "bg-[var(--color-primary-soft)] text-[var(--color-primary-hover)]" : "bg-[var(--color-surface-subtle)] text-[var(--color-text-muted)]"}`}>
              {powerPending ? `Turning ${pendingPower ? "on" : "off"}...` : powerOn ? "ON" : "OFF"}
            </span>
          ) : null}

          {supportsPowerControl ? (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                onToggle?.();
              }}
              disabled={controlLoading}
              className={`relative h-[22px] w-[40px] rounded-full transition duration-200 ${
                powerOn ? "bg-[#08a9c4]" : "bg-[#dce8ee]"
              } ${controlLoading ? "cursor-not-allowed opacity-50" : ""}`}
              aria-label={powerPending ? `Turning ${pendingPower ? "power on" : "power off"}` : `Turn power ${powerOn ? "off" : "on"}`}
            >
              <span
                className={`absolute top-[3px] h-4 w-4 rounded-full bg-white shadow-sm transition-all duration-200 ${
                  powerOn ? "left-[21px]" : "left-[3px]"
                }`}
              />
            </button>
          ) : (
            <span className="text-[10px] text-[#9aaeba]">Not supported</span>
          )}
        </div>
      </div>
    </div>
  );
}
