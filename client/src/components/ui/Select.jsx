import { useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { Check, ChevronDown } from "lucide-react";
import { createPortal } from "react-dom";

function normalizeOption(option) {
  return typeof option === "string" ? { value: option, label: option } : option;
}

export default function Select({
  id,
  name,
  label,
  value = "",
  onChange,
  options = [],
  placeholder,
  disabled = false,
  required = false,
  error,
  helperText,
  className = "",
  buttonClassName = "",
  "aria-label": ariaLabel,
  "aria-describedby": ariaDescribedBy,
}) {
  const generatedId = useId();
  const controlId = id || `select-${generatedId}`;
  const listboxId = `${controlId}-listbox`;
  const rootRef = useRef(null);
  const triggerRef = useRef(null);
  const menuRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [menuStyle, setMenuStyle] = useState(null);
  const normalizedOptions = options.map(normalizeOption);
  const selectedIndex = normalizedOptions.findIndex((option) => String(option.value) === String(value));
  const selectedOption = selectedIndex >= 0 ? normalizedOptions[selectedIndex] : null;
  const [activeIndex, setActiveIndex] = useState(selectedIndex >= 0 ? selectedIndex : 0);
  const describedBy = [ariaDescribedBy, error ? `${controlId}-error` : null, helperText && !error ? `${controlId}-help` : null].filter(Boolean).join(" ") || undefined;

  useEffect(() => {
    if (!open) return undefined;
    const closeOnOutsideClick = (event) => {
      if (!rootRef.current?.contains(event.target) && !menuRef.current?.contains(event.target)) setOpen(false);
    };
    const closeOnEscape = (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    const updateMenuPosition = () => {
      const trigger = triggerRef.current;
      if (!trigger) return;
      const rect = trigger.getBoundingClientRect();
      const viewportPadding = 12;
      const availableWidth = Math.max(0, window.innerWidth - viewportPadding * 2);
      const width = Math.min(Math.max(rect.width, 220), availableWidth);
      const left = Math.min(Math.max(viewportPadding, rect.left), Math.max(viewportPadding, window.innerWidth - width - viewportPadding));
      setMenuStyle({ top: rect.bottom + 7, left, minWidth: Math.min(rect.width, availableWidth), width });
    };
    updateMenuPosition();
    window.addEventListener("resize", updateMenuPosition);
    window.addEventListener("scroll", updateMenuPosition, true);
    return () => {
      document.removeEventListener("mousedown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
      window.removeEventListener("resize", updateMenuPosition);
      window.removeEventListener("scroll", updateMenuPosition, true);
      setMenuStyle(null);
    };
  }, [open]);

  useLayoutEffect(() => {
    if (!open || !menuRef.current || !triggerRef.current) return;
    const triggerRect = triggerRef.current.getBoundingClientRect();
    const menuRect = menuRef.current.getBoundingClientRect();
    const gap = 7;
    const belowTop = triggerRect.bottom + gap;
    const aboveTop = triggerRect.top - menuRect.height - gap;
    const top = belowTop + menuRect.height <= window.innerHeight - 12 || aboveTop < 12
      ? belowTop
      : aboveTop;
    setMenuStyle((current) => current ? { ...current, top } : current);
  }, [open, normalizedOptions.length]);

  const findNextEnabled = (start, direction) => {
    if (!normalizedOptions.length) return -1;
    let index = start;
    for (let count = 0; count < normalizedOptions.length; count += 1) {
      index = (index + direction + normalizedOptions.length) % normalizedOptions.length;
      if (!normalizedOptions[index]?.disabled) return index;
    }
    return -1;
  };

  const chooseOption = (index) => {
    const option = normalizedOptions[index];
    if (!option || option.disabled) return;
    onChange?.(option.value);
    setOpen(false);
  };

  const handleKeyDown = (event) => {
    if (disabled) return;
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      if (open) chooseOption(activeIndex);
      else setOpen(true);
      return;
    }
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      const next = findNextEnabled(activeIndex, event.key === "ArrowDown" ? 1 : -1);
      if (next >= 0) {
        setActiveIndex(next);
        if (!open) setOpen(true);
      }
      return;
    }
    if (event.key === "Home" || event.key === "End") {
      event.preventDefault();
      const indexes = normalizedOptions.map((option, index) => (!option.disabled ? index : -1)).filter((index) => index >= 0);
      if (indexes.length) setActiveIndex(event.key === "Home" ? indexes[0] : indexes[indexes.length - 1]);
    }
  };

  return (
    <div ref={rootRef} className={`nexa-select ${open ? "is-open" : ""} ${className}`}>
      {label ? <label htmlFor={controlId} className="nexa-select-label">{label}{required ? <span aria-hidden="true"> *</span> : null}</label> : null}
      <input type="hidden" name={name} value={value ?? ""} disabled={disabled} />
      <button
        id={controlId}
        type="button"
        role="combobox"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-haspopup="listbox"
        aria-label={ariaLabel || (!label ? placeholder : undefined)}
        aria-describedby={describedBy}
        aria-invalid={Boolean(error)}
        aria-required={required}
        disabled={disabled}
        ref={triggerRef}
        onClick={() => { setActiveIndex(selectedIndex >= 0 ? selectedIndex : 0); setOpen((current) => !current); }}
        onKeyDown={handleKeyDown}
        className={`nexa-select-trigger ${open ? "is-open" : ""} ${error ? "has-error" : ""} ${buttonClassName}`}
      >
        <span className={selectedOption ? "nexa-select-value" : "nexa-select-placeholder"}>{selectedOption?.label || placeholder || "Select an option"}</span>
        <ChevronDown size={16} aria-hidden="true" className="nexa-select-chevron" />
      </button>
      {open && menuStyle ? createPortal(
        <div ref={menuRef} id={listboxId} role="listbox" aria-label={ariaLabel || label || placeholder} className="nexa-select-menu nexa-select-menu-portal" style={menuStyle}>
          {normalizedOptions.length ? normalizedOptions.map((option, index) => {
            const optionId = `${controlId}-option-${index}`;
            const selected = String(option.value) === String(value);
            return (
              <button
                key={`${option.value}-${index}`}
                id={optionId}
                type="button"
                role="option"
                aria-selected={selected}
                disabled={option.disabled}
                onMouseEnter={() => setActiveIndex(index)}
                onClick={() => chooseOption(index)}
                className={`nexa-select-option ${selected ? "is-selected" : ""} ${activeIndex === index ? "is-active" : ""}`}
              >
                <span className="min-w-0 flex-1 text-left"><span className="block whitespace-normal break-words">{option.label}</span>{option.description ? <span className="mt-0.5 block whitespace-normal break-words text-[11px] text-[var(--color-text-muted)]">{option.description}</span> : null}</span>
                {selected ? <Check size={15} aria-hidden="true" /> : null}
              </button>
            );
          }) : <p className="nexa-select-empty">No options available</p>}
        </div>,
        document.body,
      ) : null}
      {error ? <p id={`${controlId}-error`} className="nexa-select-message is-error" role="alert">{error}</p> : helperText ? <p id={`${controlId}-help`} className="nexa-select-message">{helperText}</p> : null}
    </div>
  );
}
