import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  ScrollView,
  Pressable,
  StyleSheet,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Screen, Header, Avatar } from "@impilo/mobile-design-system";
import { appStore, useAppStore } from "../../stores/appStore";

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
import { ConsentScreen } from "./ConsentScreen";
import { SupportScreen } from "../support/SupportScreen";

import {
  APP_GREEN,
  APP_GREEN_DEEP,
  APP_GREEN_XLIGHT,
  APP_RED,
  APP_RED_LIGHT,
  APP_SURFACE,
  APP_BG,
  APP_TEXT,
  APP_TEXT_3,
  APP_BORDER,
} from "../../lib/colors";

// ── Types ─────────────────────────────────────────────────────────────────────

type PersonalTab =
  | "profile" | "health-id" | "allergies" | "conditions" | "immunizations"
  | "referrals" | "care-plans" | "appointments" | "prescriptions" | "results"
  | "records" | "reminders" | "timeline" | "wellness" | "finance" | "challenges"
  | "programs" | "wallet" | "monitoring" | "queue" | "sos" | "coverage"
  | "settings" | "assessments" | "care-team" | "id-recovery" | "record-sharing"
  | "claim" | "verify" | "delegated-pickup" | "comms-prefs" | "privacy" | "terms"
  | "consent" | "support";

type Icon = React.ComponentProps<typeof Ionicons>["name"];

interface MenuItem { id: PersonalTab; label: string; icon: Icon }
interface MenuGroup { id: string; title: string; items: MenuItem[] }

// ── Menu structure ────────────────────────────────────────────────────────────
// Icons: all outline variants → consistent visual weight.
// Colors: handled in styles (single teal treatment for all items).
// No descriptions — labels are self-explanatory at this level.

const GROUPS: MenuGroup[] = [
  {
    id: "health",
    title: "My Health Records",
    items: [
      { id: "health-id",     label: "Health ID",       icon: "id-card-outline"            },
      { id: "records",       label: "Medical Records",  icon: "folder-outline"             },
      { id: "timeline",      label: "Health Timeline",  icon: "git-branch-outline"         },
      { id: "conditions",    label: "Conditions",       icon: "pulse-outline"              },
      { id: "allergies",     label: "Allergies",        icon: "alert-circle-outline"       },
      { id: "immunizations", label: "Immunizations",    icon: "shield-checkmark-outline"   },
      { id: "assessments",   label: "Assessments",      icon: "checkmark-done-outline"     },
    ],
  },
  {
    id: "care",
    title: "Appointments & Care",
    items: [
      { id: "appointments",  label: "Appointments",     icon: "calendar-outline"           },
      { id: "prescriptions", label: "Prescriptions",    icon: "medical-outline"            },
      { id: "results",       label: "Lab Results",      icon: "flask-outline"              },
      { id: "referrals",     label: "Referrals",        icon: "arrow-forward-circle-outline"},
      { id: "care-plans",    label: "Care Plans",       icon: "list-outline"               },
      { id: "care-team",     label: "Care Team",        icon: "people-outline"             },
      { id: "reminders",     label: "Reminders",        icon: "alarm-outline"              },
      { id: "monitoring",    label: "Monitoring",       icon: "watch-outline"              },
      { id: "queue",         label: "Queue Status",     icon: "time-outline"               },
    ],
  },
  {
    id: "wellness",
    title: "Wellness & Lifestyle",
    items: [
      { id: "wellness",      label: "Wellness Hub",     icon: "heart-outline"              },
      { id: "challenges",    label: "Challenges",       icon: "trophy-outline"             },
      { id: "programs",      label: "Programs",         icon: "ribbon-outline"             },
    ],
  },
  {
    id: "finance",
    title: "Finance & Coverage",
    items: [
      { id: "coverage",      label: "Insurance",        icon: "shield-outline"             },
      { id: "finance",       label: "Billing",          icon: "receipt-outline"            },
      { id: "wallet",        label: "Health Wallet",    icon: "wallet-outline"             },
    ],
  },
  {
    id: "identity",
    title: "Identity & Sharing",
    items: [
      { id: "record-sharing",  label: "Share Records",    icon: "share-outline"            },
      { id: "claim",           label: "Claim Documents",  icon: "download-outline"         },
      { id: "verify",          label: "Verify Credential",icon: "checkmark-circle-outline" },
      { id: "delegated-pickup",label: "Delegation",       icon: "people-outline"           },
      { id: "id-recovery",     label: "ID Recovery",      icon: "key-outline"              },
    ],
  },
  {
    id: "settings",
    title: "Settings & Support",
    items: [
      { id: "consent",     label: "Data Consent",       icon: "lock-open-outline"          },
      { id: "comms-prefs", label: "Notifications",      icon: "notifications-outline"      },
      { id: "support",     label: "Help & Support",     icon: "help-circle-outline"        },
      { id: "settings",    label: "Settings",           icon: "settings-outline"           },
      { id: "privacy",     label: "Privacy Policy",     icon: "document-text-outline"      },
      { id: "terms",       label: "Terms of Use",       icon: "document-outline"           },
    ],
  },
];

