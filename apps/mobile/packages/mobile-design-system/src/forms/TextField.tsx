/**
 * TextField — Text input with label, validation, and error state.
 */

import React from "react";

export interface TextFieldProps {
  label: string;
  value: string;
  onChangeText: (text: string) => void;
  placeholder?: string;
  error?: string;
  helperText?: string;
  required?: boolean;
  disabled?: boolean;
  secureTextEntry?: boolean;
  multiline?: boolean;
  numberOfLines?: number;
  maxLength?: number;
  keyboardType?: "default" | "email-address" | "numeric" | "phone-pad";
  autoCapitalize?: "none" | "sentences" | "words" | "characters";
  autoComplete?: string;
  testID?: string;
  accessibilityLabel?: string;
}

export function TextField({
  label,
  value,
  onChangeText,
  placeholder,
  error,
  helperText,
  required = false,
  disabled = false,
  secureTextEntry = false,
  multiline = false,
  numberOfLines = 1,
  maxLength,
  keyboardType = "default",
  autoCapitalize = "sentences",
  autoComplete,
  testID,
  accessibilityLabel,
}: TextFieldProps) {
  const inputType = secureTextEntry ? "password" : keyboardType === "email-address" ? "email" : keyboardType === "numeric" ? "number" : keyboardType === "phone-pad" ? "tel" : "text";
  const tag = multiline ? "textarea" : "input";

  return React.createElement(
    "div",
    { "data-testid": testID, "data-error": !!error },
    React.createElement(
      "label",
      { "aria-required": required },
      label,
      required ? React.createElement("span", { "aria-hidden": true }, " *") : null
    ),
    React.createElement(tag, {
      type: multiline ? undefined : inputType,
      value,
      onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => onChangeText(e.target.value),
      placeholder,
      disabled,
      maxLength,
      autoComplete,
      autoCapitalize,
      rows: multiline ? numberOfLines : undefined,
      "aria-label": accessibilityLabel ?? label,
      "aria-invalid": !!error,
      "aria-describedby": error ? `${testID}-error` : helperText ? `${testID}-helper` : undefined,
    }),
    error
      ? React.createElement("p", { id: `${testID}-error`, role: "alert" }, error)
      : helperText
        ? React.createElement("p", { id: `${testID}-helper` }, helperText)
        : null
  );
}
