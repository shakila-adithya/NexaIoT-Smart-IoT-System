const toneStyles = {
  default: { title: "var(--page-title-default)", subtitle: "var(--page-subtitle-default)" },
  device: { title: "var(--page-title-device)", subtitle: "var(--page-subtitle-device)" },
  warning: { title: "var(--page-title-warning)", subtitle: "var(--page-subtitle-warning)" },
  automation: { title: "var(--page-title-automation)", subtitle: "var(--page-subtitle-automation)" },
  maintenance: { title: "var(--page-title-maintenance)", subtitle: "var(--page-subtitle-maintenance)" },
  analytics: { title: "var(--page-title-analytics)", subtitle: "var(--page-subtitle-analytics)" },
  account: { title: "var(--page-title-account)", subtitle: "var(--page-subtitle-account)" },
  settings: { title: "var(--page-title-settings)", subtitle: "var(--page-subtitle-settings)" },
};

export default function PageHeader({
  title,
  description,
  breadcrumb,
  status,
  actions,
  eyebrow,
  meta,
  tone = "default",
  className = "",
}) {
  const semanticTone = toneStyles[tone] || toneStyles.default;

  return (
    <header className={`page-header page-header-${tone} flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between ${className}`}>
      <div className="min-w-0">
        {breadcrumb ? <div className="mb-2 text-xs text-[var(--color-text-muted)]">{breadcrumb}</div> : null}
        {eyebrow ? <div className="mb-2 flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--color-primary-hover)]"><span className="h-1.5 w-1.5 rounded-full bg-[var(--color-primary)]" />{eyebrow}</div> : null}
        <div className="flex flex-wrap items-center gap-2.5">
          <h1 className={`page-header-title page-title-${tone} text-[30px] font-semibold leading-9 tracking-[-0.025em]`} style={{ color: semanticTone.title }}>{title}</h1>
          {status ? <div className="shrink-0">{status}</div> : null}
        </div>
        {description ? <p className={`page-header-subtitle page-subtitle-${tone} mt-1.5 max-w-2xl text-sm leading-[21px]`} style={{ color: semanticTone.subtitle }}>{description}</p> : null}
        {meta ? <div className="mt-3 flex flex-wrap items-center gap-2 text-[11px] font-medium text-[var(--color-text-secondary)]">{meta}</div> : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  );
}
