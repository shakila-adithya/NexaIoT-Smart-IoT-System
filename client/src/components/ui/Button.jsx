const variants = {
  primary: "bg-[var(--color-primary)] text-white hover:bg-[var(--color-primary-hover)]",
  secondary: "border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-text-primary)] hover:bg-[var(--color-surface-subtle)]",
  ghost: "text-[var(--color-text-muted)] hover:bg-[var(--color-surface-subtle)] hover:text-[var(--color-text-primary)]",
  tertiary: "text-[var(--color-primary-hover)] hover:bg-[var(--color-primary-soft)]",
  danger: "bg-[var(--color-danger)] text-white hover:bg-[#c93645]",
};

const sizes = {
  sm: "min-h-9 px-3 text-xs",
  md: "min-h-10 px-4 text-sm",
  lg: "min-h-11 px-6 text-base",
};

export default function Button({
  as: Component = "button",
  variant = "primary",
  size = "md",
  className = "",
  children,
  icon: Icon,
  loading = false,
  loadingLabel,
  disabled = false,
  ...props
}) {
  return (
    <Component
      className={`inline-flex items-center justify-center gap-2 rounded-[var(--radius-md)] font-medium transition-[background-color,border-color,color,box-shadow] duration-200 focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]/35 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant] || variants.primary} ${sizes[size] || sizes.md} ${className}`}
      aria-busy={loading || undefined}
      disabled={loading || disabled}
      {...props}
    >
      {Icon && <Icon size={16} />}
      {loading ? (loadingLabel || children) : children}
    </Component>
  );
}
