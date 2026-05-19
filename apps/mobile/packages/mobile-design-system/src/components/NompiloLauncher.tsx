/**
 * NompiloLauncher — Floating circular launcher for the Nompilo assistant.
 *
 * Purely presentational. The design system keeps this dependency-free of
 * `@impilo/mobile-nompilo` so apps can choose when to wire it up.
 *
 * The launcher is small, rounded, calm, and uses the Impilo green accent.
 * It is positioned bottom-right with a safe-area-aware offset.
 */

import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

export interface NompiloLauncherProps {
  /** Triggered when the user taps the launcher. */
  onPress: () => void;
  /** Show a pulsing dot indicator (e.g. when a suggestion is available). */
  showHint?: boolean;
  /** Optional accent override. Defaults to Impilo green. */
  accentColor?: string;
  /** Bottom offset above the tab bar. Default 88 to clear bottom tabs. */
  bottomOffset?: number;
  /** Optional initial / character. Default "N". */
  initial?: string;
  testID?: string;
}

export function NompiloLauncher({
  onPress,
  showHint = false,
  accentColor = "#059669",
  bottomOffset = 88,
  initial = "N",
  testID,
}: NompiloLauncherProps) {
  return (
    <Pressable
      testID={testID ?? "nompilo-launcher"}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel="Open Nompilo assistant"
      style={({ pressed }) => [
        styles.fab,
        {
          backgroundColor: accentColor,
          bottom: bottomOffset,
          opacity: pressed ? 0.85 : 1,
        },
      ]}
    >
      <View style={styles.inner}>
        <Text style={styles.initial}>{initial}</Text>
      </View>
      {showHint ? (
        <View style={[styles.hint, { borderColor: accentColor }]} />
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  fab: {
    position: "absolute",
    right: 18,
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.18,
    shadowRadius: 12,
    elevation: 8,
  },
  inner: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "rgba(255,255,255,0.15)",
    alignItems: "center",
    justifyContent: "center",
  },
  initial: {
    fontSize: 22,
    fontWeight: "800",
    color: "#FFFFFF",
    letterSpacing: 0.5,
  },
  hint: {
    position: "absolute",
    top: -2,
    right: -2,
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: "#FFFFFF",
    borderWidth: 2,
  },
});
