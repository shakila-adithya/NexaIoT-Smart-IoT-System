import { useEffect, useRef, useState } from "react";
import { Activity, MapPin, MoreHorizontal, Pencil, Power, Trash2 } from "lucide-react";

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

  const statusTextClass = isOnline
    ? "text-[#16a57a]"
    : isMaintenance
      ? "text-[#d49a24]"
      : "text-[#e24e5a]";

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
      className="w-full cursor-pointer rounded-[14px] border border-[#dce8ee] bg-white p-[18px] shadow-[0_2px_8px_rgba(16,42,58,0.03)] transition-[transform,box-shadow,border-color,background-color] duration-250 ease-out hover:-translate-y-[3px] hover:border-[#8fd4df] hover:bg-[#fcfeff] hover:shadow-[0_10px_24px_rgba(16,125,151,0.12)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#08a9c4]/35 focus-visible:ring-offset-2 motion-reduce:transform-none motion-reduce:transition-none"
    >
      <div className="flex items-start justify-between">
        <div className="flex h-10 w-10 items-center justify-center rounded-[10px] bg-[#eef9fb] text-[#08a9c4]">
          <Icon size={19} strokeWidth={1.8} />
        </div>

        <div ref={menuRef} className="relative">
          <button type="button" onClick={(event) => { event.stopPropagation(); setMenuOpen((open) => !open); }} className="flex h-8 w-8 items-center justify-center rounded-lg text-[#6b8290] transition hover:bg-[#f4f8fb]" aria-label="Device options" aria-expanded={menuOpen}>
            <MoreHorizontal size={18} />
          </button>
          {menuOpen ? (
            <div className="absolute right-0 top-full z-20 mt-1.5 w-36 rounded-[10px] border border-[#dce8ee] bg-white p-1.5 shadow-[0_10px_24px_rgba(16,42,58,0.14)]">
              <button type="button" onClick={(event) => { event.stopPropagation(); setMenuOpen(false); onOpen?.(); }} className="flex w-full items-center gap-2 rounded-[7px] px-2.5 py-2 text-left text-[10px] text-[#526b79] hover:bg-[#f4f8fb] hover:text-[#102a3a]"><Activity size={13} />Open device</button>
              <button type="button" onClick={(event) => { event.stopPropagation(); setMenuOpen(false); onEdit?.(); }} className="flex w-full items-center gap-2 rounded-[7px] px-2.5 py-2 text-left text-[10px] text-[#526b79] hover:bg-[#f4f8fb] hover:text-[#102a3a]"><Pencil size={13} />Edit device</button>
              <button type="button" onClick={(event) => { event.stopPropagation(); setMenuOpen(false); onDelete?.(); }} className="flex w-full items-center gap-2 rounded-[7px] px-2.5 py-2 text-left text-[10px] text-[#d83f4d] hover:bg-red-50"><Trash2 size={13} />Delete device</button>
            </div>
          ) : null}
        </div>
      </div>

      <div className="mt-4">
        <h3 className="truncate text-[14px] font-semibold text-[#102a3a]">
          {name}
        </h3>

        <p className="mt-1 truncate text-[10px] text-[#6b8290]">
          {type}
        </p>
      </div>

      <div className="mt-4 flex items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-2 text-[10px] text-[#6b8290]">
          <MapPin size={13} className="shrink-0" />
          <span className="truncate">{location}</span>
        </div>

        <div className="flex shrink-0 items-center gap-1.5">
          <span
            className={`h-[7px] w-[7px] rounded-full ${statusDotClass}`}
          />

          <span className={`text-[10px] font-medium ${statusTextClass}`}>
            {status}
          </span>
        </div>
      </div>

      <div className={`mt-5 flex items-center justify-between rounded-[10px] px-3 py-3 transition-colors duration-200 ${powerOn ? "bg-[#eefafd]" : "bg-[#f8fbfd]"}`}>
        <div className="flex items-center gap-2">
          <Power size={13} className={powerOn ? "text-[#08a9c4]" : "text-[#6b8290]"} />
          <span className="text-[10px] text-[#6b8290]">Power</span>
        </div>

        <div className="flex items-center gap-2.5">
          {supportsPowerControl ? (
            <span className={`rounded-full px-2 py-0.5 text-[9px] font-semibold transition-colors duration-200 ${powerPending ? "bg-[#fff4df] text-[#b77913]" : powerOn ? "bg-[#d9f4f8] text-[#087f98]" : "bg-[#edf2f4] text-[#6b8290]"}`}>
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
