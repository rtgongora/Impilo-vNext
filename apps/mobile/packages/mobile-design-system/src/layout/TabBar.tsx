/**
 * TabBar — Bottom tab navigation bar.
 */

import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";

export interface TabItem {
  key?: string;
  /**
   * Back-compat alias for `key` (older app tabs).
   * Prefer `key`.
   */
  id?: string;
  label: string;
  icon?: React.ReactNode;
  badge?: number;
}

export interface TabBarProps {
  tabs?: TabItem[];
  activeTab?: string;
  onTabPress?: (key: string) => void;
  /**
   * Back-compat aliases for older apps.
   * Prefer `tabs`, `activeTab`, `onTabPress`.
   */
  items?: TabItem[];
  activeKey?: string;
  onSelect?: (key: string) => void;
  testID?: string;
}

export function TabBar({ tabs, activeTab, onTabPress, items, activeKey, onSelect, testID }: TabBarProps) {
  const resolvedTabs = tabs ?? items ?? [];
  const resolvedActiveTab = activeTab ?? activeKey ?? "";
  const resolvedOnTabPress = onTabPress ?? onSelect ?? (() => {});
  return (
    <View
      testID={testID}
      accessibilityRole="tablist"
      style={styles.container}
    >
      {resolvedTabs.map((tab) => {
        const tabKey = tab.key ?? tab.id ?? "";
        const isActive = tabKey === resolvedActiveTab;
        return (
          <Pressable
            key={tabKey}
            onPress={() => resolvedOnTabPress(tabKey)}
            accessibilityRole="tab"
            accessibilityState={{ selected: isActive }}
            accessibilityLabel={tab.label}
            style={styles.tab}
          >
            <View>{tab.icon}</View>
            <Text
              style={[
                styles.tabLabel,
                { color: isActive ? "#009739" : "#757575" },
                isActive ? styles.tabLabelActive : undefined,
              ]}
            >
              {tab.label}
            </Text>
            {tab.badge && tab.badge > 0 ? (
              <View style={styles.badge}>
                <Text style={styles.badgeText}>
                  {tab.badge > 99 ? "99+" : tab.badge}
                </Text>
              </View>
            ) : null}
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    justifyContent: "space-around",
    alignItems: "center",
    borderTopWidth: 1,
    borderTopColor: "#E0E0E0",
    paddingVertical: 8,
    backgroundColor: "#FFFFFF",
  },
  tab: {
    alignItems: "center",
    gap: 2,
    paddingVertical: 4,
    paddingHorizontal: 12,
    position: "relative",
  },
  tabLabel: {
    fontSize: 11,
  },
  tabLabelActive: {
    fontWeight: "600",
  },
  badge: {
    position: "absolute",
    top: 0,
    right: 4,
    backgroundColor: "#F44336",
    borderRadius: 8,
    paddingVertical: 1,
    paddingHorizontal: 5,
    minWidth: 16,
    alignItems: "center",
  },
  badgeText: {
    color: "#FFFFFF",
    fontSize: 9,
    fontWeight: "700",
    textAlign: "center",
  },
});
