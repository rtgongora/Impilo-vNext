/**
 * CitizenTabs — Bottom tab navigation for the citizen app.
 *
 * Tabs: Home, Personal, Social, Marketplace, Messaging
 * Telehealth is accessed from within Personal or via deep link.
 */

import React from "react";
import { View, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TabBar, type TabItem } from "@impilo/mobile-design-system";
import { useAppStore } from "../stores/appStore";
import type { CitizenTab } from "../types";

import { HomeScreen } from "../screens/HomeScreen";
import { PersonalScreen } from "../screens/personal/PersonalScreen";
import { SocialHubScreen } from "../screens/social/SocialHubScreen";
import { MarketplaceScreen } from "../screens/marketplace/MarketplaceScreen";
import { MessagingInboxScreen } from "../screens/messaging/MessagingInboxScreen";
import { TelehealthListScreen } from "../screens/telehealth/TelehealthListScreen";
import { PublicHealthScreen } from "../screens/publicHealth/PublicHealthScreen";

const ACCENT = "#059669";

const TAB_SCREENS: Record<CitizenTab, React.FC> = {
  home: HomeScreen,
  personal: PersonalScreen,
  social: SocialHubScreen,
  marketplace: MarketplaceScreen,
  messaging: MessagingInboxScreen,
  telehealth: TelehealthListScreen,
  public_health: PublicHealthScreen,
};

function tabIcon(name: string, isActive: boolean): React.ReactNode {
  const color = isActive ? ACCENT : "#9CA3AF";
  return <Ionicons name={name as never} size={22} color={color} />;
}

export function CitizenTabs() {
  const { activeTab, setActiveTab, unreadMessages } = useAppStore();

  const tabs: TabItem[] = [
    {
      id: "home",
      label: "Home",
      icon: tabIcon(activeTab === "home" ? "home" : "home-outline", activeTab === "home"),
    },
    {
      id: "personal",
      label: "Health",
      icon: tabIcon(activeTab === "personal" ? "heart" : "heart-outline", activeTab === "personal"),
    },
    {
      id: "social",
      label: "Feed",
      icon: tabIcon(activeTab === "social" ? "earth" : "earth-outline", activeTab === "social"),
    },
    {
      id: "marketplace",
      label: "Services",
      icon: tabIcon(activeTab === "marketplace" ? "storefront" : "storefront-outline", activeTab === "marketplace"),
    },
    {
      id: "messaging",
      label: "Messages",
      icon: tabIcon(activeTab === "messaging" ? "chatbubbles" : "chatbubbles-outline", activeTab === "messaging"),
      badge: unreadMessages > 0 ? unreadMessages : undefined,
    },
    {
      id: "public_health",
      label: "Public",
      icon: tabIcon(activeTab === "public_health" ? "pulse" : "pulse-outline", activeTab === "public_health"),
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
        accentColor={ACCENT}
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
