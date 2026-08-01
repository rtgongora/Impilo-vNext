/**
 * TabBar — Modern bottom tab navigation bar.
 *
 * Accepts icon as ReactNode. Use string icon names or pass icon components
 * from @expo/vector-icons via the consuming app.
 */

import React, { useMemo, useState } from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { useOptionalTheme } from "../theme/ThemeProvider";
import { BottomSheet } from "./BottomSheet";
import { partitionTabItems } from "./tab-bar-items";

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
  /** Maximum destinations permanently visible; remaining destinations live under More. */
  maxVisibleItems?: number;
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
  maxVisibleItems = 5,
}: TabBarProps) {
  const { theme } = useOptionalTheme();
  const [moreOpen, setMoreOpen] = useState(false);
  const resolvedTabs = tabs ?? items ?? [];
  const resolvedActiveTab = activeTab ?? activeKey ?? "";
  const resolvedOnTabPress = onTabPress ?? onSelect ?? (() => {});

  const { primaryTabs, overflowTabs } = useMemo(
    () => partitionTabItems(resolvedTabs, maxVisibleItems),
    [maxVisibleItems, resolvedTabs],
  );
  const overflowActive = overflowTabs.some((tab) => (tab.key ?? tab.id ?? "") === resolvedActiveTab);

  function select(tabKey: string) {
    resolvedOnTabPress(tabKey);
    setMoreOpen(false);
  }

  const renderTab = (tab: TabItem) => {
        const tabKey = tab.key ?? tab.id ?? "";
        const isActive = tabKey === resolvedActiveTab;
        return (
          <Pressable
            key={tabKey}
            onPress={() => select(tabKey)}
            testID={`tab-${tabKey}`}
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
                  <Text style={[styles.iconFallbackText, { color: isActive ? accentColor : theme.colors.textTertiary }]}>
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
                  color: isActive ? accentColor : theme.colors.textTertiary,
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
  };

  return (
    <>
      <View
        testID={testID}
        accessibilityRole="tablist"
        style={[styles.container, { backgroundColor: theme.colors.surface, borderTopColor: theme.colors.border }]}
      >
        {primaryTabs.map(renderTab)}
        {overflowTabs.length > 0 ? (
          <Pressable
            onPress={() => setMoreOpen(true)}
            testID="tab-more"
            accessibilityRole="tab"
            accessibilityState={{ selected: overflowActive, expanded: moreOpen }}
            accessibilityLabel="More destinations"
            style={({ pressed }) => [styles.tab, { opacity: pressed ? 0.7 : 1 }]}
          >
            <View style={[styles.iconFallback, overflowActive && { backgroundColor: accentColor + "20" }]}>
              <Text style={[styles.moreIcon, { color: overflowActive ? accentColor : theme.colors.textTertiary }]}>•••</Text>
            </View>
            <Text style={[styles.tabLabel, { color: overflowActive ? accentColor : theme.colors.textTertiary, fontWeight: overflowActive ? "700" : "400" }]}>More</Text>
            {overflowActive ? <View style={[styles.activeIndicator, { backgroundColor: accentColor }]} /> : null}
          </Pressable>
        ) : null}
      </View>
      <BottomSheet isOpen={moreOpen} onClose={() => setMoreOpen(false)} title="More on Impilo" testID="tab-more-sheet">
        <View style={styles.moreGrid}>
          {overflowTabs.map((tab) => {
            const tabKey = tab.key ?? tab.id ?? "";
            const isActive = tabKey === resolvedActiveTab;
            return (
              <Pressable
                key={tabKey}
                onPress={() => select(tabKey)}
                testID={`tab-more-${tabKey}`}
                accessibilityRole="button"
                accessibilityState={{ selected: isActive }}
                style={({ pressed }) => [styles.moreItem, { borderColor: isActive ? accentColor : theme.colors.border, backgroundColor: isActive ? accentColor + "10" : theme.colors.surfaceVariant, opacity: pressed ? 0.72 : 1 }]}
              >
                {tab.icon ? <View style={styles.moreItemIcon}>{tab.icon}</View> : null}
                <Text style={[styles.moreItemLabel, { color: isActive ? accentColor : theme.colors.text }]}>{tab.label}</Text>
                {tab.badge && tab.badge > 0 ? <Text style={styles.moreBadge}>{tab.badge > 99 ? "99+" : tab.badge}</Text> : null}
              </Pressable>
            );
          })}
        </View>
      </BottomSheet>
    </>
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
    minHeight: 52,
    alignItems: "center",
    gap: 3,
    paddingVertical: 4,
    position: "relative",
  },
  moreIcon: { fontSize: 16, fontWeight: "800", letterSpacing: 1 },
  moreGrid: { flexDirection: "row", flexWrap: "wrap", gap: 10, paddingBottom: 24 },
  moreItem: { width: "48%", minHeight: 64, borderWidth: 1, borderRadius: 16, paddingHorizontal: 14, paddingVertical: 12, flexDirection: "row", alignItems: "center", gap: 10 },
  moreItemIcon: { width: 28, alignItems: "center" },
  moreItemLabel: { flex: 1, fontSize: 14, fontWeight: "600" },
  moreBadge: { minWidth: 22, borderRadius: 11, overflow: "hidden", backgroundColor: "#DC2626", paddingHorizontal: 5, paddingVertical: 2, color: "#FFFFFF", fontSize: 10, fontWeight: "700", textAlign: "center" },
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
