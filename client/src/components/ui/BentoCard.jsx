export default function BentoCard({
  title,
  description,
  icon: Icon,
  action,
  emphasis = "standard",
  children,
  className = "",
  ...props
}) {
  const emphasisClass = emphasis === "elevated"
    ? "shadow-[var(--shadow-elevated)]"
    : emphasis === "subtle"
      ? "bg-[var(--color-surface-subtle)] shadow-none"
      : "shadow-[var(--shadow-card)]";

  return (
    <section
      className={`glass-panel min-w-0 rounded-[var(--radius-card)] p-5 ${emphasisClass} ${className}`}
      {...props}
    >
      {title || description || Icon || action ? (
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 items-start gap-3">
            {Icon ? <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[var(--radius-md)] bg-[var(--color-primary-soft)] text-[var(--color-primary)]"><Icon size={20} /></span> : null}
            <div className="min-w-0">
              {title ? <h2 className="text-[15px] font-semibold leading-5 text-[var(--color-text-primary)]">{title}</h2> : null}
              {description ? <p className="mt-1 text-xs leading-[18px] text-[var(--color-text-muted)]">{description}</p> : null}
            </div>
          </div>
          {action ? <div className="shrink-0">{action}</div> : null}
        </div>
      ) : null}
      {children ? <div className={title || description || Icon || action ? "mt-5" : ""}>{children}</div> : null}
    </section>
  );
}
