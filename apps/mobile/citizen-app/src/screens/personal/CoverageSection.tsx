import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { fetchCoverage } from "../../services/coverageService";
import type { CoverageInfo } from "../../types";
import { USE_SEED_DATA, SEED_COVERAGE } from "../../data/seedHealthRecords";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

export function CoverageSection() {
  const [coverage, setCoverage]         = useState<CoverageInfo[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [expanded, setExpanded]         = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setCoverage(SEED_COVERAGE);
      } else {
        const r = await fetchCoverage();
        setCoverage(r);
      }
    } catch {
      // silent
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <ScrollView
      style={s.root}
      contentContainerStyle={s.content}
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
    >
      {coverage.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="shield-outline" size={52} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyTitle}>No coverage on file</Text>
          <Text style={s.emptySub}>Your insurance and medical aid details will appear here.</Text>
        </View>
      ) : (
        coverage.map((plan, idx) => {
          const isOpen   = expanded === plan.id;
          const isActive = plan.status === "ACTIVE";
          return (
            <Pressable
              key={plan.id}
              onPress={() => setExpanded(isOpen ? null : plan.id)}
              style={s.planCard}
            >
              {/* Card header */}
              <View style={[s.planHeader, { backgroundColor: isActive ? APP_GREEN : APP_TEXT_2 }]}>
                <View style={s.planHeaderDecor1} />
                <View style={s.planHeaderDecor2} />
                <View style={s.planHeaderTop}>
                  <View>
                    <Text style={s.planType}>{plan.planType}</Text>
                    <Text style={s.planName}>{plan.planName}</Text>
                  </View>
                  <View style={[s.statusBadge, { backgroundColor: isActive ? "rgba(255,255,255,0.2)" : "rgba(255,255,255,0.15)" }]}>
                    <View style={[s.statusDot, { backgroundColor: isActive ? "#34D399" : "#9CA3AF" }]} />
                    <Text style={s.statusText}>{plan.status}</Text>
                  </View>
                </View>
                <Text style={s.memberId}>Member ID: {plan.memberId}</Text>
                <Text style={s.effectiveDates}>
                  Effective {new Date(plan.effectiveFrom).toLocaleDateString([], { month: "short", year: "numeric" })}
                  {plan.effectiveTo ? ` – ${new Date(plan.effectiveTo).toLocaleDateString([], { month: "short", year: "numeric" })}` : " (ongoing)"}
                </Text>
              </View>

              {/* Benefits summary */}
              <View style={s.benefitsRow}>
                {plan.copay != null ? (
                  <View style={s.benefitItem}>
                    <Text style={s.benefitValue}>{plan.currency} {plan.copay}</Text>
                    <Text style={s.benefitLabel}>Co-pay</Text>
                  </View>
                ) : null}
                {plan.deductible != null ? (
                  <View style={s.benefitItem}>
                    <Text style={s.benefitValue}>{plan.currency} {plan.deductible}</Text>
                    <Text style={s.benefitLabel}>Deductible</Text>
                  </View>
                ) : null}
                {plan.outOfPocketMax != null ? (
                  <View style={s.benefitItem}>
                    <Text style={s.benefitValue}>{plan.currency} {plan.outOfPocketMax}</Text>
                    <Text style={s.benefitLabel}>Out-of-pocket max</Text>
                  </View>
                ) : null}
              </View>

              {/* Expand chevron */}
              <View style={s.expandRow}>
                <Text style={s.expandLabel}>
                  {isOpen ? "Hide details" : "View full details"}
                </Text>
                <Ionicons name={isOpen ? "chevron-up" : "chevron-down"} size={14} color={APP_TEXT_3} />
              </View>

              {isOpen ? (
                <View style={s.details}>
                  {plan.memberNumber ? <DetailRow label="Member number" value={plan.memberNumber} /> : null}
                  <DetailRow label="Plan type" value={plan.planType} />
                  <DetailRow label="Currency" value={plan.currency} />
                  <DetailRow label="Status" value={plan.status} />
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
  content: { padding: 16, gap: 14, paddingBottom: 40 },
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },

  planCard: {
    backgroundColor: APP_SURFACE, borderRadius: 20, overflow: "hidden",
    shadowColor: "#000", shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1, shadowRadius: 14, elevation: 6,
  },
  planHeader: { padding: 20, overflow: "hidden" },
  planHeaderDecor1: { position: "absolute", width: 160, height: 160, borderRadius: 80, borderWidth: 24, borderColor: "rgba(255,255,255,0.07)", top: -60, right: -50 },
  planHeaderDecor2: { position: "absolute", width: 90, height: 90, borderRadius: 45, borderWidth: 16, borderColor: "rgba(255,255,255,0.05)", bottom: -30, left: -20 },
  planHeaderTop: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 },
  planType: { fontSize: 11, fontWeight: "700", color: "rgba(255,255,255,0.65)", textTransform: "uppercase", letterSpacing: 0.8 },
  planName: { fontSize: 20, fontWeight: "800", color: "#FFFFFF", marginTop: 3 },
  statusBadge: { flexDirection: "row", alignItems: "center", gap: 5, paddingHorizontal: 10, paddingVertical: 5, borderRadius: 20 },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  statusText: { fontSize: 11, fontWeight: "700", color: "#FFFFFF" },
  memberId: { fontSize: 13, color: "rgba(255,255,255,0.85)", fontFamily: "monospace" },
  effectiveDates: { fontSize: 11, color: "rgba(255,255,255,0.6)", marginTop: 4 },

  benefitsRow: { flexDirection: "row", paddingHorizontal: 16, paddingVertical: 14, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER },
  benefitItem: { flex: 1, alignItems: "center", gap: 3 },
  benefitValue: { fontSize: 16, fontWeight: "800", color: APP_TEXT },
  benefitLabel: { fontSize: 10, color: APP_TEXT_3, textAlign: "center" },

  expandRow: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 5, paddingVertical: 12 },
  expandLabel: { fontSize: 13, color: APP_TEXT_2 },

  details: { paddingHorizontal: 16, paddingBottom: 16, gap: 6, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER },
  detailRow: { flexDirection: "row", justifyContent: "space-between", paddingTop: 8 },
  detailLabel: { fontSize: 12, color: APP_TEXT_3 },
  detailValue: { fontSize: 13, color: APP_TEXT },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
