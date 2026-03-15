/**
 * Select — Dropdown selection input.
 */

import React from "react";

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps {
  label: string;
  value: string;
  options: SelectOption[];
  onChange: (value: string) => void;
  placeholder?: string;
  error?: string;
  required?: boolean;
  disabled?: boolean;
  testID?: string;
  accessibilityLabel?: string;
}

export function Select({
  label,
  value,
  options,
  onChange,
  placeholder,
  error,
  required = false,
  disabled = false,
  testID,
  accessibilityLabel,
}: SelectProps) {
  return React.createElement(
    "div",
    { "data-testid": testID, "data-error": !!error },
    React.createElement(
      "label",
      { "aria-required": required },
      label,
      required ? React.createElement("span", { "aria-hidden": true }, " *") : null
    ),
    React.createElement(
      "select",
      {
        value,
        onChange: (e: React.ChangeEvent<HTMLSelectElement>) => onChange(e.target.value),
        disabled,
        "aria-label": accessibilityLabel ?? label,
        "aria-invalid": !!error,
      },
      placeholder
        ? React.createElement("option", { value: "", disabled: true }, placeholder)
        : null,
      ...options.map((opt) =>
        React.createElement("option", { key: opt.value, value: opt.value, disabled: opt.disabled }, opt.label)
      )
    ),
    error ? React.createElement("p", { role: "alert" }, error) : null
  );
}
