/**
 * SocialHubScreen — Wraps social features with tab navigation.
 * Tabs: Feed, Communities, Clubs, Providers, Crowdfunding
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Screen, Header, type TabItem } from "@impilo/mobile-design-system";
import { SocialFeedScreen } from "./SocialFeedScreen";
import { ClubsScreen } from "./ClubsScreen";
import { ProfessionalPagesScreen } from "./ProfessionalPagesScreen";
import { CrowdfundingScreen } from "./CrowdfundingScreen";
import { CommunitiesScreen } from "./CommunitiesScreen";

type SocialTab = "feed" | "communities" | "clubs" | "providers" | "crowdfunding";

const TABS: { id: SocialTab; label: string }[] = [
  { id: "feed", label: "Feed" },
  { id: "communities", label: "Communities" },
  { id: "clubs", label: "Clubs" },
  { id: "providers", label: "Providers" },
  { id: "crowdfunding", label: "Fundraising" },
];

export function SocialHubScreen() {
  const [tab, setTab] = useState<SocialTab>("feed");

  // Feed screen manages its own Screen/Header, so render directly
  if (tab === "feed") {
    return (
      <View style={styles.container}>
        <View style={styles.tabBarContainer}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabBar}>
            {TABS.map((t) => (
              <TouchableOpacity key={t.id} onPress={() => setTab(t.id)} style={[styles.tab, tab === t.id && styles.activeTab]}>
                <Text style={[styles.tabText, tab === t.id && styles.activeTabText]}>{t.label}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>
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
        <View style={styles.tabBarContainer}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabBar}>
            {TABS.map((t) => (
              <TouchableOpacity key={t.id} onPress={() => setTab(t.id)} style={[styles.tab, tab === t.id && styles.activeTab]}>
                <Text style={[styles.tabText, tab === t.id && styles.activeTabText]}>{t.label}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>
        <ScrollView style={styles.content} contentContainerStyle={styles.scrollContent}>
          {tab === "communities" && <CommunitiesScreen />}
          {tab === "clubs" && <ClubsScreen />}
          {tab === "providers" && <ProfessionalPagesScreen />}
          {tab === "crowdfunding" && <CrowdfundingScreen />}
        </ScrollView>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  tabBarContainer: { borderBottomWidth: 1, borderBottomColor: "#E5E7EB", paddingHorizontal: 12, backgroundColor: "#FFFFFF" },
  tabBar: { flexDirection: "row", gap: 4, paddingVertical: 4 },
  tab: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 16, backgroundColor: "#F3F4F6" },
  activeTab: { backgroundColor: "#2563EB" },
  tabText: { fontSize: 13, color: "#6B7280", fontWeight: "500" },
  activeTabText: { color: "#FFFFFF" },
  content: { flex: 1 },
  scrollContent: { padding: 16 },
});
