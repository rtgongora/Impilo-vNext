import React from "react";
import { View, Text, TouchableOpacity, StyleSheet, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { fetchPersonWalletOverview } from "../../services/personHealthWalletService";
import { NompiloGuidanceSection } from "./NompiloGuidanceSection";
import { appStore } from "../../stores/appStore";

/**
 * Mushe Personal Health Wallet — mobile home / overview.
 *
 * The person anchor landing inside "My Health". Shows trust + profile completion, Nompilo
 * next-steps, and a grid of real, source-labelled cards that jump into the existing Personal
 * sections (records, timeline, consent, dependants, payments, comms). Mirrors the web wallet page
 * for web↔mobile parity. Owns no data — reads the BFF overview composition.
 */

type AnyRecord = Record<string, unknown>;

function num(v: unknown): number {
  return typeof v === "number" ? v : 0;
}

interface CardSpec {
  label: string;
  icon: React.ComponentProps<typeof Ionicons>["name"];
  section: string;
  count?: number;
  countLabel?: string;
  source?: string;
  unavailable?: boolean;
}

export function WalletOverviewSection() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["person-wallet", "overview"],
    queryFn: fetchPersonWalletOverview,
  });

  if (isLoading) {
    return <LoadingSpinner />;
  }
  if (isError || !data) {
    return (
      <ErrorState
        title="Couldn't load your wallet"
        message="We couldn't load your wallet just now. Please try again."
        onRetry={() => void refetch()}
      />
    );
  }

  const completion = data.profileCompletion;
  const records = (data.records ?? {}) as AnyRecord;
  const timeline = (data.careTimeline ?? {}) as AnyRecord;
  const consent = (data.consent ?? {}) as AnyRecord;
  const dependants = (data.dependants ?? {}) as AnyRecord;
  const payments = (data.payments ?? {}) as AnyRecord;
  const nextActions = data.nextActions ?? [];

  const cards: CardSpec[] = [
    { label: "Health ID & trust", icon: "card", section: "health-id", source: "VITO · Assurance" },
    {
      label: "Health records",
      icon: "heart",
      section: "records",
      unavailable: records.unavailable === true,
      source: String(records._source ?? "BUTANO · PCT"),
    },
    {
      label: "Care timeline",
      icon: "time",
      section: "timeline",
      count: num(timeline.eventCount),
      countLabel: "events",
      unavailable: timeline.unavailable === true,
      source: "PCT",
    },
    {
      label: "Sharing & consent",
      icon: "share-social",
      section: "record-sharing",
      count: num(consent.activeCount),
      countLabel: "active",
      source: "Tshepo consent",
    },
    {
      label: "Dependants",
      icon: "people",
      section: "care-team",
      count: num(dependants.iCanActForCount),
      countLabel: "you can act for",
      source: "Mvumo · VITO",
    },
    {
      label: "Payments & bills",
      icon: "cash",
      section: "finance",
      count: num(payments.outstandingCount),
      countLabel: "outstanding",
      unavailable: payments.unavailable === true,
      source: "Costa · MusheX",
    },
    {
      label: "Comms preferences",
      icon: "chatbubbles-outline",
      section: "comms-prefs",
      source: "Khuluma delivery",
    },
  ];

  return (
    <ScrollView contentContainerStyle={styles.container} testID="wallet-overview-section">
      <Text style={styles.title}>Mushe Personal Health Wallet</Text>
      <Text style={styles.tagline}>Your Health, Your Wealth</Text>
      <Text style={styles.subtitle}>
        Your health life in one place — drawn live from the services that own it.
      </Text>

      {completion ? (
        <View style={styles.meterCard}>
          <View style={styles.meterRow}>
            <Text style={styles.meterLabel}>Profile completion</Text>
            <Text style={styles.meterPercent}>{completion.percent}%</Text>
          </View>
          <View style={styles.meterTrack}>
            <View style={[styles.meterFill, { width: `${Math.min(100, Math.max(0, completion.percent))}%` }]} />
          </View>
          {completion.missing.length > 0 ? (
            <Text style={styles.meterHint}>Add: {completion.missing.join(", ")}</Text>
          ) : null}
        </View>
      ) : null}

      {nextActions.length > 0 ? (
        <View style={styles.nextCard}>
          <Text style={styles.nextHeader}>What to do next · Nompilo</Text>
          {nextActions.map((a) => (
            <View key={a.code} style={styles.nextItem}>
              <Text style={styles.nextTitle}>{a.title}</Text>
              <Text style={styles.nextWhy}>{a.why}</Text>
            </View>
          ))}
        </View>
      ) : null}

      {/* Nompilo deepens the wallet next-steps with config-driven contextual guidance:
          locked-state explainers, route-to-owner CTAs, Khuluma follow-ups, and dismissal.
          Mirrors the web NompiloContextualGuidance panel for web↔mobile parity. */}
      <NompiloGuidanceSection routePath="/citizen/wallet" />

      <View style={styles.grid}>
        {cards.map((c) => (
          <TouchableOpacity
            key={c.section}
            style={styles.card}
            testID={`wallet-card-${c.section}`}
            activeOpacity={0.85}
            onPress={() => appStore.getState().setPersonalSectionRequest(c.section)}
          >
            <Ionicons name={c.icon} size={20} color="#059669" />
            <Text style={styles.cardLabel}>{c.label}</Text>
            {c.unavailable ? (
              <Text style={styles.cardMutedWarn}>Temporarily unavailable</Text>
            ) : typeof c.count === "number" ? (
              <Text style={styles.cardCount}>
                {c.count} {c.countLabel}
              </Text>
            ) : null}
            {c.source ? <Text style={styles.cardSource}>{c.source}</Text> : null}
          </TouchableOpacity>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: 24 },
  title: { fontSize: 20, fontWeight: "700", color: colors.gray[900] },
  tagline: { marginTop: 2, fontSize: 14, fontWeight: "600", color: "#2563EB" },
  subtitle: { marginTop: 4, fontSize: 13, color: colors.gray[500] },
  meterCard: {
    marginTop: 16,
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.gray[200],
    backgroundColor: "#FFFFFF",
  },
  meterRow: { flexDirection: "row", justifyContent: "space-between", marginBottom: 6 },
  meterLabel: { fontSize: 13, fontWeight: "600", color: colors.gray[900] },
  meterPercent: { fontSize: 13, fontWeight: "700", color: colors.gray[900] },
  meterTrack: { height: 8, borderRadius: 8, backgroundColor: colors.gray[100], overflow: "hidden" },
  meterFill: { height: 8, borderRadius: 8, backgroundColor: "#10B981" },
  meterHint: { marginTop: 6, fontSize: 11, color: colors.gray[500] },
  nextCard: {
    marginTop: 16,
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "#FDE68A",
    backgroundColor: "#FFFBEB",
  },
  nextHeader: { fontSize: 12, fontWeight: "700", color: colors.ui.warning.text, marginBottom: 6 },
  nextItem: { marginBottom: 6 },
  nextTitle: { fontSize: 13, fontWeight: "600", color: colors.gray[900] },
  nextWhy: { fontSize: 11, color: colors.gray[500] },
  grid: { marginTop: 16, flexDirection: "row", flexWrap: "wrap", gap: 12 },
  card: {
    width: "47%",
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.gray[200],
    backgroundColor: "#FFFFFF",
    gap: 4,
  },
  cardLabel: { fontSize: 13, fontWeight: "600", color: colors.gray[900] },
  cardCount: { fontSize: 11, color: colors.gray[500] },
  cardMutedWarn: { fontSize: 11, color: colors.gray[400] },
  cardSource: { fontSize: 9, color: colors.gray[400], textTransform: "uppercase" },
});
