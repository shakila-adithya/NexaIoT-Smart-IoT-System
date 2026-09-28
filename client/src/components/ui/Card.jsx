export default function Card({ className = "", children, ...props }) {
  return (
    <div
      className={`bg-[var(--bg)] border border-[var(--border)] rounded-2xl shadow-card ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}
