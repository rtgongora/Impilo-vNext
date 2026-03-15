/**
 * Switch — Toggle switch with label.
 */

import React from "react";
import { View, Text, Switch as RNSwitch, StyleSheet } from "react-native";

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
  return (
    <View testID={testID} style={styles.container}>
      <Text style={[styles.label, disabled ? styles.labelDisabled : undefined]}>
        {label}
      </Text>
      <RNSwitch
        value={value}
        onValueChange={onValueChange}
        disabled={disabled}
        accessibilityLabel={accessibilityLabel ?? label}
        accessibilityRole="switch"
        accessibilityState={{ checked: value, disabled }}
        trackColor={{ false: "#E0E0E0", true: "#A5D6A7" }}
        thumbColor={value ? "#43A047" : "#FAFAFA"}
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
