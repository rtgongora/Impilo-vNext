import React, { useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Header } from "@impilo/mobile-design-system";
import { BecomeDonorScreen } from "./BecomeDonorScreen";
import { DonorProfileScreen } from "./DonorProfileScreen";
import { DonationDrivesScreen } from "./DonationDrivesScreen";
import { DonorHistoryScreen } from "./DonorHistoryScreen";
import { DonorFeedbackScreen } from "./DonorFeedbackScreen";
import { DonorScreeningScreen } from "./DonorScreeningScreen";

type MadiHubTab = "hub" | "register" | "profile" | "screening" | "drives" | "history" | "feedback";

const HUB_ACTIONS: Array<{ id: MadiHubTab; label: string; icon: React.ComponentProps<typeof Ionicons>["name"]; description: string }> = [
  { id: "register", label: "Become a Donor", icon: "heart", description: "Register your blood group and join the donor registry" },
  { id: "profile", label: "My Donor Profile", icon: "person-circle", description: "View status, preferences and eligibility" },
  { id: "screening", label: "Wellness Check", icon: "clipboard", description: "Guided pre-screening with Nompilo before you donate" },
  { id: "drives", label: "Donation Drives", icon: "location", description: "Find drives near you and register to donate" },
  { id: "history", label: "Donation History", icon: "time", description: "Past donations, screenings and deferrals" },
  { id: "feedback", label: "Give Feedback", icon: "chatbubble-ellipses", description: "Share your experience after a drive" },
];

export function MadiDonorHubScreen() {
  const [tab, setTab] = useState<MadiHubTab>("hub");

  if (tab === "register") {
    return <BecomeDonorScreen onBack={() => setTab("hub")} />;
  }
  if (tab === "profile") {
    return <DonorProfileScreen onBack={() => setTab("hub")} />;
  }
  if (tab === "screening") {
    return <DonorScreeningScreen onBack={() => setTab("hub")} />;
  }
  if (tab === "drives") {
    return <DonationDrivesScreen onBack={() => setTab("hub")} />;
  }
  if (tab === "history") {
    return <DonorHistoryScreen onBack={() => setTab("hub")} />;
  }
  if (tab === "feedback") {
    return <DonorFeedbackScreen onBack={() => setTab("hub")} />;
  }

  return (
    <Screen>
      <Header title="Blood Donor (MADI)" />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.intro}>
          MADI connects you to national blood donation drives, eligibility checks, and your donor record — governed by Impilo trust and consent.
        </Text>
        {HUB_ACTIONS.map((action) => (
          <TouchableOpacity
            key={action.id}
            style={styles.card}
            onPress={() => setTab(action.id)}
            testID={`madi-hub-${action.id}`}
            activeOpacity={0.85}
          >
            <View style={styles.cardIcon}>
              <Ionicons name={action.icon} size={22} color="#DC2626" />
            </View>
            <View style={styles.cardBody}>
              <Text style={styles.cardTitle}>{action.label}</Text>
              <Text style={styles.cardDesc}>{action.description}</Text>
            </View>
            <Ionicons name="chevron-forward" size={18} color="#9CA3AF" />
          </TouchableOpacity>
        ))}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#F9FAFB" },
  content: { padding: 16, gap: 12 },
  intro: { fontSize: 14, color: "#4B5563", lineHeight: 20, marginBottom: 4 },
  card: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: "#E5E7EB",
    gap: 12,
  },
  cardIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: "#FEE2E2",
    alignItems: "center",
    justifyContent: "center",
  },
  cardBody: { flex: 1, gap: 2 },
  cardTitle: { fontSize: 15, fontWeight: "600", color: "#111827" },
  cardDesc: { fontSize: 12, color: "#6B7280" },
});
