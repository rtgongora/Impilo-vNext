/**
 * TextField — Text input with label, validation, and error state.
 */

import React from "react";
import { View, Text, TextInput, StyleSheet } from "react-native";

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
  testID,
  accessibilityLabel,
}: TextFieldProps) {
  return (
    <View testID={testID}>
      <Text style={styles.label}>
        {label}
        {required ? <Text style={styles.requiredAsterisk}> *</Text> : null}
      </Text>
      <TextInput
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        editable={!disabled}
        secureTextEntry={secureTextEntry}
        multiline={multiline}
        numberOfLines={multiline ? numberOfLines : undefined}
        maxLength={maxLength}
        keyboardType={keyboardType}
        autoCapitalize={autoCapitalize}
        accessibilityLabel={accessibilityLabel ?? label}
        accessibilityState={{ disabled }}
        style={[
          styles.input,
          multiline ? styles.multilineInput : undefined,
          error ? styles.inputError : undefined,
          disabled ? styles.inputDisabled : undefined,
        ]}
      />
      {error ? (
        <Text accessibilityRole="alert" style={styles.errorText}>
          {error}
        </Text>
      ) : helperText ? (
        <Text style={styles.helperText}>{helperText}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  label: {
    fontSize: 14,
    fontWeight: "600",
    marginBottom: 4,
  },
  requiredAsterisk: {
    color: "#F44336",
  },
  input: {
    borderWidth: 1,
    borderColor: "#E0E0E0",
    borderRadius: 8,
    padding: 12,
    fontSize: 15,
    color: "#212121",
    backgroundColor: "#FFFFFF",
  },
  multilineInput: {
    textAlignVertical: "top",
    minHeight: 80,
  },
  inputError: {
    borderColor: "#F44336",
  },
  inputDisabled: {
    backgroundColor: "#F5F5F5",
    color: "#9E9E9E",
  },
  errorText: {
    fontSize: 12,
    color: "#F44336",
    marginTop: 4,
  },
  helperText: {
    fontSize: 12,
    color: "#757575",
    marginTop: 4,
  },
});
