/**
 * DatePicker — Date selection input with label and validation.
 */

import React, { useState } from "react";
import { View, Text, Pressable, TextInput, Platform, StyleSheet } from "react-native";

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
  return (
    <View testID={testID}>
      <Text style={styles.label}>
        {label}
        {required ? <Text style={styles.requiredAsterisk}> *</Text> : null}
      </Text>
      <TextInput
        value={value}
        onChangeText={(text) => {
          onChange(text);
        }}
        placeholder="YYYY-MM-DD"
        editable={!disabled}
        keyboardType={Platform.OS === "ios" ? "numbers-and-punctuation" : "default"}
        accessibilityLabel={accessibilityLabel ?? label}
        accessibilityState={{ disabled }}
        style={[
          styles.input,
          error ? styles.inputError : undefined,
          disabled ? styles.inputDisabled : undefined,
        ]}
      />
      {error ? (
        <Text accessibilityRole="alert" style={styles.errorText}>
          {error}
        </Text>
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
});
