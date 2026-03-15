/**
 * OutreachTabs — Tab navigator for Outreach mode.
 *
 * Tabs: Dashboard, Households, Screenings, Schedule
 */

import React, { useState, useCallback } from "react";
import { View, StyleSheet } from "react-native";
import { TabBar } from "@impilo/mobile-design-system";
import { OutreachDashboardScreen } from "../screens/outreach/OutreachDashboardScreen";
import { HouseholdListScreen } from "../screens/outreach/HouseholdListScreen";
import { ScreeningScreen } from "../screens/outreach/ScreeningScreen";
import { FollowUpScreen } from "../screens/outreach/FollowUpScreen";

const TABS = [
  { key: "dashboard", label: "Dashboard", icon: "map" },
  { key: "households", label: "Households", icon: "home" },
  { key: "screenings", label: "Screenings", icon: "clipboard-check" },
  { key: "schedule", label: "Schedule", icon: "calendar" },
] as const;

type TabKey = (typeof TABS)[number]["key"];

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
        return <FollowUpScreen />;
      default:
        return <OutreachDashboardScreen />;
    }
  };

  return (
    <View testID="outreach-tabs" style={styles.container}>
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
