import React, { useEffect, useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Header } from "@impilo/mobile-design-system";
import { WalletOverviewSection } from "./WalletOverviewSection";
import { MarketplaceStoreSection } from "./MarketplaceStoreSection";
import { ProfileSection } from "./ProfileSection";
import { AppointmentsSection } from "./AppointmentsSection";
import { BookingsSection } from "./BookingsSection";
import { PrescriptionsSection } from "./PrescriptionsSection";
import { ResultsSection } from "./ResultsSection";
import { CoverageSection } from "./CoverageSection";
import { SettingsSection } from "./SettingsSection";
import { RecordsScreen } from "./RecordsScreen";
import { RemindersScreen } from "./RemindersScreen";
import { HealthTimelineScreen } from "./HealthTimelineScreen";
import { HealthIdSection } from "./HealthIdSection";
import { WellnessSection } from "./WellnessSection";
import { WellnessJourneysSection } from "./WellnessJourneysSection";
import { WalletSection } from "./WalletSection";
import { EmergencySOSSection } from "./EmergencySOSSection";
import { MonitoringSection } from "./MonitoringSection";
import { QueueStatusSection } from "./QueueStatusSection";
import { FinanceSection } from "./FinanceSection";
import { ChallengesScreen } from "./ChallengesScreen";
import { ProgramsScreen } from "./ProgramsScreen";
import { AllergiesSection } from "./AllergiesSection";
import { ConditionsSection } from "./ConditionsSection";
import { ImmunizationsSection } from "./ImmunizationsSection";
import { ReferralsSection } from "./ReferralsSection";
import { CarePlansSection } from "./CarePlansSection";
import { IdRecoverySection } from "./IdRecoverySection";
import { AssessmentsSection } from "./AssessmentsSection";
import { CareTeamSection } from "./CareTeamSection";
import { RecordSharingScreen } from "./RecordSharingScreen";
import { ClaimSharedDocumentsScreen } from "./ClaimSharedDocumentsScreen";
import { VerifyCredentialScreen } from "./VerifyCredentialScreen";
import { DelegatedPickupScreen } from "./DelegatedPickupScreen";
import { PrivacyPolicyScreen } from "./PrivacyPolicyScreen";
import { TermsOfUseScreen } from "./TermsOfUseScreen";
import { CommunicationPreferencesScreen } from "./CommunicationPreferencesScreen";
import { PatientConsentScreen } from "./PatientConsentScreen";
import { SupportScreen } from "../support/SupportScreen";
import { FeedbackScreen } from "../rito/FeedbackScreen";
import { TrackFeedbackScreen } from "../rito/TrackFeedbackScreen";
import { NhumeTrackingScreen } from "../NhumeTrackingScreen";
import { ProductionReadinessJourneyScreen } from "./ProductionReadinessJourneyScreen";
import { ProviderDiscoveryScreen } from "../discover/ProviderDiscoveryScreen";
import { MadiDonorHubScreen } from "../madi/MadiDonorHubScreen";
import { LiveDiscoverScreen } from "../live/LiveDiscoverScreen";
import { appStore, useAppStore } from "../../stores/appStore";

type PersonalTab =
  | "wallet-home"
  | "profile"
  | "health-id"
  | "allergies"
  | "conditions"
  | "immunizations"
  | "referrals"
  | "discover-providers"
  | "care-plans"
  | "bookings"
  | "appointments"
  | "prescriptions"
  | "results"
  | "records"
  | "reminders"
  | "timeline"
  | "wellness"
  | "journeys"
  | "finance"
  | "challenges"
  | "programs"
  | "wallet"
  | "monitoring"
  | "queue"
  | "sos"
  | "coverage"
  | "settings"
  | "assessments"
  | "care-team"
  | "id-recovery"
  | "record-sharing"
  | "claim"
  | "verify"
  | "delegated-pickup"
  | "nhume-track"
  | "comms-prefs"
  | "privacy"
  | "terms"
  | "consent"
  | "support"
  | "rito-feedback"
  | "rito-track"
  | "prod-ready"
  | "madi-donor"
  | "marketplace-store"
  | "impilo-live";

type IoniconsName = React.ComponentProps<typeof Ionicons>["name"];

