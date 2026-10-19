export default function DeviceSummaryCard({
  icon: Icon,
  value,
  label,
  iconClassName = "text-[#08a9c4]",
}) {
  return (
    <div className="flex min-w-0 flex-1 items-center gap-3 rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-[var(--shadow-card)]">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)]">
        <Icon size={16} className={iconClassName} />
      </div>

      <div>
        <div className="text-2xl leading-none text-[var(--color-text-primary)]">
          {value}
        </div>

        <div className="mt-1 text-xs text-[var(--color-text-muted)]">
          {label}
        </div>
      </div>
    </div>
  );
}
