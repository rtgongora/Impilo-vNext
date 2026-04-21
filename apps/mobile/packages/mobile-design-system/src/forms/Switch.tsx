/**
 * Switch — Toggle switch with label.
 */

import React from "react";
import { View, Text, Switch as RNSwitch, StyleSheet } from "react-native";

export interface SwitchProps {
  label?: string;
  value?: boolean;
  onValueChange?: (value: boolean) => void;
  /**
   * Back-compat aliases for older app screens.
   * Prefer `value` + `onValueChange`.
   */
  checked?: boolean;
  onChange?: (value: boolean) => void;
  disabled?: boolean;
  testID?: string;
  accessibilityLabel?: string;
}

export function Switch({
  label,
  value,
  onValueChange,
  checked,
  onChange,
  disabled = false,
  testID,
  accessibilityLabel,
}: SwitchProps) {
  const resolvedValue = value ?? checked ?? false;
  const resolvedOnValueChange = onValueChange ?? onChange ?? (() => {});
  return (
    <View testID={testID} style={styles.container}>
      {label ? (
        <Text style={[styles.label, disabled ? styles.labelDisabled : undefined]}>
          {label}
        </Text>
      ) : null}
      <RNSwitch
        value={resolvedValue}
        onValueChange={resolvedOnValueChange}
        disabled={disabled}
        accessibilityLabel={accessibilityLabel ?? label ?? "Switch"}
        accessibilityRole="switch"
        accessibilityState={{ checked: resolvedValue, disabled }}
        trackColor={{ false: "#E0E0E0", true: "#9DDBB6" }}
        thumbColor={resolvedValue ? "#009739" : "#FAFAFA"}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  label: {
    fontSize: 15,
    color: "#212121",
    flex: 1,
  },
  labelDisabled: {
    color: "#9E9E9E",
  },
});
