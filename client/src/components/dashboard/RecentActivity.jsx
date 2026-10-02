import {
  Power,
  Thermometer,
  UserRound,
  WifiOff,
} from "lucide-react";
import { recentActivity } from "../../data/dashboardMockData.js";

const iconMap = {
  power: Power,
  temperature: Thermometer,
  wifi: WifiOff,
  user: UserRound,
};

const toneMap = {
  cyan: "bg-[#e8f8fb] text-[#08a9c4]",
  amber: "bg-[#fff4df] text-[#e59a22]",
  green: "bg-[#e8f7f2] text-[#16a57a]",
  purple: "bg-[#f2edff] text-[#8066d6]",
};

export default function RecentActivity() {
  return (
    <section className="rounded-[14px] border border-[#dce8ee] bg-white p-[18px] shadow-[0_6px_20px_rgba(10,48,72,0.07)]">
      <div className="flex items-start justify-between">
        <div>
          <h2 className="text-[15px] font-semibold text-[#102a3a]">
            Recent activity
          </h2>
          <p className="mt-1 text-[11px] text-[#6b8290]">
            Latest events from your workspace
          </p>
        </div>

        <button type="button" className="mt-2 text-[11px] font-medium text-[#08a9c4]">
          View audit log
        </button>
      </div>

      <div className="mt-3.5 space-y-[14px]">
        {recentActivity.map((item) => {
          const Icon = iconMap[item.icon] || Power;

          return (
            <div key={item.title} className="flex items-center">
              <div
                className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-[9px] ${
                  toneMap[item.tone] || toneMap.cyan
                }`}
              >
                <Icon size={14} strokeWidth={1.9} />
              </div>

              <div className="ml-[11px] min-w-0">
                <div className="truncate text-[11px] font-medium text-[#102a3a]">
                  {item.title}
                </div>
                <div className="mt-1 truncate text-[9px] text-[#8397a2]">
                  {item.meta}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
