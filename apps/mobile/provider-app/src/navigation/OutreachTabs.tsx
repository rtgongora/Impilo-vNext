/**
 * OutreachTabs — Tab navigator for Outreach mode.
 *
 * Tabs: Dashboard, Households, Screenings, Schedule
 */

import React, { useState, useCallback } from "react";
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
        return React.createElement(OutreachDashboardScreen, null);
      case "households":
        return React.createElement(HouseholdListScreen, null);
      case "screenings":
        return React.createElement(ScreeningScreen, null);
      case "schedule":
        return React.createElement(FollowUpScreen, null);
      default:
        return React.createElement(OutreachDashboardScreen, null);
    }
  };

  return React.createElement(
    "div",
    { "data-testid": "outreach-tabs" },
    React.createElement("main", { style: { flex: 1 } }, renderContent()),
    React.createElement(TabBar, {
      items: TABS.map((t) => ({ key: t.key, label: t.label })),
      activeKey: activeTab,
      onSelect: handleTabChange,
    })
  );
}
