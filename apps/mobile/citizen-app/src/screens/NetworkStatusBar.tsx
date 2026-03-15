/**
 * NetworkStatusBar — Shows offline status warning.
 */

import React from "react";
import { View, Text, StyleSheet } from "react-native";
import NetInfo from "@react-native-community/netinfo";
import { useAppStore } from "../stores/appStore";

export function NetworkStatusBar() {
  const { isOnline, setIsOnline } = useAppStore();

  React.useEffect(() => {
    const unsubscribe = NetInfo.addEventListener((state) => {
      setIsOnline(!!state.isConnected);
    });
    return () => unsubscribe();
  }, [setIsOnline]);

  if (isOnline) return null;

  return (
    <View
      testID="network-status-bar"
      accessibilityRole="summary"
      style={styles.container}
    >
      <Text style={styles.text}>
        You are currently offline. Some features may be unavailable.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingVertical: 8,
    paddingHorizontal: 16,
    backgroundColor: "#FEF3C7",
    borderBottomWidth: 1,
    borderBottomColor: "#FDE68A",
    alignItems: "center",
  },
  text: {
    fontSize: 13,
    color: "#92400E",
    textAlign: "center",
  },
});
