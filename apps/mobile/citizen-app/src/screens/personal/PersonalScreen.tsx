/**
 * PersonalScreen — Hub for all personal health features.
 *
 * Sections: Profile, Appointments, Prescriptions, Results, Records, Coverage, Settings.
 */

import React, { useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Screen, Header, TabBar, type TabItem } from "@impilo/mobile-design-system";
import { ProfileSection } from "./ProfileSection";
import { AppointmentsSection } from "./AppointmentsSection";
import { PrescriptionsSection } from "./PrescriptionsSection";
import { ResultsSection } from "./ResultsSection";
import { CoverageSection } from "./CoverageSection";
import { SettingsSection } from "./SettingsSection";
import { RecordsScreen } from "./RecordsScreen";
import { RemindersScreen } from "./RemindersScreen";
import { HealthTimelineScreen } from "./HealthTimelineScreen";
import { HealthIdSection } from "./HealthIdSection";
import { WellnessSection } from "./WellnessSection";
import { WalletSection } from "./WalletSection";
import { EmergencySOSSection } from "./EmergencySOSSection";
import { MonitoringSection } from "./MonitoringSection";
import { QueueStatusSection } from "./QueueStatusSection";
import { FinanceSection } from "./FinanceSection";
import { ChallengesScreen } from "./ChallengesScreen";
import { ProgramsScreen } from "./ProgramsScreen";

type PersonalTab = "profile" | "health-id" | "appointments" | "prescriptions" | "results" | "records" | "reminders" | "timeline" | "wellness" | "finance" | "challenges" | "programs" | "wallet" | "monitoring" | "queue" | "sos" | "coverage" | "settings";

const PERSONAL_TABS: TabItem[] = [
  { id: "profile", label: "Profile", icon: "user" },
  { id: "health-id", label: "Health ID", icon: "credit-card" },
  { id: "appointments", label: "Appointments", icon: "calendar" },
  { id: "prescriptions", label: "Rx", icon: "pill" },
  { id: "results", label: "Results", icon: "clipboard" },
  { id: "records", label: "Records", icon: "file-text" },
  { id: "timeline", label: "Timeline", icon: "clock" },
  { id: "wellness", label: "Wellness", icon: "activity" },
  { id: "finance", label: "Finance", icon: "dollar-sign" },
  { id: "challenges", label: "Challenges", icon: "trophy" },
  { id: "programs", label: "Programs", icon: "book-open" },
  { id: "wallet", label: "Wallet", icon: "credit-card" },
  { id: "monitoring", label: "Devices", icon: "bluetooth" },
  { id: "queue", label: "Queue", icon: "list" },
  { id: "sos", label: "SOS", icon: "alert-circle" },
  { id: "reminders", label: "Reminders", icon: "bell" },
  { id: "coverage", label: "Coverage", icon: "shield" },
  { id: "settings", label: "Settings", icon: "settings" },
];

const SECTIONS: Record<PersonalTab, React.FC> = {
  profile: ProfileSection,
  "health-id": HealthIdSection,
  appointments: AppointmentsSection,
  prescriptions: PrescriptionsSection,
  results: ResultsSection,
  records: RecordsScreen,
  reminders: RemindersScreen,
  timeline: HealthTimelineScreen,
  wellness: WellnessSection,
  finance: FinanceSection,
  challenges: ChallengesScreen,
  programs: ProgramsScreen,
  wallet: WalletSection,
  monitoring: MonitoringSection,
  queue: QueueStatusSection,
  sos: EmergencySOSSection,
  coverage: CoverageSection,
  settings: SettingsSection,
};

export function PersonalScreen() {
  const [activeSection, setActiveSection] = useState<PersonalTab>("profile");
  const SectionComponent = SECTIONS[activeSection];

  return (
    <Screen>
      <Header title="My Health" />
      <View testID="personal-screen" style={styles.container}>
        <ScrollView horizontal style={styles.tabScrollView} contentContainerStyle={styles.tabScrollContent}>
          <View style={styles.tabRow}>
            {PERSONAL_TABS.map((tab) => (
              <TouchableOpacity
                key={tab.id}
                onPress={() => setActiveSection(tab.id as PersonalTab)}
                testID={`personal-tab-${tab.id}`}
                style={[
                  styles.tabButton,
                  {
                    borderBottomColor: activeSection === tab.id ? "#2563EB" : "transparent",
                  },
                ]}
              >
                <Text
                  style={[
                    styles.tabLabel,
                    {
                      fontWeight: activeSection === tab.id ? "600" : "400",
                      color: activeSection === tab.id ? "#2563EB" : "#6B7280",
                    },
                  ]}
                >
                  {tab.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </ScrollView>
        <ScrollView style={styles.sectionContainer} contentContainerStyle={styles.sectionContent}>
          <SectionComponent />
        </ScrollView>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  tabScrollView: {
    borderBottomWidth: 1,
    borderBottomColor: "#E5E7EB",
    paddingHorizontal: 16,
  },
  tabScrollContent: {
    flexGrow: 1,
  },
  tabRow: {
    flexDirection: "row",
    gap: 4,
  },
  tabButton: {
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderBottomWidth: 2,
  },
  tabLabel: {
    fontSize: 13,
  },
  sectionContainer: {
    flex: 1,
  },
  sectionContent: {
    padding: 16,
  },
});
