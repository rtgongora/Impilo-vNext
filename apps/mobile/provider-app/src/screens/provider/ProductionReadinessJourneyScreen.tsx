import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { Card, CardBody, FeatureMaturityBadge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { fetchDemoJourneys, type DemoJourney } from "../../services/demoJourneyService";

export function ProductionReadinessJourneyScreen(props: {
  onNavigateTab?: (tab: string) => void;
}) {
  const { data: journeys = [], isLoading, isError } = useQuery({
    queryKey: ["demo-journeys"],
    queryFn: fetchDemoJourneys,
  });

  return (
    <View testID="prod-ready-journey-screen" style={styles.container}>
      <View style={styles.hero}>
        <Text style={styles.heroEyebrow}>Wave 20 · Production readiness</Text>
        <Text style={styles.heroTitle}>Seven demo journeys</Text>
        <Text style={styles.heroBody}>
          Golden patient CPID-ZW-00001 — tap a journey to open the matching Clinical Tools tab.
        </Text>
        <FeatureMaturityBadge
          status={isError ? "partial" : "connected"}
          detail="Registry from /internal/v1/demo-journeys with static fallback."
        />
      </View>

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <View testID="prod-ready-journey-list" style={styles.list}>
          <Text testID="prod-ready-journey-count" style={styles.countLabel}>
            {journeys.length} journeys registered
          </Text>
          {journeys.map((journey) => (
            <JourneyCard
              key={journey.id}
              journey={journey}
              onPress={() => props.onNavigateTab?.(journey.mobile_tab)}
            />
          ))}
        </View>
      )}
    </View>
  );
}

function JourneyCard(props: { journey: DemoJourney; onPress: () => void }) {
  const { journey } = props;
  return (
    <Pressable
      testID={`prod-ready-journey-item-${journey.id}`}
      onPress={props.onPress}
      style={({ pressed }) => [styles.cardPressable, pressed && styles.cardPressed]}
    >
      <Card>
        <CardBody>
          <View style={styles.cardHeader}>
            <Text style={styles.cardNumber}>{journey.number}</Text>
            <FeatureMaturityBadge
              status={journey.maturity === "live" ? "live" : journey.maturity === "blocked" ? "blocked" : "partial"}
            />
          </View>
          <Text style={styles.cardTitle}>{journey.title}</Text>
          <Text style={styles.cardDescription}>{journey.description}</Text>
          <Text style={styles.cardMeta}>Tab: {journey.mobile_tab}</Text>
        </CardBody>
      </Card>
    </Pressable>
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
  cardMeta: {
    marginTop: 8,
    fontSize: 11,
    fontWeight: "600",
    color: colors.primary[600],
  },
});