const SECTION_COMPONENTS: Record<PersonalTab, React.FC> = {
  profile: ProfileSection,
  "health-id": HealthIdSection,
  allergies: AllergiesSection,
  conditions: ConditionsSection,
  immunizations: ImmunizationsSection,
  referrals: ReferralsSection,
  "care-plans": CarePlansSection,
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
  consent: ConsentScreen,
  "comms-prefs": CommunicationPreferencesScreen,
  support: SupportScreen,
  settings: SettingsSection,
  assessments: AssessmentsSection,
  "care-team": CareTeamSection,
  "id-recovery": IdRecoverySection,
  "record-sharing": RecordSharingScreen,
  claim: ClaimSharedDocumentsScreen,
  verify: VerifyCredentialScreen,
  "delegated-pickup": DelegatedPickupScreen,
  privacy: PrivacyPolicyScreen,
  terms: TermsOfUseScreen,
};

// ── Section view ──────────────────────────────────────────────────────────────

function SectionView({ id, onBack }: { id: PersonalTab; onBack: () => void }) {
  const allItems = GROUPS.flatMap((g) => g.items);
  const meta = allItems.find((i) => i.id === id) ?? { label: id === "sos" ? "Emergency SOS" : "" };
  const SectionComponent = SECTION_COMPONENTS[id];
  return (
    <Screen>
      <Header title={meta.label} onBack={onBack} />
      <SectionComponent />
    </Screen>
  );
}

// ── Main ──────────────────────────────────────────────────────────────────────

