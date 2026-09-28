import { CheckCircle2, AlertTriangle, Info, X } from "lucide-react";

const config = {
  success: { icon: CheckCircle2, cls: "text-success" },
  warning: { icon: AlertTriangle, cls: "text-warning" },
  error: { icon: AlertTriangle, cls: "text-danger" },
  info: { icon: Info, cls: "text-primary" },
};

export default function Toast({ message, type = "success", onDismiss }) {
  const { icon: Icon, cls } = config[type] || config.success;
  return (
    <div
      role="status"
      className="flex items-start gap-3 bg-[var(--bg)] border border-[var(--border)] shadow-soft rounded-xl px-4 py-3 animate-toast-in"
    >
      <Icon size={18} className={`mt-0.5 flex-shrink-0 ${cls}`} />
      <p className="text-sm text-[var(--text)] flex-1">{message}</p>
      <button onClick={onDismiss} aria-label="Dismiss notification" className="text-muted hover:text-[var(--text)]">
        <X size={16} />
      </button>
    </div>
  );
}
