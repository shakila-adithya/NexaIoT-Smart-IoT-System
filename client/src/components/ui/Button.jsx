const variants = {
  primary: "bg-primary text-white hover:bg-primary-dark",
  secondary: "bg-white text-ink border border-border hover:bg-subtle dark:bg-transparent dark:text-white dark:border-[var(--border)] dark:hover:bg-white/5",
  ghost: "text-muted hover:text-ink hover:bg-subtle dark:hover:bg-white/5 dark:hover:text-white",
  danger: "bg-danger text-white hover:bg-red-600",
};

const sizes = {
  sm: "text-sm px-3 py-1.5",
  md: "text-sm px-4 py-2.5",
  lg: "text-base px-6 py-3",
};

export default function Button({
  as: Component = "button",
  variant = "primary",
  size = "md",
  className = "",
  children,
  icon: Icon,
  ...props
}) {
  return (
    <Component
      className={`inline-flex items-center justify-center gap-2 rounded-xl font-medium transition-colors duration-150 disabled:opacity-50 disabled:cursor-not-allowed ${variants[variant]} ${sizes[size]} ${className}`}
      {...props}
    >
      {Icon && <Icon size={16} />}
      {children}
    </Component>
  );
}
