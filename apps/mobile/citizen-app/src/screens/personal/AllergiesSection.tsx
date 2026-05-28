import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import type { Allergy, AllergySeverity } from "../../types";
import { USE_SEED_DATA, SEED_ALLERGIES } from "../../data/seedHealthRecords";
// When USE_SEED_DATA is false: import { fetchAllergies } from "../../services/allergiesService";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

// Severity pill — text label only, no icon.
// Left border on the card is the fast-scan severity indicator.
const SEVERITY_CHIP: Record<AllergySeverity, { label: string; color: string; bg: string }> = {
  LIFE_THREATENING: { label: "Life-threatening", color: "#B91C1C", bg: "#FEE2E2" },
  SEVERE:           { label: "Severe",           color: APP_RED,        bg: APP_RED_LIGHT   },
  MODERATE:         { label: "Moderate",         color: APP_GOLD,       bg: APP_GOLD_LIGHT  },
  MILD:             { label: "Mild",             color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT },
};

const SEVERITY_BORDER: Record<AllergySeverity, string> = {
  LIFE_THREATENING: "#B91C1C",
  SEVERE:           APP_RED,
  MODERATE:         APP_GOLD,
  MILD:             APP_GREEN,
};

function SeverityPill({ severity }: { severity: AllergySeverity }) {
  const cfg = SEVERITY_CHIP[severity] ?? SEVERITY_CHIP.MILD;
  return (
    <View style={[s.pill, { backgroundColor: cfg.bg }]}>
      <Text style={[s.pillText, { color: cfg.color }]}>{cfg.label}</Text>
    </View>
  );
}

