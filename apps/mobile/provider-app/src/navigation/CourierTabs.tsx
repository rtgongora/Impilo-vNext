/**
 * CourierTabs — Nhume Courier mode tabs.
 *
 * Tabs: Assignments (lifecycle controls) | Proof & custody | Notifications
 */

import React, { useState, useCallback } from "react";
import { View, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TabBar } from "@impilo/mobile-design-system";
import { CourierDashboardScreen } from "../screens/courier/CourierDashboardScreen";
import { CourierProofScreen } from "../screens/courier/CourierProofScreen";
import { NotificationsScreen } from "../screens/NotificationsScreen";

const ACCENT = "#0D9488";

type TabKey = "assignments" | "proof" | "alerts";

const TABS: Array<{ key: TabKey; label: string; activeIcon: string; inactiveIcon: string }> = [
  { key: "assignments", label: "Assignments", activeIcon: "cube", inactiveIcon: "cube-outline" },
  { key: "proof", label: "Proof & custody", activeIcon: "shield-checkmark", inactiveIcon: "shield-checkmark-outline" },
  { key: "alerts", label: "Alerts", activeIcon: "notifications", inactiveIcon: "notifications-outline" },
];

export function CourierTabs() {
  const [activeTab, setActiveTab] = useState<TabKey>("assignments");

  const handleTabChange = useCallback((key: string) => {
    setActiveTab(key as TabKey);
  }, []);

  const renderContent = () => {
    switch (activeTab) {
      case "assignments":
        return <CourierDashboardScreen />;
      case "proof":
        return <CourierProofScreen />;
      case "alerts":
        return <NotificationsScreen />;
      default:
        return <CourierDashboardScreen />;
    }
  };

  return (
    <View testID="courier-tabs" style={styles.container}>
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
  container: { flex: 1, backgroundColor: "#F8FAFC" },
  content: { flex: 1 },
});
