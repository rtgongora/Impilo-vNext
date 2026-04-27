/**
 * DictationAssistButton — Guides clinicians/citizens to native speech input
 * without starting hidden recording or sending audio to the server.
 */
import React, { useCallback } from "react";
import { View, Text, TouchableOpacity, StyleSheet, Alert, Platform } from "react-native";

export interface DictationAssistButtonProps {
  /** Screen reader label for the narrative field this assists */
  fieldLabel?: string;
  testID?: string;
}

export function DictationAssistButton({ fieldLabel = "this field", testID }: DictationAssistButtonProps) {
  const onPress = useCallback(() => {
    const platformHint =
      Platform.OS === "ios"
        ? "Tap the microphone on the iOS keyboard after focusing the text field, or enable Dictation in Settings."
        : "Use the microphone on your keyboard (e.g. Gboard voice typing) after focusing the text field.";
    Alert.alert(
      "Voice typing",
      `Impilo does not record audio in the background.\n\n${platformHint}\n\nReview text before saving; structured codes and doses must still be entered as structured data.`,
      [{ text: "OK" }],
    );
  }, []);

  return (
    <TouchableOpacity
      testID={testID ?? "dictation-assist-btn"}
      accessibilityRole="button"
      accessibilityLabel={`Voice typing help for ${fieldLabel}`}
      onPress={onPress}
      style={styles.wrap}
      hitSlop={8}
    >
      <View style={styles.micBadge} accessibilityElementsHidden>
        <Text style={styles.micBadgeText}>V</Text>
      </View>
      <Text style={styles.caption}>Voice</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  micBadge: {
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: "#2563EB",
    alignItems: "center",
    justifyContent: "center",
  },
  micBadgeText: {
    fontSize: 11,
    fontWeight: "800",
    color: "#FFFFFF",
  },
  wrap: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 8,
    backgroundColor: "#EFF6FF",
    alignSelf: "flex-start",
  },
  caption: {
    fontSize: 12,
    fontWeight: "600",
    color: "#1D4ED8",
  },
});