const PERSONAL_TABS: Array<{ id: PersonalTab; label: string; icon: IoniconsName }> = [
  { id: "wallet-home", label: "Wallet", icon: "wallet-outline" },
  { id: "profile", label: "Profile", icon: "person" },
  { id: "health-id", label: "Health ID", icon: "card" },
  { id: "allergies", label: "Allergies", icon: "medical" },
  { id: "conditions", label: "Conditions", icon: "medical-outline" },
  { id: "immunizations", label: "Immunizations", icon: "shield-checkmark" },
  { id: "referrals", label: "Referrals", icon: "people" },
  { id: "discover-providers", label: "Find Provider", icon: "search" },
  { id: "care-plans", label: "Care Plans", icon: "clipboard" },
  { id: "bookings", label: "My Bookings", icon: "document-text" },
  { id: "appointments", label: "Appointments", icon: "calendar" },
  { id: "prescriptions", label: "Prescriptions", icon: "receipt" },
  { id: "results", label: "Results", icon: "flask" },
  { id: "records", label: "Records", icon: "folder" },
  { id: "reminders", label: "Reminders", icon: "alarm" },
  { id: "timeline", label: "Timeline", icon: "time" },
  { id: "wellness", label: "Wellness", icon: "fitness" },
  { id: "journeys", label: "Journeys", icon: "trail-sign" },
  { id: "madi-donor", label: "Blood Donor", icon: "heart" },
  { id: "impilo-live", label: "Impilo Live", icon: "radio" },
  { id: "prod-ready", label: "Prod Ready", icon: "rocket" },
  { id: "finance", label: "Finance", icon: "cash" },
  { id: "challenges", label: "Challenges", icon: "trophy" },
  { id: "programs", label: "Programs", icon: "ribbon" },
  { id: "wallet", label: "Wallet", icon: "wallet" },
  { id: "monitoring", label: "Monitoring", icon: "pulse" },
  { id: "queue", label: "Queue", icon: "location" },
  { id: "sos", label: "Emergency", icon: "warning" },
  { id: "coverage", label: "Coverage", icon: "shield" },
  { id: "consent", label: "Consent", icon: "book-outline" },
  { id: "comms-prefs", label: "Comms", icon: "chatbubbles-outline" },
  { id: "support", label: "Help", icon: "help-circle" },
  { id: "rito-feedback", label: "Feedback", icon: "chatbox-ellipses" },
  { id: "rito-track", label: "Track Feedback", icon: "search-circle" },
  { id: "settings", label: "Settings", icon: "settings" },
  { id: "assessments", label: "Assessments", icon: "duplicate" },
  { id: "care-team", label: "Care Team", icon: "people" },
  { id: "id-recovery", label: "ID Recovery", icon: "key" },
  { id: "record-sharing", label: "Share", icon: "share-social" },
  { id: "claim", label: "Claim", icon: "download" },
  { id: "verify", label: "Verify", icon: "shield-checkmark" },
  { id: "delegated-pickup", label: "Pickup", icon: "car" },
  { id: "nhume-track", label: "Track Delivery", icon: "navigate" },
  { id: "marketplace-store", label: "Marketplace", icon: "storefront" },
  { id: "privacy", label: "Privacy", icon: "lock-closed" },
  { id: "terms", label: "Terms", icon: "document-text" },
];

const SECTIONS: Partial<Record<PersonalTab, React.FC>> = {
  "wallet-home": WalletOverviewSection,
  "marketplace-store": MarketplaceStoreSection,
  profile: ProfileSection,
  "health-id": HealthIdSection,
  allergies: AllergiesSection,
  conditions: ConditionsSection,
  immunizations: ImmunizationsSection,
  referrals: ReferralsSection,
  "discover-providers": ProviderDiscoveryScreen,
  "care-plans": CarePlansSection,
  bookings: BookingsSection,
  appointments: AppointmentsSection,
  prescriptions: PrescriptionsSection,
  results: ResultsSection,
  records: RecordsScreen,
  reminders: RemindersScreen,
  timeline: HealthTimelineScreen,
  wellness: WellnessSection,
  journeys: WellnessJourneysSection,
  "madi-donor": MadiDonorHubScreen,
  "impilo-live": LiveDiscoverScreen,
  finance: FinanceSection,
  challenges: ChallengesScreen,
  programs: ProgramsScreen,
  wallet: WalletSection,
  monitoring: MonitoringSection,
  queue: QueueStatusSection,
  sos: EmergencySOSSection,
  coverage: CoverageSection,
  consent: PatientConsentScreen,
  "comms-prefs": CommunicationPreferencesScreen,
  support: SupportScreen,
  "rito-feedback": FeedbackScreen,
  "rito-track": TrackFeedbackScreen,
  settings: SettingsSection,
  assessments: AssessmentsSection,
  "care-team": CareTeamSection,
  "id-recovery": IdRecoverySection,
  "record-sharing": RecordSharingScreen,
  claim: ClaimSharedDocumentsScreen,
  verify: VerifyCredentialScreen,
  "delegated-pickup": DelegatedPickupScreen,
  "nhume-track": NhumeTrackingScreen,
  privacy: PrivacyPolicyScreen,
  terms: TermsOfUseScreen,
};

