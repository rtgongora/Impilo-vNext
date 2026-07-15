/**
 * OtpCodeInput — segmented one-time-code entry.
 *
 * A row of `length` cells backed by a single hidden numeric TextInput (the robust
 * cross-platform RN pattern). Input is sanitized to digits and clamped to `length`;
 * `onComplete` fires once the code is full. No auto-submit side effects beyond the
 * callbacks the caller wires — the parent owns verification.
 */

import React, { useRef } from "react";
import { View, Text, TextInput, Pressable, StyleSheet } from "react-native";
import { sanitizeOtp, otpCells, isOtpComplete, DEFAULT_OTP_LENGTH } from "./otpCode";

export interface OtpCodeInputProps {
  value: string;
  onChangeText: (code: string) => void;
  /** Fired when the code reaches `length` digits. */
  onComplete?: (code: string) => void;
  length?: number;
  disabled?: boolean;
  error?: string;
  autoFocus?: boolean;
  testID?: string;
  accessibilityLabel?: string;
}

export function OtpCodeInput({
  value,
  onChangeText,
  onComplete,
  length = DEFAULT_OTP_LENGTH,
  disabled = false,
  error,
  autoFocus = false,
  testID,
  accessibilityLabel = "One-time verification code",
}: OtpCodeInputProps) {
  const inputRef = useRef<TextInput>(null);
  const cells = otpCells(value, length);
  const focusedIndex = Math.min(sanitizeOtp(value, length).length, length - 1);

  const handleChange = (raw: string) => {
    if (disabled) return;
    const next = sanitizeOtp(raw, length);
    onChangeText(next);
    if (isOtpComplete(next, length)) {
      onComplete?.(next);
    }
  };

  return (
    <View testID={testID}>
      <Pressable
        onPress={() => inputRef.current?.focus()}
        style={styles.row}
        accessibilityLabel={accessibilityLabel}
        accessibilityRole="none"
      >
        {cells.map((char, i) => {
          const isFilled = char !== "";
          const isFocused = i === focusedIndex && !disabled;
          return (
            <View
              key={i}
              testID={testID ? `${testID}-cell-${i}` : undefined}
              style={[
                styles.cell,
                isFilled ? styles.cellFilled : undefined,
                isFocused ? styles.cellFocused : undefined,
                error ? styles.cellError : undefined,
                disabled ? styles.cellDisabled : undefined,
              ]}
            >
              <Text style={styles.cellText}>{char}</Text>
            </View>
          );
        })}
      </Pressable>

      {/* Hidden capture field — one source of truth for the whole code. */}
      <TextInput
        ref={inputRef}
        testID={testID ? `${testID}-field` : undefined}
        value={sanitizeOtp(value, length)}
        onChangeText={handleChange}
        keyboardType="number-pad"
        maxLength={length}
        editable={!disabled}
        autoFocus={autoFocus}
        textContentType="oneTimeCode"
        autoComplete="one-time-code"
        importantForAutofill="yes"
        accessibilityLabel={accessibilityLabel}
        style={styles.hiddenInput}
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
  row: {
    flexDirection: "row",
    gap: 8,
    justifyContent: "center",
  },
  cell: {
    width: 44,
    height: 52,
    borderRadius: 10,
    borderWidth: 1.5,
    borderColor: "#D1D5DB",
    backgroundColor: "#FFFFFF",
    alignItems: "center",
    justifyContent: "center",
  },
  cellFilled: {
    borderColor: "#059669",
  },
  cellFocused: {
    borderColor: "#059669",
    backgroundColor: "#F0FDF4",
  },
  cellError: {
    borderColor: "#DC2626",
  },
  cellDisabled: {
    backgroundColor: "#F3F4F6",
  },
  cellText: {
    fontSize: 22,
    fontWeight: "700",
    color: "#111827",
  },
  hiddenInput: {
    position: "absolute",
    width: 1,
    height: 1,
    opacity: 0,
  },
  errorText: {
    fontSize: 12,
    color: "#DC2626",
    marginTop: 8,
    textAlign: "center",
  },
});
