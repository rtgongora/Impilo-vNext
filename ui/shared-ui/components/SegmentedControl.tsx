"use client";

import React from "react";

/**
 * A row of mutually exclusive options, sized for a gloved finger on a tablet at a bedside.
 *
 * Clinical data entry is mostly picking one answer from a short list, and a dropdown makes
 * that three interactions (open, scan, pick) with the current answer hidden until you open
 * it. A segmented control makes it one interaction with every option and the current answer
 * visible at a glance, which matters when the person entering the data is also holding a
 * child.
 *
 * Falls back to a select when the option set is too long or too wordy to lay out as
 * segments — a control that overflows its row is worse than a dropdown.
 *
 * Keyboard: arrow keys move between options, matching the radiogroup pattern, so the
 * control is usable without a pointer.
 */
export interface SegmentedControlOption {
  value: string;
  label: string;
  /** Colours the option when selected. Use sparingly and only where it carries meaning. */
  tone?: "default" | "positive" | "warning" | "danger" | "muted";
  disabled?: boolean;
}

export interface SegmentedControlProps {
  options: SegmentedControlOption[];
  value?: string | null;
  onChange: (value: string) => void;
  /** Allows clearing by re-selecting the chosen option. */
  allowDeselect?: boolean;
  size?: "sm" | "md";
  name?: string;
  className?: string;
  "aria-label"?: string;
  "aria-labelledby"?: string;
  "data-testid"?: string;
  /** Above this many options the control renders as a select instead. */
  maxSegments?: number;
}

const TONE_SELECTED: Record<string, string> = {
  default: "bg-primary text-primary-foreground border-primary",
  positive: "bg-emerald-600 text-white border-emerald-600",
  warning: "bg-amber-500 text-white border-amber-500",
  danger: "bg-red-600 text-white border-red-600",
  muted: "bg-muted text-foreground border-border",
};

export function SegmentedControl({
  options,
  value,
  onChange,
  allowDeselect = false,
  size = "md",
  name,
  className = "",
  "aria-label": ariaLabel,
  "aria-labelledby": ariaLabelledBy,
  "data-testid": testId,
  maxSegments = 6,
}: SegmentedControlProps) {
  const buttonRefs = React.useRef<Array<HTMLButtonElement | null>>([]);

  const totalLabelLength = options.reduce((sum, option) => sum + option.label.length, 0);
  // Long or wordy option sets wrap unreadably on a phone; a select is the honest fallback.
  const useSelect = options.length > maxSegments || totalLabelLength > 60;

  if (useSelect) {
    return (
      <select
        name={name}
        aria-label={ariaLabel}
        aria-labelledby={ariaLabelledBy}
        data-testid={testId}
        className={`block w-full rounded-lg border border-border bg-background px-3 py-2 text-sm ${className}`}
        value={value ?? ""}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">Select…</option>
        {options.map((option) => (
          <option key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </option>
        ))}
      </select>
    );
  }

  const selectedIndex = options.findIndex((option) => option.value === value);

  const focusOption = (index: number) => {
    const count = options.length;
    if (count === 0) return;
    const next = ((index % count) + count) % count;
    buttonRefs.current[next]?.focus();
  };

  const onKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>, index: number) => {
    switch (event.key) {
      case "ArrowRight":
      case "ArrowDown":
        event.preventDefault();
        focusOption(index + 1);
        break;
      case "ArrowLeft":
      case "ArrowUp":
        event.preventDefault();
        focusOption(index - 1);
        break;
      case "Home":
        event.preventDefault();
        focusOption(0);
        break;
      case "End":
        event.preventDefault();
        focusOption(options.length - 1);
        break;
      default:
        break;
    }
  };

  const padding = size === "sm" ? "px-3 py-2" : "px-4 py-2.5";

  return (
    <div
      role="radiogroup"
      aria-label={ariaLabel}
      aria-labelledby={ariaLabelledBy}
      data-testid={testId}
      className={`flex flex-wrap gap-1 rounded-lg border border-border bg-muted/40 p-1 ${className}`}
    >
      {options.map((option, index) => {
        const selected = option.value === value;
        const tone = TONE_SELECTED[option.tone ?? "default"] ?? TONE_SELECTED.default;
        return (
          <button
            key={option.value}
            ref={(element) => {
              buttonRefs.current[index] = element;
            }}
            type="button"
            role="radio"
            aria-checked={selected}
            disabled={option.disabled}
            // Only the selected option (or the first, when none is selected) is in the tab
            // order; arrow keys move within the group, as the radiogroup pattern expects.
            tabIndex={selected || (selectedIndex === -1 && index === 0) ? 0 : -1}
            data-testid={testId ? `${testId}-option-${option.value}` : undefined}
            onKeyDown={(event) => onKeyDown(event, index)}
            onClick={() => {
              if (option.disabled) return;
              if (selected && allowDeselect) {
                onChange("");
                return;
              }
              onChange(option.value);
            }}
            // min-h-[44px] keeps the target reachable with a gloved finger.
            className={`min-h-[44px] flex-1 whitespace-nowrap rounded-md border text-sm font-medium transition-colors ${padding} ${
              selected
                ? tone
                : "border-transparent bg-background text-foreground hover:bg-muted"
            } ${option.disabled ? "cursor-not-allowed opacity-50" : ""}`}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