export function PersonalScreen() {
  const insets = useSafeAreaInsets();
  const { profile, selectedFacilityName, pendingPersonalSection, setPendingPersonalSection } = useAppStore();

  const [activeSection, setActiveSection] = useState<PersonalTab | null>(() => {
    const pending = appStore.getState().pendingPersonalSection;
    return pending ? (pending as PersonalTab) : null;
  });

  useEffect(() => {
    if (appStore.getState().pendingPersonalSection) {
      appStore.getState().setPendingPersonalSection(null);
    }
  }, []);

  useEffect(() => {
    if (pendingPersonalSection) {
      setActiveSection(pendingPersonalSection as PersonalTab);
      setPendingPersonalSection(null);
    }
  }, [pendingPersonalSection, setPendingPersonalSection]);

  const facilityLabel = selectedFacilityName ?? profile?.facilityName;

  return (
    <View style={{ flex: 1 }}>
      {activeSection ? (
        <SectionView id={activeSection} onBack={() => setActiveSection(null)} />
      ) : null}

      <View style={[s.root, activeSection ? s.hidden : null]}>
        <ScrollView
          testID="personal-screen"
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{ paddingBottom: 52 }}
        >
          {/* ── HERO ─────────────────────────────────────────────────────────── */}
          <View style={[s.hero, { paddingTop: insets.top + 16 }]}>
            <View style={s.decoRing1} />
            <View style={s.decoRing2} />

            {/* Top bar — title + settings shortcut */}
            <View style={s.topBar}>
              <Text style={s.heroLabel}>My Health</Text>
              <Pressable
                onPress={() => setActiveSection("settings")}
                style={s.settingsBtn}
                hitSlop={10}
              >
                <Ionicons name="settings-outline" size={20} color="rgba(255,255,255,0.85)" />
              </Pressable>
            </View>

            {/* Profile */}
            <View style={s.profileRow}>
              <View style={s.avatarRing}>
                {profile ? (
                  <Avatar name={`${profile.givenName} ${profile.familyName}`} size="lg" />
                ) : (
                  <View style={s.avatarPlaceholder}>
                    <Ionicons name="person" size={28} color="rgba(255,255,255,0.45)" />
                  </View>
                )}
              </View>
              <View style={s.profileInfo}>
                <Text style={s.profileName} numberOfLines={1}>
                  {profile ? `${profile.givenName} ${profile.familyName}` : "My Profile"}
                </Text>
                {facilityLabel ? (
                  <View style={s.facilityRow}>
                    <Ionicons name="location-outline" size={12} color="rgba(255,255,255,0.65)" />
                    <Text style={s.facilityText} numberOfLines={1}>{facilityLabel}</Text>
                  </View>
                ) : null}
              </View>
            </View>

            {/* CTA buttons — text only, no icons (labels are clear enough) */}
            <View style={s.heroBtns}>
              <Pressable
                onPress={() => setActiveSection("profile")}
                style={({ pressed }) => [s.heroBtn, pressed && { opacity: 0.82 }]}
              >
                <Text style={s.heroBtnText}>Edit Profile</Text>
              </Pressable>
              <Pressable
                onPress={() => setActiveSection("health-id")}
                style={({ pressed }) => [s.heroBtn, s.heroBtnPrimary, pressed && { opacity: 0.82 }]}
              >
                <Text style={[s.heroBtnText, { color: APP_GREEN }]}>Show Health ID</Text>
              </Pressable>
            </View>
          </View>

          {/* ── MENU ─────────────────────────────────────────────────────────── */}
          <View style={s.body}>
            {GROUPS.map((group) => (
              <View key={group.id} style={s.group}>
                {/* Group title — text only, no icon */}
                <Text style={s.groupTitle}>{group.title.toUpperCase()}</Text>

                <View style={s.groupCard}>
                  {group.items.map((item, idx) => (
                    <View key={item.id}>
                      <Pressable
                        testID={`personal-tab-${item.id}`}
                        onPress={() => setActiveSection(item.id)}
                        style={({ pressed }) => [s.menuRow, pressed && s.menuRowPressed]}
                      >
                        {/* Consistent icon treatment — single teal colour, single bg */}
                        <View style={s.menuIcon}>
                          <Ionicons name={item.icon} size={18} color={APP_GREEN} />
                        </View>
                        <Text style={s.menuLabel}>{item.label}</Text>
                        <Ionicons name="chevron-forward" size={14} color={APP_TEXT_3} />
                      </Pressable>
                      {idx < group.items.length - 1 ? <View style={s.divider} /> : null}
                    </View>
                  ))}
                </View>
              </View>
            ))}

            {/* ── Emergency SOS — standalone, outside group system ─────────── */}
            <Pressable
              testID="personal-tab-sos"
              onPress={() => setActiveSection("sos")}
              style={({ pressed }) => [s.sosCard, pressed && { opacity: 0.88 }]}
            >
              <View style={s.sosIconWrap}>
                <Ionicons name="call" size={20} color="#FFFFFF" />
              </View>
              <View style={s.sosBody}>
                <View style={s.sosTitleRow}>
                  <Text style={s.sosTitle}>Emergency SOS</Text>
                  <View style={s.sosDot} />
                </View>
                <Text style={s.sosSub}>Hold to activate · emergency contacts</Text>
              </View>
              <Ionicons name="chevron-forward" size={15} color="rgba(255,255,255,0.6)" />
            </Pressable>
          </View>
        </ScrollView>
      </View>
    </View>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  hidden: { display: "none" },

  /* Hero */
  hero: {
    backgroundColor: APP_GREEN,
    paddingHorizontal: 20,
    paddingBottom: 22,
    borderBottomLeftRadius: 28,
    borderBottomRightRadius: 28,
    overflow: "hidden",
    shadowColor: APP_GREEN_DEEP,
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.3,
    shadowRadius: 16,
    elevation: 10,
  },
  decoRing1: {
    position: "absolute", width: 200, height: 200, borderRadius: 100,
    borderWidth: 30, borderColor: "rgba(255,255,255,0.07)", top: -70, right: -60,
  },
  decoRing2: {
    position: "absolute", width: 120, height: 120, borderRadius: 60,
    borderWidth: 20, borderColor: "rgba(255,255,255,0.05)", top: -10, right: 40,
  },

  topBar: {
    flexDirection: "row", justifyContent: "space-between",
    alignItems: "center", marginBottom: 20,
  },
  heroLabel: { fontSize: 17, fontWeight: "800", color: "#FFFFFF", letterSpacing: 0.2 },
  settingsBtn: { padding: 4 },

  profileRow: { flexDirection: "row", alignItems: "center", gap: 14, marginBottom: 18 },
  avatarRing: {
    padding: 3, borderRadius: 34,
    borderWidth: 2, borderColor: "rgba(255,255,255,0.3)",
  },
  avatarPlaceholder: {
    width: 56, height: 56, borderRadius: 28,
    backgroundColor: "rgba(255,255,255,0.12)",
    alignItems: "center", justifyContent: "center",
  },
  profileInfo: { flex: 1 },
  profileName: { fontSize: 19, fontWeight: "800", color: "#FFFFFF", marginBottom: 5 },
  facilityRow: { flexDirection: "row", alignItems: "center", gap: 4 },
  facilityText: { fontSize: 12, color: "rgba(255,255,255,0.7)", flex: 1 },

  heroBtns: { flexDirection: "row", gap: 10 },
  heroBtn: {
    flex: 1, alignItems: "center", justifyContent: "center",
    paddingVertical: 11, borderRadius: 13,
    backgroundColor: "rgba(255,255,255,0.12)",
    borderWidth: 1, borderColor: "rgba(255,255,255,0.18)",
  },
  heroBtnPrimary: {
    backgroundColor: "rgba(255,255,255,0.92)",
    borderColor: "transparent",
  },
  heroBtnText: { fontSize: 13, fontWeight: "700", color: "#FFFFFF" },

  /* Menu */
  body: { paddingHorizontal: 16, paddingTop: 20, gap: 20, paddingBottom: 8 },

  group: { gap: 7 },

  /* Group title — no icon, purely typographic */
  groupTitle: {
    fontSize: 11,
    fontWeight: "700",
    color: APP_TEXT_3,
    letterSpacing: 1.1,
    paddingHorizontal: 4,
  },

  groupCard: {
    backgroundColor: APP_SURFACE,
    borderRadius: 16,
    overflow: "hidden",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 6,
    elevation: 2,
  },

  menuRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  menuRowPressed: { backgroundColor: APP_GREEN_XLIGHT },

  /* Single consistent icon treatment — one colour, one background */
  menuIcon: {
    width: 36,
    height: 36,
    borderRadius: 10,
    backgroundColor: APP_GREEN_XLIGHT,
    alignItems: "center",
    justifyContent: "center",
  },

  menuLabel: { flex: 1, fontSize: 15, fontWeight: "500", color: APP_TEXT },

  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: APP_BORDER,
    marginLeft: 66, // aligns with text (icon 36 + gap 14 + padding 16)
  },

  /* Emergency SOS — standalone full-width card, solid red */
  sosCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    backgroundColor: APP_RED,
    borderRadius: 16,
    padding: 16,
    shadowColor: APP_RED,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 12,
    elevation: 6,
  },
  sosIconWrap: {
    width: 40,
    height: 40,
    borderRadius: 12,
    backgroundColor: "rgba(255,255,255,0.18)",
    alignItems: "center",
    justifyContent: "center",
  },
  sosBody: { flex: 1 },
  sosTitleRow: { flexDirection: "row", alignItems: "center", gap: 7, marginBottom: 2 },
  sosTitle: { fontSize: 15, fontWeight: "700", color: "#FFFFFF" },
  sosDot: {
    width: 6, height: 6, borderRadius: 3,
    backgroundColor: "rgba(255,255,255,0.65)",
  },
  sosSub: { fontSize: 12, color: "rgba(255,255,255,0.72)" },
});
