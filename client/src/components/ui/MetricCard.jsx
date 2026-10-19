export default function MetricCard({
  label,
  value,
  unit,
  status,
  icon: Icon,
  trend,
  loading = false,
  className = "",
}) {
  return (
    <article className={`glass-panel-strong surface-interactive rounded-[var(--radius-card)] p-4 ${className}`}>
      {loading ? (
        <div className="space-y-3" aria-busy="true" aria-label={`Loading ${label || "metric"}`}>
          <div className="h-3 w-24 animate-pulse rounded bg-[var(--color-border)]" />
          <div className="h-8 w-28 animate-pulse rounded bg-[var(--color-surface-subtle)]" />
        </div>
      ) : (
        <>
          <div className="flex items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-2">
              {Icon ? <Icon size={18} className="shrink-0 text-[var(--color-primary)]" /> : null}
              <span className="truncate text-xs font-semibold uppercase tracking-[0.06em] text-[var(--color-text-muted)]">{label}</span>
            </div>
            {status ? <span className="text-xs text-[var(--color-text-muted)]">{status}</span> : null}
          </div>
          <div className="mt-4 flex items-baseline gap-1.5">
            <span className="text-[28px] font-semibold leading-8 tracking-[-0.03em] text-[var(--color-text-primary)]">{value == null ? "—" : value}</span>
            {unit ? <span className="text-xs font-medium text-[var(--color-text-muted)]">{unit}</span> : null}
          </div>
          {trend ? <div className="mt-2 text-xs text-[var(--color-text-secondary)]">{trend}</div> : null}
        </>
      )}
    </article>
  );
}
