import { ArrowUpRight } from "lucide-react";

const variants = {
  // solid pill: light on dark theme, dark on light theme
  primary: "bg-primary text-white dark:text-[#04202c] hover:bg-primary-dark dark:hover:bg-primary-light",
  // always a white pill, for use over photography
  white: "bg-white text-neutral-950 hover:bg-white/90",
  // always dark glass, for use over photography
  onDark: "glass-dark hover:bg-white/15",
  secondary: "glass text-ink hover:bg-subtle",
  ghost: "text-muted hover:text-ink hover:bg-subtle",
  danger: "bg-danger text-white hover:opacity-90",
};

const sizes = {
  sm: "text-sm px-4 py-2",
  md: "text-sm px-5 py-2.5",
  lg: "text-base px-7 py-3.5",
};

export default function Button({
  as: Component = "button",
  variant = "primary",
  size = "md",
  className = "",
  children,
  icon: Icon,
  iconEnd: IconEnd,
  ...props
}) {
  return (
    <Component
      className={`inline-flex items-center justify-center gap-2 rounded-full font-medium transition-[background,opacity,transform] duration-150 active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed ${variants[variant]} ${sizes[size]} ${className}`}
      {...props}
    >
      {Icon && <Icon size={16} />}
      {children}
      {IconEnd && <IconEnd size={16} />}
    </Component>
  );
}

/* Round arrow button that sits beside a pill (as in the hero) */
export function ArrowButton({ as: Component = "button", onDark = true, className = "", label = "Open", ...props }) {
  return (
    <Component
      aria-label={label}
      className={`inline-flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full transition-colors ${
        onDark ? "glass-dark hover:bg-white/20" : "glass text-ink hover:bg-subtle"
      } ${className}`}
      {...props}
    >
      <ArrowUpRight size={18} />
    </Component>
  );
}