export function AllergiesSection() {
  const [allergies, setAllergies]       = useState<Allergy[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError]               = useState<string | null>(null);
  const [expanded, setExpanded]         = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    setError(null);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setAllergies(SEED_ALLERGIES);
      } else {
        // const r = await fetchAllergies(); setAllergies(r.items);
        setAllergies([]);
      }
    } catch {
      setError("Could not load allergies. Pull down to retry.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const severeCount   = allergies.filter((a) => a.severity === "SEVERE" || a.severity === "LIFE_THREATENING").length;
  const moderateCount = allergies.filter((a) => a.severity === "MODERATE").length;
  const mildCount     = allergies.filter((a) => a.severity === "MILD").length;

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <ScrollView
      style={s.root}
      contentContainerStyle={s.content}
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
    >
      {error ? (
        <View style={s.errorBanner}>
          <Ionicons name="alert-circle-outline" size={16} color={APP_RED} />
          <Text style={s.errorText}>{error}</Text>
        </View>
      ) : null}

      {/* Summary strip — uniform white cards, number colours are the only variation */}
      <View style={s.summaryStrip}>
        <View style={s.summaryCard}>
          <Text style={[s.summaryNum, { color: APP_RED }]}>{severeCount}</Text>
          <Text style={s.summaryLbl}>Severe</Text>
        </View>
        <View style={s.summaryCard}>
          <Text style={[s.summaryNum, { color: APP_GOLD }]}>{moderateCount}</Text>
          <Text style={s.summaryLbl}>Moderate</Text>
        </View>
        <View style={s.summaryCard}>
          <Text style={[s.summaryNum, { color: APP_GREEN_DARK }]}>{mildCount}</Text>
          <Text style={s.summaryLbl}>Mild</Text>
        </View>
      </View>

      {/* Severe allergy alert — keep red (patient safety) */}
      {severeCount > 0 ? (
        <View style={s.alertBanner}>
          <Ionicons name="warning" size={15} color={APP_RED} />
          <Text style={s.alertText}>
            {severeCount} severe allerg{severeCount > 1 ? "ies" : "y"} on record — always inform your care team.
          </Text>
        </View>
      ) : null}

      {allergies.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="checkmark-circle-outline" size={52} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyTitle}>No allergies recorded</Text>
          <Text style={s.emptySub}>Your allergy history will appear here when added by your care team.</Text>
        </View>
      ) : (
        allergies.map((a) => {
          const isOpen     = expanded === a.id;
          const borderColor= SEVERITY_BORDER[a.severity] ?? APP_BORDER;
          return (
            <Pressable
              key={a.id}
              onPress={() => setExpanded(isOpen ? null : a.id)}
              style={[s.card, { borderLeftColor: borderColor }]}
            >
              <View style={s.cardTop}>
                {/* Uniform teal icon — severity shown only via pill text + left border */}
                <View style={s.cardIcon}>
                  <Ionicons name="alert-circle-outline" size={18} color={APP_GREEN} />
                </View>

                <View style={s.cardMain}>
                  <Text style={s.allergenName}>{a.allergen}</Text>
                  <Text style={s.reactionText} numberOfLines={isOpen ? undefined : 1}>{a.reaction}</Text>
                </View>

                <View style={s.cardRight}>
                  <SeverityPill severity={a.severity} />
                  <Ionicons
                    name={isOpen ? "chevron-up" : "chevron-down"}
                    size={14}
                    color={APP_TEXT_3}
                    style={{ marginTop: 6 }}
                  />
                </View>
              </View>

              {isOpen ? (
                <View style={s.cardExpanded}>
                  {a.onsetDate ? <DetailRow label="Onset" value={new Date(a.onsetDate).toLocaleDateString()} /> : null}
                  <DetailRow label="Recorded" value={new Date(a.recordedAt).toLocaleDateString()} />
                  {a.recordedBy ? <DetailRow label="Recorded by" value={a.recordedBy} /> : null}
                  {a.facilityName ? <DetailRow label="Facility" value={a.facilityName} /> : null}
                  {a.notes ? (
                    <View style={s.noteBox}>
                      <Text style={s.noteText}>{a.notes}</Text>
                    </View>
                  ) : null}
                </View>
              ) : null}
            </Pressable>
          );
        })
      )}
    </ScrollView>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={s.detailRow}>
      <Text style={s.detailLabel}>{label}</Text>
      <Text style={s.detailValue}>{value}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 12, paddingBottom: 40 },
  centred: { flex: 1, alignItems: "center", justifyContent: "center", padding: 40 },
  errorBanner: {
    flexDirection: "row", alignItems: "center", gap: 8,
    backgroundColor: APP_RED_LIGHT, padding: 12, borderRadius: 12,
  },
  errorText: { fontSize: 13, color: APP_RED, flex: 1 },

  /* Summary — uniform white cards */
  summaryStrip: { flexDirection: "row", gap: 10 },
  summaryCard: {
    flex: 1, backgroundColor: APP_SURFACE, borderRadius: 14,
    padding: 14, alignItems: "center", gap: 4,
    shadowColor: "#000", shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04, shadowRadius: 3, elevation: 2,
  },
  summaryNum: { fontSize: 24, fontWeight: "800" },
  summaryLbl: { fontSize: 11, fontWeight: "600", color: APP_TEXT_2 },

  alertBanner: {
    flexDirection: "row", alignItems: "flex-start", gap: 8,
    backgroundColor: APP_RED_LIGHT, borderRadius: 12, padding: 12,
    borderLeftWidth: 3, borderLeftColor: APP_RED,
  },
  alertText: { fontSize: 13, color: APP_RED, flex: 1, lineHeight: 18 },

  card: {
    backgroundColor: APP_SURFACE, borderRadius: 16, borderLeftWidth: 3,
    overflow: "hidden",
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05, shadowRadius: 6, elevation: 2,
  },
  cardTop: { flexDirection: "row", alignItems: "flex-start", gap: 12, padding: 14 },

  /* Uniform teal icon */
  cardIcon: {
    width: 38, height: 38, borderRadius: 10,
    backgroundColor: APP_GREEN_XLIGHT,
    alignItems: "center", justifyContent: "center",
  },

  cardMain: { flex: 1 },
  cardRight: { alignItems: "flex-end" },
  allergenName: { fontSize: 15, fontWeight: "600", color: APP_TEXT, marginBottom: 4 },
  reactionText: { fontSize: 13, color: APP_TEXT_2, lineHeight: 18 },

  /* Severity pill — text only, no icon */
  pill: {
    paddingHorizontal: 9, paddingVertical: 4, borderRadius: 10,
  },
  pillText: { fontSize: 11, fontWeight: "700" },

  cardExpanded: {
    paddingHorizontal: 14, paddingBottom: 14, gap: 6,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  detailRow: { flexDirection: "row", justifyContent: "space-between", paddingTop: 8 },
  detailLabel: { fontSize: 12, color: APP_TEXT_3, width: 110 },
  detailValue: { fontSize: 13, color: APP_TEXT, flex: 1, textAlign: "right" },
  noteBox: {
    backgroundColor: APP_GREEN_XLIGHT, borderRadius: 10, padding: 10, marginTop: 6,
  },
  noteText: { fontSize: 12, color: APP_TEXT_2, lineHeight: 17 },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
