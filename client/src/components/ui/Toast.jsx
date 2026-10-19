import { CheckCircle2, AlertTriangle, Info, X } from "lucide-react";

const config = {
  success: { icon: CheckCircle2, cls: "text-[var(--color-success)]", border: "border-[var(--color-success)]/25" },
  warning: { icon: AlertTriangle, cls: "text-[var(--color-warning)]", border: "border-[var(--color-warning)]/30" },
  error: { icon: AlertTriangle, cls: "text-[var(--color-danger)]", border: "border-[var(--color-danger)]/25" },
  info: { icon: Info, cls: "text-[var(--color-primary)]", border: "border-[var(--color-primary)]/25" },
};

export default function Toast({ message, type = "success", onDismiss }) {
  const { icon: Icon, cls, border } = config[type] || config.success;
  return (
    <div
      role="status"
      className={`flex items-start gap-3 rounded-[var(--radius-card)] border bg-[var(--color-surface-elevated)] px-4 py-3 shadow-[var(--shadow-elevated)] animate-toast-in ${border}`}
    >
      <Icon size={18} className={`mt-0.5 flex-shrink-0 ${cls}`} />
      <p className="flex-1 text-sm text-[var(--color-text-primary)]">{message}</p>
      <button type="button" onClick={onDismiss} aria-label="Dismiss notification" className="rounded-[var(--radius-sm)] text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]/35">
        <X size={16} />
      </button>
    </div>
  );
}
