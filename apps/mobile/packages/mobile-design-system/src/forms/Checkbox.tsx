/**
 * Checkbox — Boolean toggle with label.
 */

import React from "react";

export interface CheckboxProps {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  error?: string;
  testID?: string;
}

export function Checkbox({ label, checked, onChange, disabled = false, error, testID }: CheckboxProps) {
  return React.createElement(
    "label",
    { "data-testid": testID, style: { display: "flex", alignItems: "center", gap: 8 } },
    React.createElement("input", {
      type: "checkbox",
      checked,
      onChange: (e: React.ChangeEvent<HTMLInputElement>) => onChange(e.target.checked),
      disabled,
      "aria-invalid": !!error,
    }),
    React.createElement("span", null, label),
    error ? React.createElement("span", { role: "alert" }, error) : null
  );
}
