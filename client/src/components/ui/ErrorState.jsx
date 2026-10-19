export default function ErrorState({
  title = "Something went wrong",
  message = "We could not load this information right now.",
  action,
  retry,
  className = "",
}) {
  return (
    <div role="alert" className={`rounded-[var(--radius-card)] border border-[var(--color-danger)]/25 bg-[var(--color-danger-soft)] p-5 text-center ${className}`}>
      <h2 className="text-[15px] font-semibold text-[var(--color-text-primary)]">{title}</h2>
      <p className="mt-1 text-xs leading-[18px] text-[var(--color-text-secondary)]">{message}</p>
      {retry ? <button type="button" onClick={retry} className="mt-4 rounded-[var(--radius-md)] bg-[var(--color-surface)] px-3 py-2 text-xs font-semibold text-[var(--color-danger)] shadow-sm focus-visible:ring-2 focus-visible:ring-[var(--color-danger)]/35">Try again</button> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}
