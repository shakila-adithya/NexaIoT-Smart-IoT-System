export default function DashboardStatCard({
  icon: Icon,
  value,
  label,
  trend,
  tone = "cyan",
}) {
  const tones = {
    cyan: {
      tile: "bg-[#e8f8fb] text-[#08a9c4]",
      trend: "text-[#08a9c4]",
    },
    green: {
      tile: "bg-[#e8f7f2] text-[#16a57a]",
      trend: "text-[#16a57a]",
    },
    amber: {
      tile: "bg-[#fff4df] text-[#e59a22]",
      trend: "text-[#d88910]",
    },
    red: {
      tile: "bg-[#fdecee] text-[#e24e5a]",
      trend: "text-[#e24e5a]",
    },
  };

  const style = tones[tone] || tones.cyan;

  return (
    <div className="h-[129px] rounded-[14px] border border-[#dce8ee] bg-white p-4 shadow-[0_6px_20px_rgba(10,48,72,0.07)]">
      <div className="flex items-center justify-between">
        <div
          className={`flex h-9 w-9 items-center justify-center rounded-[10px] ${style.tile}`}
        >
          <Icon size={18} strokeWidth={1.9} />
        </div>

        <span className={`text-[11px] font-medium ${style.trend}`}>
          {trend}
        </span>
      </div>

      <div className="mt-[13px]">
        <div className="text-[26px] font-semibold leading-none text-[#102a3a]">
          {value}
        </div>
        <div className="mt-1.5 text-[11px] text-[#6b8290]">{label}</div>
      </div>
    </div>
  );
}
