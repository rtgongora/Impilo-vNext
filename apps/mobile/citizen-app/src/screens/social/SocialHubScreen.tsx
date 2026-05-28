import React, { useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Header } from "@impilo/mobile-design-system";
import { SocialFeedScreen } from "./SocialFeedScreen";
import { ClubsScreen } from "./ClubsScreen";
import { ProfessionalPagesScreen } from "./ProfessionalPagesScreen";
import { CrowdfundingScreen } from "./CrowdfundingScreen";
import { CommunitiesScreen } from "./CommunitiesScreen";
import {
  APP_GREEN,
  APP_GREEN_LIGHT,
  APP_GREEN_XLIGHT,
  APP_SURFACE,
  APP_BG,
  APP_TEXT,
  APP_TEXT_2,
  APP_BORDER,
} from "../../lib/colors";

type SocialTab = "feed" | "communities" | "clubs" | "providers" | "crowdfunding";

const TABS: {
  id: SocialTab;
  label: string;
  icon: React.ComponentProps<typeof Ionicons>["name"];
}[] = [
  { id: "feed",         label: "Feed",        icon: "newspaper-outline" },
  { id: "communities",  label: "Communities", icon: "people-circle-outline" },
  { id: "clubs",        label: "Clubs",       icon: "trophy-outline" },
  { id: "providers",    label: "Providers",   icon: "briefcase-outline" },
  { id: "crowdfunding", label: "Fundraising", icon: "heart-circle-outline" },
];

const TAB_TITLE: Record<SocialTab, string> = {
  feed:         "Health Feed",
  communities:  "Communities",
  clubs:        "Wellness Clubs",
  providers:    "Providers",
  crowdfunding: "Fundraising",
};

function SocialTabBar({ tab, setTab }: { tab: SocialTab; setTab: (t: SocialTab) => void }) {
  return (
    <View style={styles.tabBarWrap}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.tabBar}
      >
        {TABS.map((t) => (
          <Pressable
            key={t.id}
            onPress={() => setTab(t.id)}
            style={({ pressed }) => [
              styles.tab,
              tab === t.id && styles.tabActive,
              pressed && { opacity: 0.8 },
            ]}
          >
            <Ionicons
              name={t.icon}
              size={15}
              color={tab === t.id ? APP_GREEN : APP_TEXT_2}
            />
            <Text style={[styles.tabText, tab === t.id && styles.tabTextActive]}>
              {t.label}
            </Text>
          </Pressable>
        ))}
      </ScrollView>
    </View>
  );
}

export function SocialHubScreen() {
  const [tab, setTab] = useState<SocialTab>("feed");

  return (
    <Screen>
      <Header title={TAB_TITLE[tab]} />
      <SocialTabBar tab={tab} setTab={setTab} />
      <View style={styles.content}>
        {tab === "feed" && <SocialFeedScreen />}
        {tab === "communities" && <CommunitiesScreen />}
        {tab === "clubs" && <ClubsScreen />}
        {tab === "providers" && <ProfessionalPagesScreen />}
        {tab === "crowdfunding" && <CrowdfundingScreen />}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  tabBarWrap: {
    backgroundColor: APP_SURFACE,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: APP_BORDER,
    paddingHorizontal: 12,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 3,
    elevation: 2,
  },
  tabBar: {
    flexDirection: "row",
    gap: 4,
    paddingVertical: 8,
  },
  tab: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: "#F3F4F6",
  },
  tabActive: {
    backgroundColor: APP_GREEN_XLIGHT,
    borderWidth: 1,
    borderColor: APP_GREEN_LIGHT,
  },
  tabText: { fontSize: 13, color: APP_TEXT_2, fontWeight: "500" },
  tabTextActive: { color: APP_GREEN, fontWeight: "700" },
  content: { flex: 1, backgroundColor: APP_BG },
});
