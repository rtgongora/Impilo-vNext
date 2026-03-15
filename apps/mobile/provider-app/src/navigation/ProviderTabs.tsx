/**
 * ProviderTabs — Tab navigator for Provider mode.
 *
 * Tabs: Dashboard (Worklist), Patients, Activity, Notifications
 */

import React, { useState, useCallback } from "react";
import { TabBar } from "@impilo/mobile-design-system";
import { ProviderDashboardScreen } from "../screens/provider/ProviderDashboardScreen";
import { PatientLookupScreen } from "../screens/provider/PatientLookupScreen";
import { ActivityFeedScreen } from "../screens/provider/ActivityFeedScreen";
import { NotificationsScreen } from "../screens/NotificationsScreen";
import { useAppStore } from "../stores/appStore";

const TABS = [
  { key: "dashboard", label: "Worklist", icon: "clipboard" },
  { key: "patients", label: "Patients", icon: "users" },
  { key: "activity", label: "Activity", icon: "activity" },
  { key: "notifications", label: "Alerts", icon: "bell" },
] as const;

type TabKey = (typeof TABS)[number]["key"];

export function ProviderTabs() {
  const [activeTab, setActiveTab] = useState<TabKey>("dashboard");
  const { unreadNotifications } = useAppStore();

  const handleTabChange = useCallback((key: string) => {
    setActiveTab(key as TabKey);
  }, []);

  const renderContent = () => {
    switch (activeTab) {
      case "dashboard":
        return React.createElement(ProviderDashboardScreen, null);
      case "patients":
        return React.createElement(PatientLookupScreen, null);
      case "activity":
        return React.createElement(ActivityFeedScreen, null);
      case "notifications":
        return React.createElement(NotificationsScreen, null);
      default:
        return React.createElement(ProviderDashboardScreen, null);
    }
  };

  return React.createElement(
    "div",
    { "data-testid": "provider-tabs" },
    React.createElement("main", { style: { flex: 1 } }, renderContent()),
    React.createElement(TabBar, {
      items: TABS.map((t) => ({
        key: t.key,
        label: t.label,
        badge: t.key === "notifications" && unreadNotifications > 0 ? unreadNotifications : undefined,
      })),
      activeKey: activeTab,
      onSelect: handleTabChange,
    })
  );
}
