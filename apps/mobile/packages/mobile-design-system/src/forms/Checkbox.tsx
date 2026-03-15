/**
 * Checkbox — Boolean toggle with label.
 */

import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";

export interface CheckboxProps {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  error?: string;
  testID?: string;
}

export function Checkbox({ label, checked, onChange, disabled = false, error, testID }: CheckboxProps) {
  return (
    <View testID={testID}>
      <Pressable
        onPress={() => {
          if (!disabled) {
            onChange(!checked);
          }
        }}
        disabled={disabled}
        accessibilityRole="checkbox"
        accessibilityState={{ checked, disabled }}
        accessibilityLabel={label}
        style={styles.row}
      >
        <View
          style={[
            styles.box,
            checked ? styles.boxChecked : undefined,
            disabled ? styles.boxDisabled : undefined,
          ]}
        >
          {checked ? <Text style={styles.checkmark}>✓</Text> : null}
        </View>
        <Text style={[styles.label, disabled ? styles.labelDisabled : undefined]}>
          {label}
        </Text>
      </Pressable>
      {error ? (
        <Text accessibilityRole="alert" style={styles.errorText}>
          {error}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  box: {
    width: 22,
    height: 22,
    borderWidth: 2,
    borderColor: "#9E9E9E",
    borderRadius: 4,
    alignItems: "center",
    justifyContent: "center",
  },
  boxChecked: {
    backgroundColor: "#43A047",
    borderColor: "#43A047",
  },
  boxDisabled: {
    backgroundColor: "#F5F5F5",
    borderColor: "#E0E0E0",
  },
  checkmark: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "700",
  },
  label: {
    fontSize: 15,
    color: "#212121",
  },
  labelDisabled: {
    color: "#9E9E9E",
  },
  errorText: {
    fontSize: 12,
    color: "#F44336",
    marginTop: 4,
  },
});
