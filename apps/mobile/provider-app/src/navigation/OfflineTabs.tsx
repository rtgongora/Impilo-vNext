/**
 * OfflineTabs — Tab navigator for Offline Edge mode.
 *
 * Tabs: Status, Queue, Conflicts, Break-Glass
 */

import React, { useState, useCallback } from "react";
import { View, StyleSheet } from "react-native";
import { TabBar } from "@impilo/mobile-design-system";
import { OfflineDashboardScreen } from "../screens/offline/OfflineDashboardScreen";
import { LocalQueueScreen } from "../screens/offline/LocalQueueScreen";
import { ConflictReviewScreen } from "../screens/offline/ConflictReviewScreen";
import { BreakGlassScreen } from "../screens/offline/BreakGlassScreen";

const TABS = [
  { key: "status", label: "Status", icon: "wifi-off" },
  { key: "queue", label: "Queue", icon: "list" },
  { key: "conflicts", label: "Conflicts", icon: "git-merge" },
  { key: "breakglass", label: "Emergency", icon: "shield" },
] as const;

type TabKey = (typeof TABS)[number]["key"];

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
        items={TABS.map((t) => ({ key: t.key, label: t.label }))}
        activeKey={activeTab}
        onSelect={handleTabChange}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
  },
});
