/**
 * SupervisorTabs — Tab navigator for Supervisor mode.
 *
 * Tabs: Dashboard, Team, Stock, Escalations
 */

import React, { useState, useCallback } from "react";
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
        return React.createElement(SupervisorDashboardScreen, null);
      case "team":
        return React.createElement(TeamOverviewScreen, null);
      case "stock":
        return React.createElement(StockScreen, null);
      case "escalations":
        return React.createElement(EscalationsScreen, null);
      default:
        return React.createElement(SupervisorDashboardScreen, null);
    }
  };

  return React.createElement(
    "div",
    { "data-testid": "supervisor-tabs" },
    React.createElement("main", { style: { flex: 1 } }, renderContent()),
    React.createElement(TabBar, {
      items: TABS.map((t) => ({ key: t.key, label: t.label })),
      activeKey: activeTab,
      onSelect: handleTabChange,
    })
  );
}
