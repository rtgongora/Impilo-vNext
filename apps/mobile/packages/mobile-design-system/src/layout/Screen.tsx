/**
 * Screen — Top-level screen wrapper with safe area and scroll support.
 */

import React from "react";
import { View, ScrollView, StyleSheet } from "react-native";

export interface ScreenProps {
  children: React.ReactNode;
  scrollable?: boolean;
  padding?: boolean;
  backgroundColor?: string;
  testID?: string;
}

export function Screen({
  children,
  scrollable = true,
  padding = true,
  backgroundColor,
  testID,
}: ScreenProps) {
  const containerStyle = [
    styles.container,
    { backgroundColor: backgroundColor ?? "#FFFFFF" },
    padding ? styles.padded : undefined,
  ];

  if (scrollable) {
    return (
      <ScrollView
        testID={testID}
        style={styles.container}
        contentContainerStyle={[
          { backgroundColor: backgroundColor ?? "#FFFFFF" },
          padding ? styles.padded : undefined,
          styles.scrollContent,
        ]}
      >
        {children}
      </ScrollView>
    );
  }

  return (
    <View testID={testID} style={containerStyle}>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  padded: {
    padding: 16,
  },
  scrollContent: {
    flexGrow: 1,
  },
});
