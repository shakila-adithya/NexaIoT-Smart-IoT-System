import {
  BellRing,
  ChevronRight,
  FileChartColumn,
  PlusCircle,
  ScanLine,
} from "lucide-react";
import { useNavigate } from "react-router-dom";

const actions = [
  {
    title: "Register a device",
    description: "Add hardware to this workspace",
    icon: PlusCircle,
    tone: "bg-[#e8f8fb] text-[#08a9c4]",
    path: "/devices",
  },
  {
    title: "Run diagnostics",
    description: "Check network and sensor health",
    icon: ScanLine,
    tone: "bg-[#e8f7f2] text-[#16a57a]",
  },
  {
    title: "Generate report",
    description: "Build a performance summary",
    icon: FileChartColumn,
    tone: "bg-[#eef3ff] text-[#5577d6]",
    path: "/reports",
  },
  {
    title: "Manage alerts",
    description: "Review notification rules",
    icon: BellRing,
    tone: "bg-[#fdecee] text-[#e24e5a]",
    path: "/alerts",
  },
];

export default function QuickActions() {
  const navigate = useNavigate();

  return (
    <section className="rounded-[14px] border border-[#dce8ee] bg-white p-[18px] shadow-[0_6px_20px_rgba(10,48,72,0.07)]">
      <div>
        <h2 className="text-[15px] font-semibold text-[#102a3a]">
          Quick actions
        </h2>
        <p className="mt-1 text-[11px] text-[#6b8290]">
          Common network tasks
        </p>
      </div>

      <div className="mt-3.5 divide-y divide-[#edf3f6]">
        {actions.map((action) => {
          const Icon = action.icon;

          return (
            <button
              key={action.title}
              type="button"
              onClick={() => action.path && navigate(action.path)}
              className="group flex min-h-[62px] w-full items-center text-left"
            >
              <div
                className={`flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-[10px] ${action.tone}`}
              >
                <Icon size={16} strokeWidth={1.9} />
              </div>

              <div className="ml-[11px] min-w-0 flex-1">
                <div className="text-[12px] font-medium text-[#102a3a]">
                  {action.title}
                </div>
                <div className="mt-1 truncate text-[10px] text-[#7e929d]">
                  {action.description}
                </div>
              </div>

              <ChevronRight
                size={14}
                className="ml-3 text-[#a8bac4] transition group-hover:translate-x-0.5 group-hover:text-[#08a9c4]"
              />
            </button>
          );
        })}
      </div>
    </section>
  );
}
