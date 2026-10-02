import { useNavigate } from "react-router-dom";

export default function DeviceStatusCard() {
  const navigate = useNavigate();

  return (
    <section className="rounded-[14px] border border-[#dce8ee] bg-white p-[18px] shadow-[0_6px_20px_rgba(10,48,72,0.07)]">
      <div className="flex items-center justify-between">
        <h2 className="text-[15px] font-semibold text-[#102a3a]">
          Device status
        </h2>

        <button
          type="button"
          onClick={() => navigate("/devices")}
          className="text-[11px] font-medium text-[#08a9c4]"
        >
          View all
        </button>
      </div>

      <div className="mt-[15px] flex items-center gap-5">
        <div className="relative h-28 w-28 shrink-0">
          <div
            className="absolute inset-0 rounded-full"
            style={{
              background:
                "conic-gradient(#16a57a 0deg 345deg, #e8eff3 345deg 360deg)",
            }}
          />
          <div className="absolute inset-[14px] flex flex-col items-center justify-center rounded-full bg-white">
            <span className="text-[20px] font-semibold text-[#102a3a]">96%</span>
            <span className="text-[9px] text-[#6b8290]">online</span>
          </div>
        </div>

        <div className="min-w-0 flex-1 space-y-[10px]">
          <div className="flex items-center justify-between text-[11px]">
            <span className="flex items-center gap-2 text-[#6b8290]">
              <span className="h-[7px] w-[7px] rounded-full bg-[#16a57a]" />
              Online
            </span>
            <span className="font-semibold text-[#102a3a]">46</span>
          </div>

          <div className="flex items-center justify-between text-[11px]">
            <span className="flex items-center gap-2 text-[#6b8290]">
              <span className="h-[7px] w-[7px] rounded-full bg-[#e24e5a]" />
              Offline
            </span>
            <span className="font-semibold text-[#102a3a]">2</span>
          </div>

          <div className="flex items-center justify-between text-[11px]">
            <span className="flex items-center gap-2 text-[#6b8290]">
              <span className="h-[7px] w-[7px] rounded-full bg-[#e59a22]" />
              Maintenance
            </span>
            <span className="font-semibold text-[#102a3a]">3</span>
          </div>
        </div>
      </div>

      <div className="mt-[15px]">
        <div className="flex items-center justify-between text-[10px]">
          <span className="text-[#6b8290]">Network quality</span>
          <span className="font-semibold text-[#16a57a]">Excellent</span>
        </div>

        <div className="mt-[7px] h-1.5 overflow-hidden rounded-full bg-[#e8eff3]">
          <div className="h-full w-full rounded-full bg-[#16a57a]" />
        </div>
      </div>
    </section>
  );
}
