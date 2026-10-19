import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";

export default function Input({ label, error, icon: Icon, className = "", id, type, ...props }) {
  const inputId = id || label?.toLowerCase().replace(/\s+/g, "-");
  const isPassword = type === "password";
  const [showPassword, setShowPassword] = useState(false);
  const inputType = isPassword && showPassword ? "text" : type;
  return (
    <div className={className}>
      {label && (
        <label htmlFor={inputId} className="ui-label mb-1.5 block">
          {label}
        </label>
      )}
      <div className="relative">
        {Icon && <Icon size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" />}
        <input
          id={inputId}
          className={`ui-control w-full px-3.5 py-2 text-sm placeholder:text-[var(--color-text-muted)] ${
            Icon ? "pl-10" : ""
          } ${isPassword ? "pr-10" : ""} ${error ? "border-[var(--color-danger)] focus:border-[var(--color-danger)]" : ""}`}
          aria-invalid={!!error}
          aria-describedby={error ? `${inputId}-error` : undefined}
          type={inputType}
          {...props}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setShowPassword((visible) => !visible)}
            className="absolute right-3 top-1/2 -translate-y-1/2 rounded-[var(--radius-sm)] text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]/40"
            aria-label={showPassword ? "Hide password" : "Show password"}
            aria-pressed={showPassword}
          >
            {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        )}
      </div>
      {error && (
        <p id={`${inputId}-error`} className="mt-1.5 text-xs text-[var(--color-danger)]">
          {error}
        </p>
      )}
    </div>
  );
}
