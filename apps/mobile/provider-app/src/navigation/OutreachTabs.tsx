/**
 * OutreachTabs — Tab navigator for Outreach mode.
 *
 * Tabs: Dashboard, Households, Screenings, Schedule
 */

import React, { useState, useCallback } from "react";
import { View, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TabBar } from "@impilo/mobile-design-system";
import { OutreachDashboardScreen } from "../screens/outreach/OutreachDashboardScreen";
import { HouseholdListScreen } from "../screens/outreach/HouseholdListScreen";
import { ScreeningScreen } from "../screens/outreach/ScreeningScreen";
import { PublicHealthFieldTasksScreen } from "../screens/provider/PublicHealthFieldTasksScreen";

const ACCENT = "#1E40AF";

type TabKey = "dashboard" | "households" | "screenings" | "schedule";

const TABS: Array<{ key: TabKey; label: string; activeIcon: string; inactiveIcon: string }> = [
  { key: "dashboard", label: "Dashboard", activeIcon: "map", inactiveIcon: "map-outline" },
  { key: "households", label: "Households", activeIcon: "home", inactiveIcon: "home-outline" },
  { key: "screenings", label: "Screenings", activeIcon: "clipboard", inactiveIcon: "clipboard-outline" },
  { key: "schedule", label: "Schedule", activeIcon: "calendar", inactiveIcon: "calendar-outline" },
];

export function OutreachTabs() {
  const [activeTab, setActiveTab] = useState<TabKey>("dashboard");

  const handleTabChange = useCallback((key: string) => {
    setActiveTab(key as TabKey);
  }, []);

  const renderContent = () => {
    switch (activeTab) {
      case "dashboard":
        return <OutreachDashboardScreen />;
      case "households":
        return <HouseholdListScreen />;
      case "screenings":
        return <ScreeningScreen />;
      case "schedule":
        return <PublicHealthFieldTasksScreen />;
      default:
        return <OutreachDashboardScreen />;
    }
  };

  return (
    <View testID="outreach-tabs" style={styles.container}>
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
    backgroundColor: "#F8FAFC",
  },
  content: {
    flex: 1,
  },
});
