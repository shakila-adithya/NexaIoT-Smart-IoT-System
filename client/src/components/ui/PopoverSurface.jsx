export default function PopoverSurface({ as: Component = "div", children, className = "", ...props }) {
  return (
    <Component className={`glass-popover dropdown-surface rounded-[var(--radius-card)] p-2 ${className}`} {...props}>
      {children}
    </Component>
  );
}
