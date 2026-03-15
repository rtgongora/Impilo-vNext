/**
 * Switch — Toggle switch with label.
 */

import React from "react";

export interface SwitchProps {
  label: string;
  value: boolean;
  onValueChange: (value: boolean) => void;
  disabled?: boolean;
  testID?: string;
  accessibilityLabel?: string;
}

export function Switch({
  label,
  value,
  onValueChange,
  disabled = false,
  testID,
  accessibilityLabel,
}: SwitchProps) {
  return React.createElement(
    "label",
    {
      "data-testid": testID,
      style: { display: "flex", alignItems: "center", justifyContent: "space-between" },
    },
    React.createElement("span", null, label),
    React.createElement("input", {
      type: "checkbox",
      role: "switch",
      checked: value,
      onChange: (e: React.ChangeEvent<HTMLInputElement>) => onValueChange(e.target.checked),
      disabled,
      "aria-label": accessibilityLabel ?? label,
    })
  );
}
