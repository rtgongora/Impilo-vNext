/**
 * CitizenTabs — Bottom tab navigation for the citizen app.
 *
 * Tabs: Home, Personal, Social, Marketplace, Messaging
 * Telehealth is accessed from within Personal or via deep link.
 */

import React from "react";
import { TabBar, type TabItem } from "@impilo/mobile-design-system";
import { useAppStore } from "../stores/appStore";
import type { CitizenTab } from "../types";

import { HomeScreen } from "../screens/HomeScreen";
import { PersonalScreen } from "../screens/personal/PersonalScreen";
import { SocialFeedScreen } from "../screens/social/SocialFeedScreen";
import { MarketplaceScreen } from "../screens/marketplace/MarketplaceScreen";
import { MessagingInboxScreen } from "../screens/messaging/MessagingInboxScreen";
import { TelehealthListScreen } from "../screens/telehealth/TelehealthListScreen";

const TAB_SCREENS: Record<CitizenTab, React.FC> = {
  home: HomeScreen,
  personal: PersonalScreen,
  social: SocialFeedScreen,
  marketplace: MarketplaceScreen,
  messaging: MessagingInboxScreen,
  telehealth: TelehealthListScreen,
};

export function CitizenTabs() {
  const { activeTab, setActiveTab, unreadMessages, unreadNotifications } = useAppStore();

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

  return React.createElement(
    "div",
    {
      "data-testid": "citizen-tabs",
      style: { display: "flex", flexDirection: "column", height: "100%" },
    },
    React.createElement(
      "div",
      { style: { flex: 1, overflow: "auto" } },
      React.createElement(ScreenComponent, null)
    ),
    React.createElement(TabBar, {
      tabs,
      activeTab,
      onTabPress: (id: string) => setActiveTab(id as CitizenTab),
    })
  );
}
