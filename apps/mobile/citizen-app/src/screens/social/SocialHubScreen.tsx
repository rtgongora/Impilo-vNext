/**
 * SocialHubScreen — Wraps social features with tab navigation.
 * Tabs: Feed, Communities, Clubs, Providers, Crowdfunding
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Header, type TabItem, colors } from "@impilo/mobile-design-system";
import { SocialFeedScreen } from "./SocialFeedScreen";
import { ClubsScreen } from "./ClubsScreen";
import { ProfessionalPagesScreen } from "./ProfessionalPagesScreen";
import { CrowdfundingScreen } from "./CrowdfundingScreen";
import { CommunitiesScreen } from "./CommunitiesScreen";
import { PagesScreen } from "./PagesScreen";

type SocialTab = "feed" | "communities" | "pages" | "clubs" | "providers" | "crowdfunding";

const TABS: { id: SocialTab; label: string; icon: React.ComponentProps<typeof Ionicons>["name"] }[] = [
  { id: "feed", label: "Feed", icon: "newspaper-outline" },
  { id: "communities", label: "Communities", icon: "people-circle-outline" },
  { id: "pages", label: "Pages", icon: "bookmark-outline" },
  { id: "clubs", label: "Clubs", icon: "trophy-outline" },
  { id: "providers", label: "Providers", icon: "briefcase-outline" },
  { id: "crowdfunding", label: "Fundraising", icon: "heart-circle-outline" },
];

function TabBar({ tab, setTab }: { tab: SocialTab; setTab: (t: SocialTab) => void }) {
  return (
    <View style={styles.tabBarContainer}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabBar}>
        {TABS.map((t) => (
          <TouchableOpacity
            key={t.id}
            onPress={() => setTab(t.id)}
            style={[styles.tab, tab === t.id && styles.activeTab]}
          >
            <Ionicons
              name={t.icon}
              size={16}
              color={tab === t.id ? "#059669" : colors.gray[500]}
              style={styles.tabIcon}
            />
            <Text style={[styles.tabText, tab === t.id && styles.activeTabText]}>{t.label}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </View>
  );
}

export function SocialHubScreen() {
  const [tab, setTab] = useState<SocialTab>("feed");

  if (tab === "feed") {
    return (
      <View style={styles.container}>
        <TabBar tab={tab} setTab={setTab} />
        <View style={styles.content}>
          <SocialFeedScreen />
        </View>
      </View>
    );
  }

  return (
    <Screen>
      <Header title="Social" />
      <View style={styles.container}>
        <TabBar tab={tab} setTab={setTab} />
        <ScrollView style={styles.content} contentContainerStyle={styles.scrollContent}>
          {tab === "communities" && <CommunitiesScreen />}
          {tab === "pages" && <PagesScreen />}
          {tab === "clubs" && <ClubsScreen />}
          {tab === "providers" && <ProfessionalPagesScreen />}
          {tab === "crowdfunding" && <CrowdfundingScreen />}
        </ScrollView>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#F8FAFC" },
  tabBarContainer: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.gray[200],
    paddingHorizontal: 12,
    backgroundColor: "#FFFFFF",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 2,
    elevation: 2,
  },
  tabBar: { flexDirection: "row", gap: 4, paddingVertical: 8 },
  tab: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: colors.gray[100],
    gap: 5,
  },
  activeTab: { backgroundColor: colors.ui.success.light },
  tabIcon: { marginRight: 1 },
  tabText: { fontSize: 13, color: colors.gray[500], fontWeight: "500" },
  activeTabText: { color: "#059669", fontWeight: "600" },
  content: { flex: 1 },
  scrollContent: { padding: 16 },
});
