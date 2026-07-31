/**
 * OutreachTabs — Tab navigator for Outreach mode.
 *
 * Tabs: Dashboard, Households, Screenings, Postnatal, Schedule
 *
 * Postnatal is the CHW community postnatal contact (day-3/week-6-style home and community visits)
 * — see `PostnatalContactScreen` and docs/clinical/rmnp/chw-community-postnatal-mobile-contract.md.
 * It lives here rather than under Households or the provider specialty panel because it is a
 * distinct, offline-first recording flow a CHW returns to repeatedly, the same reason Screenings
 * has its own tab rather than living inside Households.
 */

import React, { useState, useCallback } from "react";
import { View, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TabBar, useOptionalTheme, colors } from "@impilo/mobile-design-system";
import { OutreachDashboardScreen } from "../screens/outreach/OutreachDashboardScreen";
import { HouseholdListScreen } from "../screens/outreach/HouseholdListScreen";
import { ScreeningScreen } from "../screens/outreach/ScreeningScreen";
import { FollowUpScreen } from "../screens/outreach/FollowUpScreen";
import { PostnatalContactScreen } from "../screens/outreach/PostnatalContactScreen";
import { PublicHealthFieldTasksScreen } from "../screens/provider/PublicHealthFieldTasksScreen";


type TabKey = "dashboard" | "households" | "screenings" | "followups" | "postnatal" | "schedule";

const TABS: Array<{ key: TabKey; label: string; activeIcon: string; inactiveIcon: string }> = [
  { key: "dashboard", label: "Dashboard", activeIcon: "map", inactiveIcon: "map-outline" },
  { key: "households", label: "Households", activeIcon: "home", inactiveIcon: "home-outline" },
  { key: "screenings", label: "Screenings", activeIcon: "clipboard", inactiveIcon: "clipboard-outline" },
  { key: "followups", label: "Follow-ups", activeIcon: "walk", inactiveIcon: "walk-outline" },
  { key: "postnatal", label: "Postnatal", activeIcon: "heart", inactiveIcon: "heart-outline" },
  { key: "schedule", label: "Schedule", activeIcon: "calendar", inactiveIcon: "calendar-outline" },
];

export function OutreachTabs() {
  // Provider identity: teal, not the generic Tailwind blue this file used
  // to hardcode. See App.tsx ThemeProvider mount.
  const { theme } = useOptionalTheme();
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
      case "followups":
        return <FollowUpScreen />;
      case "postnatal":
        return <PostnatalContactScreen />;
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
