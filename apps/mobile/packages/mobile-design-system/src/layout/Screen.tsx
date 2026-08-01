/**
 * Screen — Top-level screen wrapper with safe area and optional scroll support.
 */

import React from "react";
import { View, ScrollView, StyleSheet, Platform } from "react-native";
import { useOptionalTheme } from "../theme/ThemeProvider";

export interface ScreenProps {
  children: React.ReactNode;
  scrollable?: boolean;
  padding?: boolean;
  backgroundColor?: string;
  testID?: string;
}

export function Screen({
  children,
  scrollable = false,
  padding = false,
  backgroundColor,
  testID,
}: ScreenProps) {
  const { theme } = useOptionalTheme();
  const resolvedBackground = backgroundColor ?? theme.colors.canvas;
  if (scrollable) {
    return (
      <View style={[styles.safe, { backgroundColor: resolvedBackground }]}>
        <ScrollView
          testID={testID}
          style={styles.scroll}
          contentContainerStyle={[
            styles.scrollContent,
            padding ? styles.padded : undefined,
          ]}
          showsVerticalScrollIndicator={false}
        >
          {children}
        </ScrollView>
      </View>
    );
  }

  return (
    <View
      testID={testID}
      style={[
        styles.safe,
        styles.container,
        { backgroundColor: resolvedBackground },
        padding ? styles.padded : undefined,
      ]}
    >
      {children}
    </View>
  );
}

const STATUS_BAR_HEIGHT = Platform.OS === "ios" ? 44 : 24;

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    paddingTop: STATUS_BAR_HEIGHT,
  },
  container: {
    flex: 1,
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    paddingBottom: 24,
  },
  padded: {
    padding: 16,
  },
});
