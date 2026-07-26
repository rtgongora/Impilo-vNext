/**
 * NetworkStatusBar — Shows offline/online status.
 *
 * Listens to NetInfo connectivity changes and syncs with appStore.
 */

import React, { useEffect } from "react";
import { View, Text, StyleSheet } from "react-native";
import NetInfo from "@react-native-community/netinfo";
import { StatusIndicator, colors } from "@impilo/mobile-design-system";
import { useAppStore, appStore } from "../stores/appStore";
import { useSyncEngine } from "@impilo/mobile-offline";

export function NetworkStatusBar() {
  const { isOnline, pendingSyncCount } = useAppStore();
  const { status: syncStatus } = useSyncEngine();

  useEffect(() => {
    // Set initial state
    NetInfo.fetch().then((state) => {
      appStore.getState().setOnlineStatus(!!state.isConnected);
    });

    // Subscribe to connectivity changes
    const unsubscribe = NetInfo.addEventListener((state) => {
      appStore.getState().setOnlineStatus(!!state.isConnected);
    });

    return () => {
      unsubscribe();
    };
  }, []);

  if (isOnline && syncStatus === "idle" && pendingSyncCount === 0) {
    return null;
  }

  const statusText = !isOnline
    ? "Offline \u2014 changes will sync when connected"
    : syncStatus === "syncing"
      ? `Syncing ${pendingSyncCount} items...`
      : pendingSyncCount > 0
        ? `${pendingSyncCount} items pending sync`
        : "";

  if (!statusText) return null;

  return (
    <View
      testID="network-status-bar"
      style={[
        styles.container,
        { backgroundColor: isOnline ? colors.ui.warning.light : colors.ui.error.light },
      ]}
    >
      <StatusIndicator
        status={isOnline ? "syncing" : "offline"}
        size="sm"
      />
      <Text style={styles.statusText}>{statusText}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
  statusText: {
    fontSize: 14,
    marginLeft: 8,
  },
});
