/**
 * DatePicker — Date selection input with label and validation.
 */

import React from "react";

export interface DatePickerProps {
  label: string;
  value: string;
  onChange: (date: string) => void;
  minDate?: string;
  maxDate?: string;
  error?: string;
  required?: boolean;
  disabled?: boolean;
  testID?: string;
  accessibilityLabel?: string;
}

export function DatePicker({
  label,
  value,
  onChange,
  minDate,
  maxDate,
  error,
  required = false,
  disabled = false,
  testID,
  accessibilityLabel,
}: DatePickerProps) {
  return React.createElement(
    "div",
    { "data-testid": testID, "data-error": !!error },
    React.createElement(
      "label",
      { "aria-required": required },
      label,
      required ? React.createElement("span", { "aria-hidden": true }, " *") : null
    ),
    React.createElement("input", {
      type: "date",
      value,
      onChange: (e: React.ChangeEvent<HTMLInputElement>) => onChange(e.target.value),
      min: minDate,
      max: maxDate,
      disabled,
      "aria-label": accessibilityLabel ?? label,
      "aria-invalid": !!error,
    }),
    error ? React.createElement("p", { role: "alert" }, error) : null
  );
}
