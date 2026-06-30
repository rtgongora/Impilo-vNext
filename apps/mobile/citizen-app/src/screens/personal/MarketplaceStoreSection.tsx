import React, { useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, TextInput } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner, ErrorState } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import {
  fetchMarketplaceHome,
  searchMarketplace,
  type MarketplaceListing,
} from "../../services/marketplaceStoreService";
import { NompiloGuidanceSection } from "./NompiloGuidanceSection";
import { appStore } from "../../stores/appStore";

/**
 * Msika Health Marketplace — mobile storefront slice (WS#4).
 *
 * The buyer storefront landing inside "My Health". Shows featured published listings
 * (with risk badges and a regulated-gate hint), Nompilo contextual guidance, and quick
 * jumps into the existing Personal sections (search, activity). Owns no data — reads the
 * BFF storefront composition. Mirrors the web /marketplace/store page for web↔mobile parity.
 */

const RISK_COLOR: Record<string, string> = {
  UNRESTRICTED: "#059669",
  LOW_RISK: "#0284C7",
  MODERATE_RISK: "#D97706",
  HIGH_RISK: "#EA580C",
  REGULATED: "#DC2626",
};

const REGULATED = new Set(["HIGH_RISK", "REGULATED"]);

export function MarketplaceStoreSection() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["msika-store", "home"],
    queryFn: fetchMarketplaceHome,
  });

  if (isLoading) {
    return <LoadingSpinner />;
  }
  if (isError || !data) {
    return (
      <ErrorState
        title="Couldn't load the marketplace"
        message="We couldn't load the marketplace just now. Please try again."
        onRetry={() => void refetch()}
      />
    );
  }

  const featured = data.featured ?? [];

  return <MarketplaceStoreBody featured={featured} activityUnavailable={data.activityUnavailable} />;
}

function MarketplaceStoreBody({
  featured,
  activityUnavailable,
}: {
  featured: MarketplaceListing[];
  activityUnavailable: boolean;
}) {
  const [term, setTerm] = useState("");
  const [submitted, setSubmitted] = useState("");
  const searchQuery = useQuery({
    queryKey: ["msika-store", "search", submitted],
    queryFn: () => searchMarketplace(submitted),
    enabled: submitted.length > 0,
  });
  const searchResults = submitted ? searchQuery.data ?? [] : [];
  const shown = submitted ? searchResults : featured;

  return (
    <ScrollView contentContainerStyle={styles.container} testID="marketplace-store-section">
      <Text style={styles.title}>Health Marketplace</Text>
      <Text style={styles.subtitle}>
        Discover approved products and services from verified sellers. Billing stays with Costa,
        payments with MusheX, and clinical items route through your care team.
      </Text>

      <View style={styles.searchRow}>
        <TextInput
          style={styles.searchInput}
          placeholder="Search products and services"
          value={term}
          onChangeText={setTerm}
          onSubmitEditing={() => setSubmitted(term.trim())}
          returnKeyType="search"
          testID="marketplace-search-input"
        />
        <TouchableOpacity
          style={styles.searchBtn}
          testID="marketplace-action-search"
          activeOpacity={0.85}
          onPress={() => setSubmitted(term.trim())}
        >
          <Ionicons name="search" size={18} color="#FFFFFF" />
        </TouchableOpacity>
      </View>
      <TouchableOpacity
        style={styles.activityLink}
        testID="marketplace-action-activity"
        activeOpacity={0.85}
        onPress={() => appStore.getState().setPersonalSectionRequest("bookings")}
      >
        <Ionicons name="clipboard-outline" size={16} color="#059669" />
        <Text style={styles.activityLinkText}>My orders & bookings (msika-flow)</Text>
      </TouchableOpacity>

      {/* Nompilo contextual guidance — locked/regulated explainers + route-to-owner CTAs.
          Mirrors the web NompiloContextualGuidance panel for web↔mobile parity. */}
      <NompiloGuidanceSection routePath="/marketplace/store" />

      <Text style={styles.sectionHeader}>{submitted ? "Search results" : "Featured & recently published"}</Text>
      {submitted && searchQuery.isLoading ? (
        <Text style={styles.empty}>Searching…</Text>
      ) : shown.length === 0 ? (
        <Text style={styles.empty}>{submitted ? "No listings match your search." : "No published listings yet."}</Text>
      ) : (
        shown.map((l: MarketplaceListing) => (
          <View key={l.listingId} style={styles.listingCard} testID={`marketplace-listing-${l.listingId}`}>
            <View style={styles.listingHeader}>
              <Text style={styles.listingTitle}>{l.title}</Text>
              <View style={[styles.riskBadge, { borderColor: RISK_COLOR[l.riskClassification] ?? "#9CA3AF" }]}>
                <Text style={[styles.riskText, { color: RISK_COLOR[l.riskClassification] ?? "#6B7280" }]}>
                  {l.riskClassification.replace("_", " ")}
                </Text>
              </View>
            </View>
            {l.summary ? <Text style={styles.listingSummary}>{l.summary}</Text> : null}
            <Text style={styles.listingMeta}>{l.priceDisplay ?? "Price via Costa"}</Text>
            {REGULATED.has(l.riskClassification) ? (
              <Text style={styles.regulatedHint}>Regulated — eligibility check before checkout</Text>
            ) : null}
          </View>
        ))
      )}

      {activityUnavailable ? (
        <Text style={styles.warn}>Your order activity (msika-flow) is unavailable right now.</Text>
      ) : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: 24 },
  title: { fontSize: 20, fontWeight: "700", color: "#111827" },
  subtitle: { marginTop: 4, fontSize: 13, color: "#6B7280" },
  searchRow: { flexDirection: "row", gap: 8, marginTop: 16 },
  searchInput: {
    flex: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: "#F9FAFB",
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "#E5E7EB",
    fontSize: 14,
    color: "#111827",
  },
  searchBtn: {
    width: 44,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#059669",
    borderRadius: 12,
  },
  activityLink: { flexDirection: "row", alignItems: "center", gap: 6, marginTop: 10 },
  activityLinkText: { fontSize: 13, fontWeight: "600", color: "#059669" },
  sectionHeader: { marginTop: 20, fontSize: 15, fontWeight: "700", color: "#111827" },
  empty: { marginTop: 8, fontSize: 13, color: "#6B7280" },
  listingCard: {
    marginTop: 12,
    padding: 14,
    backgroundColor: "#FFFFFF",
    borderRadius: 14,
    borderWidth: 1,
    borderColor: "#E5E7EB",
  },
  listingHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start", gap: 8 },
  listingTitle: { flex: 1, fontSize: 15, fontWeight: "600", color: "#111827" },
  riskBadge: { borderWidth: 1, borderRadius: 999, paddingHorizontal: 8, paddingVertical: 2 },
  riskText: { fontSize: 10, fontWeight: "600" },
  listingSummary: { marginTop: 4, fontSize: 13, color: "#6B7280" },
  listingMeta: { marginTop: 6, fontSize: 12, color: "#374151" },
  regulatedHint: { marginTop: 6, fontSize: 11, color: "#DC2626" },
  warn: { marginTop: 16, fontSize: 12, color: "#D97706" },
});
