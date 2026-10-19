const variants = {
  standard: "glass-panel-strong",
  subtle: "glass-subtle",
  elevated: "glass-panel-strong",
  glass: "glass-panel",
};

export default function Card({ variant = "standard", className = "", children, ...props }) {
  return (
    <div
      className={`rounded-[var(--radius-card)] ${variants[variant] || variants.standard} ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}
