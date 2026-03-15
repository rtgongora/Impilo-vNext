/**
 * SupervisorTabs — Tab navigator for Supervisor mode.
 *
 * Tabs: Dashboard, Team, Stock, Escalations
 */

import React, { useState, useCallback } from "react";
import { View, StyleSheet } from "react-native";
import { TabBar } from "@impilo/mobile-design-system";
import { SupervisorDashboardScreen } from "../screens/supervisor/SupervisorDashboardScreen";
import { TeamOverviewScreen } from "../screens/supervisor/TeamOverviewScreen";
import { StockScreen } from "../screens/supervisor/StockScreen";
import { EscalationsScreen } from "../screens/supervisor/EscalationsScreen";

const TABS = [
  { key: "dashboard", label: "Dashboard", icon: "bar-chart" },
  { key: "team", label: "Team", icon: "users" },
  { key: "stock", label: "Stock", icon: "package" },
  { key: "escalations", label: "Escalations", icon: "alert-triangle" },
] as const;

type TabKey = (typeof TABS)[number]["key"];

export function SupervisorTabs() {
  const [activeTab, setActiveTab] = useState<TabKey>("dashboard");

  const handleTabChange = useCallback((key: string) => {
    setActiveTab(key as TabKey);
  }, []);

  const renderContent = () => {
    switch (activeTab) {
      case "dashboard":
        return <SupervisorDashboardScreen />;
      case "team":
        return <TeamOverviewScreen />;
      case "stock":
        return <StockScreen />;
      case "escalations":
        return <EscalationsScreen />;
      default:
        return <SupervisorDashboardScreen />;
    }
  };

  return (
    <View testID="supervisor-tabs" style={styles.container}>
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
