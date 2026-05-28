import React, { useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  APP_GREEN, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

interface Section { title: string; content: string }

const SECTIONS: Section[] = [
  {
    title: "1. Introduction",
    content: "The Impilo Health Operating System ('Impilo', 'we', 'us') is operated by the Ministry of Health and Child Care (MoHCC) of Zimbabwe. This Privacy Policy explains how we collect, use, store, share, and protect your personal health information when you use the Impilo Citizen App.\n\nWe are committed to protecting your privacy in accordance with the Zimbabwe Cyber and Data Protection Act (2021) and applicable health data governance standards.",
  },
  {
    title: "2. Data We Collect",
    content: "We collect the following categories of personal information:\n\n• Identity data: full name, date of birth, sex, National ID number, Health ID (CPID)\n• Contact data: phone number, email address, physical address\n• Health data: medical records, diagnoses, prescriptions, immunizations, lab results, vitals\n• Usage data: app activity, feature interactions, device information\n• Location data: facility selections and queue participation (no continuous tracking)\n\nWe collect only what is necessary to provide health services.",
  },
  {
    title: "3. How We Use Your Data",
    content: "Your data is used to:\n\n• Provide and improve health services across the Impilo network\n• Enable your care team to coordinate your care\n• Send appointment reminders and medication alerts\n• Support emergency access to critical health information\n• Generate anonymised insights for national health planning (with your consent)\n• Comply with legal and regulatory obligations\n\nWe do not sell your personal health data. We do not use it for commercial advertising.",
  },
  {
    title: "4. Data Sharing",
    content: "Your data may be shared with:\n\n• Healthcare providers: your treating clinicians and care team within the Impilo network\n• Authorised facilities: hospitals, clinics, pharmacies where you receive care\n• Government health agencies: MoHCC for public health surveillance (anonymised)\n• Emergency services: critical health data in life-threatening situations (if consented)\n• Technology partners: only those processing data on our behalf under strict agreements\n\nYou can manage sharing consents in the Data Consent section of the app.",
  },
  {
    title: "5. Data Storage & Security",
    content: "Your data is stored on secure government-approved cloud infrastructure within Zimbabwe. We apply:\n\n• End-to-end encryption for data in transit\n• AES-256 encryption for data at rest\n• Role-based access controls\n• Audit logging of all data access\n• Regular security assessments and penetration testing\n\nOur systems are certified against the Zimbabwe National Health Information Security Policy.",
  },
  {
    title: "6. Your Rights",
    content: "Under the Zimbabwe Cyber and Data Protection Act (2021) you have the right to:\n\n• Access: request a copy of your personal data\n• Correction: correct inaccurate or incomplete data\n• Deletion: request deletion of data (subject to legal retention requirements)\n• Portability: receive your data in a machine-readable format\n• Restriction: restrict processing of your data\n• Objection: object to processing for certain purposes\n\nTo exercise these rights, contact privacy@impilo.gov.zw or visit any registered facility.",
  },
  {
    title: "7. Retention",
    content: "Medical records are retained for a minimum of 25 years from the date of last clinical contact in accordance with the Zimbabwe Health Records Act. Usage logs are retained for 12 months. You may request deletion of non-clinical data at any time.",
  },
  {
    title: "8. Contact",
    content: "Data Controller: Ministry of Health and Child Care, Zimbabwe\nPrivacy Officer: privacy@impilo.gov.zw\nAddress: Kaguvi Building, 4th Street, Harare, Zimbabwe\nHelpline: 0800 IMPILO (467456)\n\nTo lodge a complaint, contact the Postal and Telecommunications Regulatory Authority of Zimbabwe (POTRAZ) Data Protection Office.",
  },
];

export function PrivacyPolicyScreen() {
  const [expanded, setExpanded] = useState<string | null>(null);

  return (
    <ScrollView style={s.root} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>
      {/* Header */}
      <View style={s.header}>
        <View style={s.headerIcon}>
          <Ionicons name="lock-closed" size={28} color={APP_GREEN} />
        </View>
        <Text style={s.headerTitle}>Privacy Policy</Text>
        <Text style={s.headerMeta}>Ministry of Health & Child Care, Zimbabwe</Text>
        <View style={s.headerDate}>
          <Ionicons name="calendar-outline" size={12} color={APP_TEXT_3} />
          <Text style={s.headerDateText}>Effective 1 January 2025 · Version 2.1</Text>
        </View>
      </View>

      {/* Summary card */}
      <View style={s.summaryCard}>
        <Text style={s.summaryTitle}>In plain terms</Text>
        <Text style={s.summaryText}>
          Your health data belongs to you. We use it only to provide your healthcare and improve national health services. We never sell it. You can control how it is shared at any time through the Data Consent screen.
        </Text>
      </View>

      {/* Sections */}
      {SECTIONS.map((sec) => {
        const isOpen = expanded === sec.title;
        return (
          <Pressable
            key={sec.title}
            onPress={() => setExpanded(isOpen ? null : sec.title)}
            style={s.sectionCard}
          >
            <View style={s.sectionHeader}>
              <Text style={s.sectionTitle}>{sec.title}</Text>
              <Ionicons name={isOpen ? "chevron-up" : "chevron-down"} size={15} color={APP_TEXT_3} />
            </View>
            {isOpen ? (
              <Text style={s.sectionBody}>{sec.content}</Text>
            ) : null}
          </Pressable>
        );
      })}

      <View style={s.footer}>
        <Ionicons name="shield-checkmark-outline" size={14} color={APP_TEXT_3} />
        <Text style={s.footerText}>
          Protected under the Zimbabwe Cyber and Data Protection Act (2021)
        </Text>
      </View>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 12, paddingBottom: 40 },
  header: { alignItems: "center", paddingVertical: 16, gap: 8 },
  headerIcon: { width: 64, height: 64, borderRadius: 20, backgroundColor: APP_GREEN_LIGHT, alignItems: "center", justifyContent: "center" },
  headerTitle: { fontSize: 22, fontWeight: "800", color: APP_TEXT },
  headerMeta: { fontSize: 13, color: APP_TEXT_2, textAlign: "center" },
  headerDate: { flexDirection: "row", alignItems: "center", gap: 5 },
  headerDateText: { fontSize: 11, color: APP_TEXT_3 },
  summaryCard: {
    backgroundColor: APP_GREEN_XLIGHT, borderRadius: 16, padding: 16, gap: 8,
    borderLeftWidth: 4, borderLeftColor: APP_GREEN,
  },
  summaryTitle: { fontSize: 14, fontWeight: "700", color: APP_GREEN },
  summaryText: { fontSize: 13, color: APP_TEXT_2, lineHeight: 19 },
  sectionCard: {
    backgroundColor: APP_SURFACE, borderRadius: 14, overflow: "hidden",
    shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.04, shadowRadius: 4, elevation: 2,
  },
  sectionHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", padding: 14 },
  sectionTitle: { fontSize: 14, fontWeight: "700", color: APP_TEXT, flex: 1, marginRight: 8 },
  sectionBody: {
    fontSize: 13, color: APP_TEXT_2, lineHeight: 20,
    paddingHorizontal: 14, paddingBottom: 14,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  footer: { flexDirection: "row", alignItems: "flex-start", gap: 6, paddingHorizontal: 4, paddingTop: 4 },
  footerText: { fontSize: 11, color: APP_TEXT_3, flex: 1, lineHeight: 15 },
});
