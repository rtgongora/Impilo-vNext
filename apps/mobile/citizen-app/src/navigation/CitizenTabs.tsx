/**
 * CitizenTabs — Bottom tab navigation for the citizen app.
 *
 * Tabs: Home, Personal, Social, Marketplace, Messaging
 * Telehealth is accessed from within Personal or via deep link.
 */

import React from "react";
import { View, StyleSheet } from "react-native";
import { TabBar, type TabItem } from "@impilo/mobile-design-system";
import { useAppStore } from "../stores/appStore";
import type { CitizenTab } from "../types";

import { HomeScreen } from "../screens/HomeScreen";
import { PersonalScreen } from "../screens/personal/PersonalScreen";
import { SocialHubScreen } from "../screens/social/SocialHubScreen";
import { MarketplaceScreen } from "../screens/marketplace/MarketplaceScreen";
import { MessagingInboxScreen } from "../screens/messaging/MessagingInboxScreen";
import { TelehealthListScreen } from "../screens/telehealth/TelehealthListScreen";

const TAB_SCREENS: Record<CitizenTab, React.FC> = {
  home: HomeScreen,
  personal: PersonalScreen,
  social: SocialHubScreen,
  marketplace: MarketplaceScreen,
  messaging: MessagingInboxScreen,
  telehealth: TelehealthListScreen,
};

export function CitizenTabs() {
  const { activeTab, setActiveTab, unreadMessages } = useAppStore();

  const tabs: TabItem[] = [
    { id: "home", label: "Home", icon: "home" },
    { id: "personal", label: "Health", icon: "heart" },
    { id: "social", label: "Feed", icon: "globe" },
    { id: "marketplace", label: "Services", icon: "shopping-bag" },
    {
      id: "messaging",
      label: "Messages",
      icon: "message-circle",
      badge: unreadMessages > 0 ? unreadMessages : undefined,
    },
  ];

  const ScreenComponent = TAB_SCREENS[activeTab] ?? HomeScreen;

  return (
    <View testID="citizen-tabs" style={styles.container}>
      <View style={styles.content}>
        <ScreenComponent />
      </View>
      <TabBar
        tabs={tabs}
        activeTab={activeTab}
        onTabPress={(id: string) => setActiveTab(id as CitizenTab)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
  },
});
