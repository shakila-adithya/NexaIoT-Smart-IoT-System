export default function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className = "",
}) {
  return (
    <div className={`empty-state flex min-h-32 flex-col items-center justify-center rounded-[var(--radius-card)] border border-dashed border-[var(--color-border)] bg-[var(--color-surface-subtle)] p-6 text-center ${className}`}>
      {Icon ? <span className="empty-state-icon flex h-11 w-11 items-center justify-center rounded-full bg-[var(--color-primary-soft)] text-[var(--color-primary-hover)]"><Icon size={22} /> </span> : null}
      {title ? <h2 className="mt-3 text-[15px] font-semibold text-[var(--color-text-primary)]">{title}</h2> : null}
      {description ? <p className="mt-1 max-w-md text-xs leading-[18px] text-[var(--color-text-muted)]">{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}