export function PersonalScreen() {
  const { personalSectionRequest } = useAppStore();
  const [activeSection, setActiveSection] = useState<PersonalTab>("wallet-home");
  const SectionComponent = SECTIONS[activeSection];

  useEffect(() => {
    if (personalSectionRequest) {
      setActiveSection(personalSectionRequest as PersonalTab);
      appStore.getState().setPersonalSectionRequest(null);
    }
  }, [personalSectionRequest]);

  const sectionNavMap: Record<string, PersonalTab> = {
    wellness: "wellness",
    social: "timeline",
    discover: "discover-providers",
    care: "appointments",
    medications: "prescriptions",
    prescriptions: "prescriptions",
    appointments: "appointments",
    "discover-providers": "discover-providers",
    "nhume-track": "nhume-track",
    monitoring: "monitoring",
    records: "records",
  };

  return (
    <Screen>
      <Header title="My Health" />
      <View testID="personal-screen" style={styles.container}>
        <View style={styles.tabBarWrapper}>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            style={styles.tabScrollView}
            contentContainerStyle={styles.tabScrollContent}
          >
            {PERSONAL_TABS.map((tab) => {
              const isActive = activeSection === tab.id;
              return (
                <TouchableOpacity
                  key={tab.id}
                  onPress={() => setActiveSection(tab.id as PersonalTab)}
                  testID={`personal-tab-${tab.id}`}
                  activeOpacity={0.85}
                  style={[
                    styles.tabPill,
                    isActive ? styles.tabPillActive : styles.tabPillInactive,
                  ]}
                >
                  <Ionicons
                    name={tab.icon}
                    size={14}
                    color={isActive ? "#059669" : "#9CA3AF"}
                  />
                  <Text
                    style={[
                      styles.tabLabel,
                      isActive ? styles.tabLabelActive : styles.tabLabelInactive,
                    ]}
                  >
                    {tab.label}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        </View>
        <ScrollView style={styles.sectionContainer} contentContainerStyle={styles.sectionContent}>
          {activeSection === "prod-ready" ? (
            <ProductionReadinessJourneyScreen
              onNavigateSection={(section) => {
                const target = sectionNavMap[section];
                if (target) setActiveSection(target);
              }}
            />
          ) : SectionComponent ? (
            <SectionComponent />
          ) : null}
        </ScrollView>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#F9FAFB",
  },
  tabBarWrapper: {
    backgroundColor: "#FFFFFF",
    borderBottomWidth: 1,
    borderBottomColor: "#E5E7EB",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 2,
    elevation: 2,
  },
  tabScrollView: {
    paddingVertical: 10,
  },
  tabScrollContent: {
    paddingHorizontal: 16,
    gap: 8,
    flexDirection: "row",
    alignItems: "center",
  },
  tabPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 20,
  },
  tabPillActive: {
    backgroundColor: "#D1FAE5",
  },
  tabPillInactive: {
    backgroundColor: "#F3F4F6",
  },
  tabLabel: {
    fontSize: 12,
  },
  tabLabelActive: {
    fontWeight: "700",
    color: "#059669",
  },
  tabLabelInactive: {
    fontWeight: "400",
    color: "#6B7280",
  },
  sectionContainer: {
    flex: 1,
  },
  sectionContent: {
    padding: 16,
    paddingBottom: 32,
  },
});
