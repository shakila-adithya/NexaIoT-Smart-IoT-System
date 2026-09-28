export default function Input({ label, error, icon: Icon, className = "", id, ...props }) {
  const inputId = id || label?.toLowerCase().replace(/\s+/g, "-");
  return (
    <div className={className}>
      {label && (
        <label htmlFor={inputId} className="block text-sm font-medium text-[var(--text)] mb-1.5">
          {label}
        </label>
      )}
      <div className="relative">
        {Icon && <Icon size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" />}
        <input
          id={inputId}
          className={`w-full rounded-xl border bg-[var(--bg)] text-[var(--text)] placeholder:text-muted px-3.5 py-2.5 text-sm outline-none transition-colors ${
            Icon ? "pl-10" : ""
          } ${error ? "border-danger focus:border-danger" : "border-[var(--border)] focus:border-primary"}`}
          aria-invalid={!!error}
          aria-describedby={error ? `${inputId}-error` : undefined}
          {...props}
        />
      </div>
      {error && (
        <p id={`${inputId}-error`} className="mt-1.5 text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
