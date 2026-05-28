import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Card, CardBody, FeatureMaturityBadge, colors } from "@impilo/mobile-design-system";

interface DemoJourney {
  id: string;
  number: number;
  title: string;
  description: string;
  citizenSection: string;
  maturity: "live" | "partial";
}

const CITIZEN_DEMO_JOURNEYS: DemoJourney[] = [
  {
    id: "clinical-rx",
    number: 1,
    title: "Core clinical / Rx",
    description: "Prescriptions, pickup, and payment handoff for golden patient CPID-ZW-00001.",
    citizenSection: "prescriptions",
    maturity: "partial",
  },
  {
    id: "inpatient",
    number: 2,
    title: "Inpatient",
    description: "Appointments and care coordination while admitted.",
    citizenSection: "appointments",
    maturity: "partial",
  },
  {
    id: "wellness",
    number: 3,
    title: "Wellness",
    description: "Challenges, programs, and citizen wellness profile.",
    citizenSection: "wellness",
    maturity: "partial",
  },
  {
    id: "enterprise",
    number: 4,
    title: "Enterprise resources",
    description: "Find providers and facility-linked services.",
    citizenSection: "discover-providers",
    maturity: "partial",
  },
  {
    id: "tele-dispatch",
    number: 5,
    title: "Telemedicine → dispatch",
    description: "Track Nhume deliveries after telehealth and Rx.",
    citizenSection: "nhume-track",
    maturity: "partial",
  },
  {
    id: "public-health",
    number: 6,
    title: "Public health + geo",
    description: "Remote monitoring devices and field-linked alerts.",
    citizenSection: "monitoring",
    maturity: "partial",
  },
  {
    id: "data-intel",
    number: 7,
    title: "Data & intelligence",
    description: "Personal health records and shared document intelligence.",
    citizenSection: "records",
    maturity: "partial",
  },
];

export function ProductionReadinessJourneyScreen(props: {
  onNavigateSection: (section: string) => void;
}) {
  return (
    <View testID="citizen-prod-ready-journey-screen" style={styles.container}>
      <View style={styles.hero}>
        <Text style={styles.heroEyebrow}>Wave 20 · Production readiness</Text>
        <Text style={styles.heroTitle}>Seven citizen journeys</Text>
        <Text style={styles.heroBody}>
          Balanced parity with provider demo paths — tap to jump into the matching My Health section.
        </Text>
        <FeatureMaturityBadge status="partial" detail="Citizen sections mirror web demo map maturity labels." />
      </View>

      <View testID="citizen-prod-ready-journey-list" style={styles.list}>
        <Text testID="citizen-prod-ready-journey-count" style={styles.countLabel}>
          {CITIZEN_DEMO_JOURNEYS.length} journeys
        </Text>
        {CITIZEN_DEMO_JOURNEYS.map((journey) => (
          <Pressable
            key={journey.id}
            testID={`citizen-prod-ready-journey-item-${journey.id}`}
            onPress={() => props.onNavigateSection(journey.citizenSection)}
            style={({ pressed }) => [styles.cardPressable, pressed && styles.cardPressed]}
          >
            <Card>
              <CardBody>
                <View style={styles.cardHeader}>
                  <Text style={styles.cardNumber}>{journey.number}</Text>
                  <FeatureMaturityBadge status="partial" />
                </View>
                <Text style={styles.cardTitle}>{journey.title}</Text>
                <Text style={styles.cardDescription}>{journey.description}</Text>
              </CardBody>
            </Card>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
    paddingBottom: 24,
  },
  hero: {
    gap: 8,
    padding: 14,
    borderRadius: 20,
    backgroundColor: colors.primary[50],
    borderWidth: 1,
    borderColor: colors.primary[100],
  },
  heroEyebrow: {
    fontSize: 11,
    fontWeight: "700",
    letterSpacing: 1.2,
    textTransform: "uppercase",
    color: colors.primary[700],
  },
  heroTitle: {
    fontSize: 18,
    fontWeight: "700",
    color: colors.primary[900],
  },
  heroBody: {
    fontSize: 13,
    lineHeight: 18,
    color: colors.neutral[700],
  },
  list: {
    gap: 10,
  },
  countLabel: {
    fontSize: 12,
    fontWeight: "600",
    color: colors.neutral[600],
  },
  cardPressable: {
    borderRadius: 16,
  },
  cardPressed: {
    opacity: 0.92,
  },
  cardHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 6,
  },
  cardNumber: {
    width: 28,
    height: 28,
    borderRadius: 14,
    overflow: "hidden",
    textAlign: "center",
    lineHeight: 28,
    fontSize: 13,
    fontWeight: "700",
    color: colors.primary[700],
    backgroundColor: colors.primary[100],
  },
  cardTitle: {
    fontSize: 15,
    fontWeight: "700",
    color: colors.neutral[900],
  },
  cardDescription: {
    marginTop: 4,
    fontSize: 12,
    lineHeight: 17,
    color: colors.neutral[600],
  },
});
