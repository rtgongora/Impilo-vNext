import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";

export interface OfflineBannerProps {
  visible: boolean;
  message?: string;
  onRetry?: () => void;
}

export function OfflineBanner({
  visible,
  message = "You are offline. Some services require a connection.",
  onRetry,
}: OfflineBannerProps) {
  if (!visible) return null;

  return (
    <View style={styles.banner} accessibilityRole="alert">
      <Ionicons name="cloud-offline-outline" size={18} color="#92400E" />
      <Text style={styles.text}>{message}</Text>
      {onRetry ? (
        <Pressable onPress={onRetry} accessibilityRole="button" accessibilityLabel="Retry connection">
          <Text style={styles.retry}>Retry</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: "#FEF3C7",
    borderBottomWidth: 1,
    borderBottomColor: "#FDE68A",
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  text: {
    flex: 1,
    fontSize: 13,
    color: "#92400E",
    lineHeight: 18,
  },
  retry: {
    fontSize: 13,
    fontWeight: "600",
    color: "#B45309",
  },
});
