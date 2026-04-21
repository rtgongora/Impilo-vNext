/**
 * TabBar — Modern bottom tab navigation bar.
 *
 * Accepts icon as ReactNode. Use string icon names or pass icon components
 * from @expo/vector-icons via the consuming app.
 */

import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";

export interface TabItem {
  key?: string;
  id?: string;
  label: string;
  icon?: React.ReactNode;
  badge?: number;
}

export interface TabBarProps {
  tabs?: TabItem[];
  activeTab?: string;
  onTabPress?: (key: string) => void;
  items?: TabItem[];
  activeKey?: string;
  onSelect?: (key: string) => void;
  accentColor?: string;
  testID?: string;
}

export function TabBar({
  tabs,
  activeTab,
  onTabPress,
  items,
  activeKey,
  onSelect,
  accentColor = "#059669",
  testID,
}: TabBarProps) {
  const resolvedTabs = tabs ?? items ?? [];
  const resolvedActiveTab = activeTab ?? activeKey ?? "";
  const resolvedOnTabPress = onTabPress ?? onSelect ?? (() => {});

  return (
    <View testID={testID} accessibilityRole="tablist" style={styles.container}>
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
            style={({ pressed }) => [
              styles.tab,
              { opacity: pressed ? 0.7 : 1 },
            ]}
          >
            <View style={styles.iconWrapper}>
              {tab.icon ? (
                <View style={[styles.iconContainer, isActive && { backgroundColor: accentColor + "15" }]}>
                  {tab.icon}
                </View>
              ) : (
                <View style={[styles.iconFallback, isActive && { backgroundColor: accentColor + "20" }]}>
                  <Text style={[styles.iconFallbackText, { color: isActive ? accentColor : "#9CA3AF" }]}>
                    {tab.label.charAt(0).toUpperCase()}
                  </Text>
                </View>
              )}
              {tab.badge && tab.badge > 0 ? (
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>
                    {tab.badge > 99 ? "99+" : String(tab.badge)}
                  </Text>
                </View>
              ) : null}
            </View>
            <Text
              style={[
                styles.tabLabel,
                {
                  color: isActive ? accentColor : "#9CA3AF",
                  fontWeight: isActive ? "700" : "400",
                },
              ]}
            >
              {tab.label}
            </Text>
            {isActive ? (
              <View style={[styles.activeIndicator, { backgroundColor: accentColor }]} />
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
    paddingTop: 8,
    paddingBottom: 20,
    paddingHorizontal: 4,
    backgroundColor: "#FFFFFF",
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: "#E5E7EB",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: -2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 8,
  },
  tab: {
    flex: 1,
    alignItems: "center",
    gap: 3,
    paddingVertical: 4,
    position: "relative",
  },
  iconWrapper: {
    position: "relative",
  },
  iconContainer: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
  },
  iconFallback: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
  },
  iconFallbackText: {
    fontSize: 16,
    fontWeight: "700",
  },
  tabLabel: {
    fontSize: 10,
    letterSpacing: 0.3,
  },
  activeIndicator: {
    position: "absolute",
    top: -8,
    left: "25%",
    right: "25%",
    height: 3,
    borderRadius: 2,
  },
  badge: {
    position: "absolute",
    top: -2,
    right: -4,
    backgroundColor: "#DC2626",
    borderRadius: 8,
    paddingVertical: 1,
    paddingHorizontal: 4,
    minWidth: 16,
    alignItems: "center",
    borderWidth: 1.5,
    borderColor: "#FFFFFF",
  },
  badgeText: {
    color: "#FFFFFF",
    fontSize: 9,
    fontWeight: "700",
    textAlign: "center",
  },
});
