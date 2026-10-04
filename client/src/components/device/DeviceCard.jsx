import { Activity, MapPin, MoreHorizontal } from "lucide-react";

export default function DeviceCard({
  icon: Icon,
  name,
  type,
  location,
  status = "OFFLINE",
  powerOn = false,
  onOpen,
  onToggle,
}) {
  const isOnline = status === "ONLINE";
  const isMaintenance = status === "MAINTENANCE";

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

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={handleKeyDown}
      className="w-full cursor-pointer rounded-[14px] border border-[#dce8ee] bg-white p-[18px] shadow-[0_2px_8px_rgba(16,42,58,0.03)] transition hover:-translate-y-0.5 hover:border-[#b9d7df] hover:shadow-[0_8px_22px_rgba(16,42,58,0.08)]"
    >
      <div className="flex items-start justify-between">
        <div className="flex h-10 w-10 items-center justify-center rounded-[10px] bg-[#eef9fb] text-[#08a9c4]">
          <Icon size={19} strokeWidth={1.8} />
        </div>

        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation();
          }}
          className="flex h-8 w-8 items-center justify-center rounded-lg text-[#6b8290] transition hover:bg-[#f4f8fb]"
          aria-label="Device options"
        >
          <MoreHorizontal size={18} />
        </button>
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

      <div className="mt-5 flex items-center justify-between rounded-[10px] bg-[#f8fbfd] px-3 py-3">
        <div className="flex items-center gap-2">
          <Activity size={13} className="text-[#6b8290]" />
          <span className="text-[10px] text-[#6b8290]">
            Power
          </span>
        </div>

        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            onToggle?.();
          }}
          className={`relative h-[22px] w-[40px] rounded-full transition ${
            powerOn ? "bg-[#08a9c4]" : "bg-[#dce8ee]"
          }`}
          aria-label={`Turn ${name} ${powerOn ? "off" : "on"}`}
        >
          <span
            className={`absolute top-[3px] h-4 w-4 rounded-full bg-white shadow-sm transition-all ${
              powerOn ? "left-[21px]" : "left-[3px]"
            }`}
          />
        </button>
      </div>
    </div>
  );
}