/**
 * Select — Dropdown selection input.
 */

import React, { useState } from "react";
import { View, Text, Pressable, ScrollView, Modal, StyleSheet } from "react-native";

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
  const [isOpen, setIsOpen] = useState(false);
  const selectedOption = options.find((opt) => opt.value === value);
  const displayText = selectedOption?.label ?? placeholder ?? "Select...";

  return (
    <View testID={testID}>
      <Text style={styles.label}>
        {label}
        {required ? <Text style={styles.requiredAsterisk}> *</Text> : null}
      </Text>
      <Pressable
        onPress={() => {
          if (!disabled) {
            setIsOpen(true);
          }
        }}
        disabled={disabled}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel ?? label}
        accessibilityState={{ disabled, expanded: isOpen }}
        style={[
          styles.trigger,
          error ? styles.triggerError : undefined,
          disabled ? styles.triggerDisabled : undefined,
        ]}
      >
        <Text
          style={[
            styles.triggerText,
            !selectedOption ? styles.placeholderText : undefined,
          ]}
        >
          {displayText}
        </Text>
        <Text style={styles.chevron}>▼</Text>
      </Pressable>

      <Modal
        visible={isOpen}
        transparent
        animationType="fade"
        onRequestClose={() => setIsOpen(false)}
      >
        <Pressable style={styles.overlay} onPress={() => setIsOpen(false)}>
          <View style={styles.dropdown}>
            <ScrollView>
              {options.map((opt) => (
                <Pressable
                  key={opt.value}
                  onPress={() => {
                    if (!opt.disabled) {
                      onChange(opt.value);
                      setIsOpen(false);
                    }
                  }}
                  disabled={opt.disabled}
                  accessibilityRole="button"
                  accessibilityState={{
                    selected: opt.value === value,
                    disabled: opt.disabled,
                  }}
                  style={[
                    styles.option,
                    opt.value === value ? styles.optionSelected : undefined,
                    opt.disabled ? styles.optionDisabled : undefined,
                  ]}
                >
                  <Text
                    style={[
                      styles.optionText,
                      opt.value === value ? styles.optionTextSelected : undefined,
                      opt.disabled ? styles.optionTextDisabled : undefined,
                    ]}
                  >
                    {opt.label}
                  </Text>
                </Pressable>
              ))}
            </ScrollView>
          </View>
        </Pressable>
      </Modal>

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
  trigger: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    borderWidth: 1,
    borderColor: "#E0E0E0",
    borderRadius: 8,
    padding: 12,
    backgroundColor: "#FFFFFF",
  },
  triggerError: {
    borderColor: "#F44336",
  },
  triggerDisabled: {
    backgroundColor: "#F5F5F5",
  },
  triggerText: {
    fontSize: 15,
    color: "#212121",
    flex: 1,
  },
  placeholderText: {
    color: "#9E9E9E",
  },
  chevron: {
    fontSize: 10,
    color: "#757575",
    marginLeft: 8,
  },
  overlay: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.5)",
  },
  dropdown: {
    width: "80%",
    maxHeight: 300,
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    paddingVertical: 8,
    elevation: 8,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 8,
  },
  option: {
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  optionSelected: {
    backgroundColor: "#E8F5E9",
  },
  optionDisabled: {
    opacity: 0.4,
  },
  optionText: {
    fontSize: 15,
    color: "#212121",
  },
  optionTextSelected: {
    color: "#2E7D32",
    fontWeight: "600",
  },
  optionTextDisabled: {
    color: "#9E9E9E",
  },
  errorText: {
    fontSize: 12,
    color: "#F44336",
    marginTop: 4,
  },
});
