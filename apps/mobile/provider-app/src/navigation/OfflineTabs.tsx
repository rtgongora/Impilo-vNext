/**
 * OfflineTabs — Tab navigator for Offline Edge mode.
 *
 * Tabs: Status, Queue, Conflicts, Break-Glass
 */

import React, { useState, useCallback } from "react";
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
        return React.createElement(OfflineDashboardScreen, null);
      case "queue":
        return React.createElement(LocalQueueScreen, null);
      case "conflicts":
        return React.createElement(ConflictReviewScreen, null);
      case "breakglass":
        return React.createElement(BreakGlassScreen, null);
      default:
        return React.createElement(OfflineDashboardScreen, null);
    }
  };

  return React.createElement(
    "div",
    { "data-testid": "offline-tabs" },
    React.createElement("main", { style: { flex: 1 } }, renderContent()),
    React.createElement(TabBar, {
      items: TABS.map((t) => ({ key: t.key, label: t.label })),
      activeKey: activeTab,
      onSelect: handleTabChange,
    })
  );
}
