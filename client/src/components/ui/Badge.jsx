const tones = {
  neutral: "bg-[#edf2f4] text-[var(--color-text-secondary)]",
  primary: "bg-[var(--color-primary-soft)] text-[var(--color-primary-hover)]",
  info: "bg-[var(--color-info-soft)] text-[#416ac2]",
  success: "bg-[var(--color-success-soft)] text-[#16815f]",
  warning: "bg-[var(--color-warning-soft)] text-[#a36a0a]",
  danger: "bg-[var(--color-danger-soft)] text-[#d83f4d]",
};

export default function Badge({ tone = "neutral", children, className = "" }) {
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-[var(--radius-pill)] px-2.5 py-1 text-xs font-medium leading-4 ${tones[tone] || tones.neutral} ${className}`}>
      {children}
    </span>
  );
}
