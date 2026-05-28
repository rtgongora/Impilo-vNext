import React, { useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  APP_GREEN, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const SECTIONS = [
  {
    title: "1. Acceptance of Terms",
    content: "By downloading, installing, or using the Impilo Citizen App ('App'), you agree to be bound by these Terms of Use. If you do not agree, you must not use the App.\n\nThese Terms constitute a binding agreement between you and the Ministry of Health and Child Care (MoHCC) of Zimbabwe ('Ministry', 'we', 'us').",
  },
  {
    title: "2. Eligibility",
    content: "To use the App you must:\n\n• Be a resident of Zimbabwe with a valid Health ID (or in the process of obtaining one)\n• Be at least 16 years of age, or have parental/guardian consent\n• Provide accurate, complete, and current information during registration\n• Comply with all applicable laws of Zimbabwe",
  },
  {
    title: "3. Permitted Use",
    content: "The App is provided for the purpose of accessing and managing your personal health services within the Impilo network. You may use the App to:\n\n• View your health records, prescriptions, and results\n• Book appointments and manage your care\n• Communicate with your healthcare providers\n• Access telehealth services\n• Participate in wellness programmes\n\nAny commercial use of the App or its data is strictly prohibited.",
  },
  {
    title: "4. Prohibited Activities",
    content: "You must not:\n\n• Attempt to access another person's health records without authorisation\n• Misrepresent your identity or health information\n• Use the App to distribute malware or conduct cyberattacks\n• Scrape, harvest, or extract data beyond your own records\n• Interfere with the security or integrity of the Impilo network\n• Use the App for any unlawful purpose under Zimbabwe law\n\nViolations may result in account suspension and referral to law enforcement.",
  },
  {
    title: "5. Medical Disclaimer",
    content: "The App is a tool to access and manage health information — it is NOT a substitute for professional medical advice, diagnosis, or treatment.\n\nAlways consult a qualified healthcare professional before making any medical decision. In an emergency, call 999 or proceed directly to your nearest emergency department.\n\nThe Ministry does not accept liability for clinical decisions made solely on the basis of information displayed in the App.",
  },
  {
    title: "6. Data Accuracy",
    content: "While we strive to maintain accurate and up-to-date records, health information in the App reflects data provided by healthcare facilities and professionals. You are responsible for notifying your care team of any inaccuracies.\n\nThe Ministry does not guarantee the completeness or accuracy of information synced from third-party facilities.",
  },
  {
    title: "7. Account Security",
    content: "You are responsible for:\n\n• Keeping your login credentials confidential\n• Logging out of shared or public devices\n• Reporting suspected unauthorised access immediately to support@impilo.gov.zw or 0800 IMPILO\n\nThe Ministry will never ask for your password via phone, email, or SMS.",
  },
  {
    title: "8. Intellectual Property",
    content: "All content, design, code, and trademarks in the App are the property of the Ministry of Health and Child Care, Government of Zimbabwe. You may not reproduce, modify, distribute, or create derivative works without written permission.",
  },
  {
    title: "9. Availability",
    content: "We aim to maintain 99.5% uptime but do not guarantee uninterrupted service. Scheduled maintenance windows will be communicated in advance. The Ministry shall not be liable for service interruptions beyond its reasonable control.",
  },
  {
    title: "10. Governing Law",
    content: "These Terms are governed by the laws of Zimbabwe. Any disputes shall be subject to the exclusive jurisdiction of the courts of Zimbabwe.\n\nFor formal complaints, contact:\nMinistry of Health and Child Care\nKaguvi Building, 4th Street, Harare\nEmail: legal@impilo.gov.zw",
  },
];

export function TermsOfUseScreen() {
  const [expanded, setExpanded] = useState<string | null>(null);

  return (
    <ScrollView style={s.root} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>
      {/* Header */}
      <View style={s.header}>
        <View style={s.headerIcon}>
          <Ionicons name="document-text" size={28} color={APP_GREEN} />
        </View>
        <Text style={s.headerTitle}>Terms of Use</Text>
        <Text style={s.headerMeta}>Ministry of Health & Child Care, Zimbabwe</Text>
        <View style={s.headerDate}>
          <Ionicons name="calendar-outline" size={12} color={APP_TEXT_3} />
          <Text style={s.headerDateText}>Effective 1 January 2025 · Version 2.0</Text>
        </View>
      </View>

      {/* Warning card */}
      <View style={s.warningCard}>
        <Ionicons name="warning-outline" size={16} color="#D97706" />
        <Text style={s.warningText}>
          This App is not a medical emergency service. In an emergency, call 999 or go to your nearest A&E immediately.
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
        <Ionicons name="checkmark-done-circle-outline" size={14} color={APP_TEXT_3} />
        <Text style={s.footerText}>
          By using this app, you confirm you have read and agree to these Terms of Use.
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
  warningCard: {
    flexDirection: "row", alignItems: "flex-start", gap: 10,
    backgroundColor: "#FFFBEB", borderRadius: 14, padding: 14,
    borderLeftWidth: 3, borderLeftColor: "#D97706",
  },
  warningText: { fontSize: 13, color: "#92400E", flex: 1, lineHeight: 19 },
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
