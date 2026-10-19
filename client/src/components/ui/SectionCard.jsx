export default function SectionCard({
  title,
  description,
  action,
  compact = false,
  children,
  className = "",
  ...props
}) {
  return (
    <section
      className={`glass-panel-strong surface-interactive rounded-[var(--radius-card)] ${compact ? "p-4" : "p-5"} ${className}`}
      {...props}
    >
      {title || description || action ? (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            {title ? <h2 className="text-[18px] font-semibold leading-6 text-[var(--color-text-primary)]">{title}</h2> : null}
            {description ? <p className="mt-1 text-xs leading-[18px] text-[var(--color-text-muted)]">{description}</p> : null}
          </div>
          {action ? <div className="shrink-0">{action}</div> : null}
        </div>
      ) : null}
      {children ? <div className={title || description || action ? "mt-4" : ""}>{children}</div> : null}
    </section>
  );
}
