const variants = {
  text: "h-3 w-full",
  card: "h-28 w-full",
  circle: "h-10 w-10 rounded-full",
};

export default function LoadingSkeleton({ variant = "text", className = "", ...props }) {
  return (
    <div
      role="status"
      aria-label="Loading"
      className={`animate-pulse rounded-[var(--radius-sm)] bg-[var(--color-border)] ${variants[variant] || variants.text} ${className}`}
      {...props}
    />
  );
}
