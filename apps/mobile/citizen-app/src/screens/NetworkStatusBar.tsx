import React, { useEffect, useRef } from "react";
import { Animated, Pressable, StyleSheet, Text, View } from "react-native";
import NetInfo from "@react-native-community/netinfo";
import { Ionicons } from "@expo/vector-icons";
import { useSyncEngine } from "@impilo/mobile-offline";
import { appStore, useAppStore } from "../stores/appStore";

type BannerMode = "offline" | "error" | "syncing" | "pending" | null;

function getMode(
  isOnline: boolean,
  syncStatus: string,
  pendingCount: number,
  failedCount: number,
): BannerMode {
  if (!isOnline) return "offline";
  if (failedCount > 0) return "error";
  if (syncStatus === "syncing") return "syncing";
  if (pendingCount > 0) return "pending";
  return null;
}

const MODE_CONFIG: Record<
  Exclude<BannerMode, null>,
  {
    bg: string;
    border: string;
    icon: React.ComponentProps<typeof Ionicons>["name"];
    iconColor: string;
    textColor: string;
    label: (pending: number, failed: number) => string;
  }
> = {
  offline: {
    bg: "#FEF2F2",
    border: "#FECACA",
    icon: "cloud-offline-outline",
    iconColor: "#DC2626",
    textColor: "#991B1B",
    label: () => "Offline — changes will sync when reconnected",
  },
  error: {
    bg: "#FEF2F2",
    border: "#FECACA",
    icon: "warning-outline",
    iconColor: "#DC2626",
    textColor: "#991B1B",
    label: (_, f) => `${f} sync error${f !== 1 ? "s" : ""} — tap Retry`,
  },
  syncing: {
    bg: "#EFF6FF",
    border: "#BFDBFE",
    icon: "sync-outline",
    iconColor: "#2563EB",
    textColor: "#1E40AF",
    label: (p) => `Syncing ${p} item${p !== 1 ? "s" : ""}…`,
  },
  pending: {
    bg: "#FFFBEB",
    border: "#FDE68A",
    icon: "time-outline",
    iconColor: "#D97706",
    textColor: "#92400E",
    label: (p) => `${p} item${p !== 1 ? "s" : ""} pending sync`,
  },
};

export function NetworkStatusBar() {
  const isOnline = useAppStore((s) => s.isOnline);
  const { status: syncStatus, pendingCount, queue, sync } = useSyncEngine();
  const failedCount = queue.filter((q) => q.status === "FAILED").length;

  const mode = getMode(isOnline, syncStatus, pendingCount, failedCount);

  const slideAnim = useRef(new Animated.Value(-48)).current;
  const prevMode = useRef(mode);

  useEffect(() => {
    if (mode !== null && prevMode.current === null) {
      Animated.spring(slideAnim, {
        toValue: 0,
        tension: 120,
        friction: 14,
        useNativeDriver: true,
      }).start();
    } else if (mode === null && prevMode.current !== null) {
      Animated.timing(slideAnim, {
        toValue: -48,
        duration: 200,
        useNativeDriver: true,
      }).start();
    }
    prevMode.current = mode;
  }, [mode, slideAnim]);

  useEffect(() => {
    void NetInfo.fetch().then((state) => {
      appStore.getState().setOnlineStatus(!!state.isConnected);
    });
    const unsub = NetInfo.addEventListener((state) => {
      appStore.getState().setOnlineStatus(!!state.isConnected);
    });
    return () => unsub();
  }, []);

  if (!mode) return null;

  const cfg = MODE_CONFIG[mode];

  return (
    <Animated.View
      testID="network-status-bar"
      accessibilityRole="alert"
      style={[
        styles.container,
        { backgroundColor: cfg.bg, borderBottomColor: cfg.border, transform: [{ translateY: slideAnim }] },
      ]}
    >
      <View style={[styles.iconWrap, { backgroundColor: cfg.bg }]}>
        <Ionicons
          name={cfg.icon}
          size={15}
          color={cfg.iconColor}
          style={syncStatus === "syncing" && mode === "syncing" ? styles.spinIcon : undefined}
        />
      </View>
      <Text style={[styles.text, { color: cfg.textColor }]}>
        {cfg.label(pendingCount, failedCount)}
      </Text>
      {isOnline && failedCount > 0 ? (
        <Pressable
          onPress={() => void sync()}
          style={styles.retryBtn}
          testID="network-retry-sync"
          hitSlop={8}
        >
          <Ionicons name="refresh-outline" size={13} color="#DC2626" />
          <Text style={styles.retryText}>Retry</Text>
        </Pressable>
      ) : null}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 7,
    paddingHorizontal: 14,
    gap: 8,
    borderBottomWidth: 1,
  },
  iconWrap: {
    width: 24,
    height: 24,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  spinIcon: {},
  text: { flex: 1, fontSize: 12, fontWeight: "500", lineHeight: 16 },
  retryBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 10,
    backgroundColor: "#FFFFFF",
    borderWidth: 1,
    borderColor: "#FECACA",
  },
  retryText: { fontSize: 12, fontWeight: "700", color: "#B91C1C" },
});
