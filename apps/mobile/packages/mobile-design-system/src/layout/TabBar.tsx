/**
 * TabBar — Bottom tab navigation bar.
 */

import React from "react";

export interface TabItem {
  key: string;
  label: string;
  icon: React.ReactNode;
  badge?: number;
}

export interface TabBarProps {
  tabs: TabItem[];
  activeTab: string;
  onTabPress: (key: string) => void;
  testID?: string;
}

export function TabBar({ tabs, activeTab, onTabPress, testID }: TabBarProps) {
  return React.createElement(
    "nav",
    {
      "data-testid": testID,
      role: "tablist",
      style: {
        display: "flex",
        justifyContent: "space-around",
        alignItems: "center",
        borderTop: "1px solid #E0E0E0",
        padding: "8px 0",
        backgroundColor: "#FFFFFF",
      },
    },
    ...tabs.map((tab) =>
      React.createElement(
        "button",
        {
          key: tab.key,
          role: "tab",
          "aria-selected": tab.key === activeTab,
          "aria-label": tab.label,
          onClick: () => onTabPress(tab.key),
          style: {
            display: "flex",
            flexDirection: "column" as const,
            alignItems: "center",
            gap: 2,
            padding: "4px 12px",
            color: tab.key === activeTab ? "#43A047" : "#757575",
            fontWeight: tab.key === activeTab ? 600 : 400,
            fontSize: 11,
            position: "relative" as const,
            background: "none",
            border: "none",
            cursor: "pointer",
          },
        },
        React.createElement("span", null, tab.icon),
        React.createElement("span", null, tab.label),
        tab.badge && tab.badge > 0
          ? React.createElement(
              "span",
              {
                style: {
                  position: "absolute",
                  top: 0,
                  right: 4,
                  backgroundColor: "#F44336",
                  color: "#FFFFFF",
                  fontSize: 9,
                  fontWeight: 700,
                  borderRadius: 8,
                  padding: "1px 5px",
                  minWidth: 16,
                  textAlign: "center" as const,
                },
              },
              tab.badge > 99 ? "99+" : tab.badge
            )
          : null
      )
    )
  );
}
