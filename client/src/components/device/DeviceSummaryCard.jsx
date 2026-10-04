export default function DeviceSummaryCard({
  icon: Icon,
  value,
  label,
  iconClassName = "text-[#08a9c4]",
}) {
  return (
    <div className="flex min-w-0 flex-1 items-center gap-[11px] rounded-[14px] border border-[#dce8ee] bg-white p-[14px]">
      <div className="flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-[10px] bg-[#f8fbfd]">
        <Icon size={16} className={iconClassName} />
      </div>

      <div>
        <div className="text-[20px] leading-none text-[#102a3a]">
          {value}
        </div>

        <div className="mt-1 text-[9px] text-[#6b8290]">
          {label}
        </div>
      </div>
    </div>
  );
}