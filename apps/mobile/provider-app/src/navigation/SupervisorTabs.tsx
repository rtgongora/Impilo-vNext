/**
 * SupervisorTabs — Tab navigator for Supervisor mode.
 *
 * Tabs: Work Home, Dashboard, Team, Stock, Inventory, Escalations
 */

import React, { useState, useCallback, useLayoutEffect } from "react";
import { View, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TabBar, useOptionalTheme, colors } from "@impilo/mobile-design-system";
import { WorkHomeScreen } from "../screens/provider/WorkHomeScreen";
import { SupervisorDashboardScreen } from "../screens/supervisor/SupervisorDashboardScreen";
import { TeamOverviewScreen } from "../screens/supervisor/TeamOverviewScreen";
import { StockScreen } from "../screens/supervisor/StockScreen";
import { InventoryScreen } from "../screens/supervisor/InventoryScreen";
import { EscalationsScreen } from "../screens/supervisor/EscalationsScreen";
import { appStore } from "../stores/appStore";


type TabKey = "workhome" | "dashboard" | "team" | "stock" | "inventory" | "escalations";

const TABS: Array<{ key: TabKey; label: string; activeIcon: string; inactiveIcon: string }> = [
  { key: "workhome", label: "Work Home", activeIcon: "home", inactiveIcon: "home-outline" },
  { key: "dashboard", label: "Dashboard", activeIcon: "bar-chart", inactiveIcon: "bar-chart-outline" },
  { key: "team", label: "Team", activeIcon: "people", inactiveIcon: "people-outline" },
  { key: "stock", label: "Stock", activeIcon: "cube", inactiveIcon: "cube-outline" },
  { key: "inventory", label: "Inventory", activeIcon: "albums", inactiveIcon: "albums-outline" },
  { key: "escalations", label: "Escalations", activeIcon: "warning", inactiveIcon: "warning-outline" },
];

export function SupervisorTabs() {
  // Provider identity: teal, not the generic Tailwind blue this file used
  // to hardcode. See App.tsx ThemeProvider mount.
  const { theme } = useOptionalTheme();
  const [activeTab, setActiveTab] = useState<TabKey>("workhome");

  useLayoutEffect(() => {
    const entry = appStore.getState().supervisorEntryTab;
    if (entry) {
      setActiveTab(entry as TabKey);
      appStore.getState().setSupervisorEntryTab(null);
    }
  }, []);

  const handleTabChange = useCallback((key: string) => {
    setActiveTab(key as TabKey);
  }, []);

  const renderContent = () => {
    switch (activeTab) {
      case "workhome":
        return <WorkHomeScreen />;
      case "dashboard":
        return <SupervisorDashboardScreen />;
      case "team":
        return <TeamOverviewScreen />;
      case "stock":
        return <StockScreen />;
      case "inventory":
        return <InventoryScreen />;
      case "escalations":
        return <EscalationsScreen />;
      default:
        return <WorkHomeScreen />;
    }
  };

  return (
    <View testID="supervisor-tabs" style={styles.container}>
      <View style={styles.content}>{renderContent()}</View>
      <TabBar
        items={TABS.map((t) => ({
          key: t.key,
          label: t.label,
          icon: (
            <Ionicons
              name={(activeTab === t.key ? t.activeIcon : t.inactiveIcon) as never}
              size={22}
              color={activeTab === t.key ? theme.colors.primary : colors.gray[400]}
            />
          ),
        }))}
        activeKey={activeTab}
        onSelect={handleTabChange}
        accentColor={theme.colors.primary}
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
