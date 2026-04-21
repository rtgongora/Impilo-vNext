/**
 * OfflineTabs — Tab navigator for Offline Edge mode.
 *
 * Tabs: Status, Queue, Conflicts, Break-Glass
 */

import React, { useState, useCallback } from "react";
import { View, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TabBar } from "@impilo/mobile-design-system";
import { OfflineDashboardScreen } from "../screens/offline/OfflineDashboardScreen";
import { LocalQueueScreen } from "../screens/offline/LocalQueueScreen";
import { ConflictReviewScreen } from "../screens/offline/ConflictReviewScreen";
import { BreakGlassScreen } from "../screens/offline/BreakGlassScreen";

const ACCENT = "#B45309";

type TabKey = "status" | "queue" | "conflicts" | "breakglass";

const TABS: Array<{ key: TabKey; label: string; activeIcon: string; inactiveIcon: string }> = [
  { key: "status", label: "Status", activeIcon: "wifi", inactiveIcon: "wifi-outline" },
  { key: "queue", label: "Queue", activeIcon: "list", inactiveIcon: "list-outline" },
  { key: "conflicts", label: "Conflicts", activeIcon: "git-merge", inactiveIcon: "git-merge-outline" },
  { key: "breakglass", label: "Emergency", activeIcon: "shield", inactiveIcon: "shield-outline" },
];

export function OfflineTabs() {
  const [activeTab, setActiveTab] = useState<TabKey>("status");

  const handleTabChange = useCallback((key: string) => {
    setActiveTab(key as TabKey);
  }, []);

  const renderContent = () => {
    switch (activeTab) {
      case "status":
        return <OfflineDashboardScreen />;
      case "queue":
        return <LocalQueueScreen />;
      case "conflicts":
        return <ConflictReviewScreen />;
      case "breakglass":
        return <BreakGlassScreen />;
      default:
        return <OfflineDashboardScreen />;
    }
  };

  return (
    <View testID="offline-tabs" style={styles.container}>
      <View style={styles.content}>{renderContent()}</View>
      <TabBar
        items={TABS.map((t) => ({
          key: t.key,
          label: t.label,
          icon: (
            <Ionicons
              name={(activeTab === t.key ? t.activeIcon : t.inactiveIcon) as never}
              size={22}
              color={activeTab === t.key ? ACCENT : "#9CA3AF"}
            />
          ),
        }))}
        activeKey={activeTab}
        onSelect={handleTabChange}
        accentColor={ACCENT}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#FFFBEB",
  },
  content: {
    flex: 1,
  },
});
