/**
 * NetworkStatusBar — Connectivity + offline sync summary (parity with provider app).
 */

import React, { useEffect } from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import NetInfo from "@react-native-community/netinfo";
import { StatusIndicator, colors } from "@impilo/mobile-design-system";
import { useSyncEngine } from "@impilo/mobile-offline";
import { appStore, useAppStore } from "../stores/appStore";

export function NetworkStatusBar() {
  const isOnline = useAppStore((s) => s.isOnline);
  const { status: syncStatus, pendingCount, queue, sync } = useSyncEngine();
  const failedCount = queue.filter((q) => q.status === "FAILED").length;

  useEffect(() => {
    void NetInfo.fetch().then((state) => {
      appStore.getState().setOnlineStatus(!!state.isConnected);
    });
    const unsubscribe = NetInfo.addEventListener((state) => {
      appStore.getState().setOnlineStatus(!!state.isConnected);
    });
    return () => unsubscribe();
  }, []);

  if (isOnline && syncStatus === "idle" && pendingCount === 0 && failedCount === 0) {
    return null;
  }

  const statusText = !isOnline
    ? "Offline — changes will sync when connected"
    : syncStatus === "syncing"
      ? `Syncing ${pendingCount} item(s)…`
      : failedCount > 0
        ? `${failedCount} sync error(s) — tap Retry`
        : pendingCount > 0
          ? `${pendingCount} item(s) pending sync`
          : "";

  if (!statusText) return null;

  return (
    <View
      testID="network-status-bar"
      accessibilityRole="summary"
      style={[
        styles.container,
        { backgroundColor: !isOnline ? colors.ui.error.light : failedCount > 0 ? colors.ui.error.light : colors.ui.warning.light },
      ]}
    >
      <StatusIndicator status={!isOnline ? "offline" : failedCount > 0 ? "error" : "syncing"} size="sm" />
      <Text style={styles.text}>{statusText}</Text>
      {isOnline && failedCount > 0 ? (
        <Pressable onPress={() => void sync()} style={styles.retry} testID="network-retry-sync">
          <Text style={styles.retryText}>Retry</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 8,
    paddingHorizontal: 12,
    gap: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#FDE68A",
  },
  text: {
    flex: 1,
    fontSize: 13,
    color: colors.ui.warning.text,
  },
  retry: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
    backgroundColor: "#FFFFFF",
    borderWidth: 1,
    borderColor: "#FCA5A5",
  },
  retryText: {
    fontSize: 12,
    fontWeight: "700",
    color: "#B91C1C",
  },
});
