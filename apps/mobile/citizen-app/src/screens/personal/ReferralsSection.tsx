import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import type { Referral, ReferralStatus } from "../../types";
import { USE_SEED_DATA, SEED_REFERRALS } from "../../data/seedHealthRecords";
// When USE_SEED_DATA is false: import { fetchReferrals } from "../../services/referralsService";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const STATUS_CFG: Record<ReferralStatus, { color: string; bg: string; label: string }> = {
  PENDING:     { color: APP_GOLD,       bg: APP_GOLD_LIGHT,   label: "Pending"     },
  ACCEPTED:    { color: APP_GREEN,      bg: APP_GREEN_LIGHT,  label: "Accepted"    },
  IN_PROGRESS: { color: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT, label: "In Progress" },
  COMPLETED:   { color: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT, label: "Completed"   },
  CANCELLED:   { color: APP_RED,        bg: APP_RED_LIGHT,    label: "Cancelled"   },
};

export function ReferralsSection() {
  const [referrals, setReferrals]       = useState<Referral[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [expanded, setExpanded]         = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setReferrals(SEED_REFERRALS);
      } else {
        // const r = await fetchReferrals(); setReferrals(r.items);
        setReferrals([]);
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

  const active    = referrals.filter((r) => r.status === "PENDING" || r.status === "ACCEPTED").length;
  const completed = referrals.filter((r) => r.status === "COMPLETED").length;

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <ScrollView
      style={s.root}
      contentContainerStyle={s.content}
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
    >
      <View style={s.summaryStrip}>
        <View style={[s.summaryCard, { backgroundColor: APP_GOLD_LIGHT }]}>
          <Text style={[s.summaryNum, { color: APP_GOLD }]}>{active}</Text>
          <Text style={s.summaryLbl}>Active</Text>
        </View>
        <View style={[s.summaryCard, { backgroundColor: APP_GREEN_LIGHT }]}>
          <Text style={[s.summaryNum, { color: APP_GREEN_DARK }]}>{completed}</Text>
          <Text style={s.summaryLbl}>Completed</Text>
        </View>
        <View style={[s.summaryCard, { backgroundColor: APP_GREEN_XLIGHT }]}>
          <Text style={[s.summaryNum, { color: APP_GREEN }]}>{referrals.length}</Text>
          <Text style={s.summaryLbl}>Total</Text>
        </View>
      </View>

      {referrals.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="people-outline" size={52} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyTitle}>No referrals</Text>
          <Text style={s.emptySub}>Specialist referrals from your care team will appear here.</Text>
        </View>
      ) : (
        referrals.map((ref) => {
          const isOpen = expanded === ref.id;
          const cfg    = STATUS_CFG[ref.status] ?? STATUS_CFG.PENDING;
          return (
            <Pressable
              key={ref.id}
              onPress={() => setExpanded(isOpen ? null : ref.id)}
              style={[s.card, { borderLeftColor: cfg.color }]}
            >
              <View style={s.cardTop}>
                <View style={[s.cardIcon, { backgroundColor: cfg.bg }]}>
                  <Ionicons name="arrow-forward-circle" size={20} color={cfg.color} />
                </View>
                <View style={s.cardMain}>
                  <Text style={s.specialty}>{ref.specialty ?? "General Referral"}</Text>
                  <View style={s.metaRow}>
                    <Ionicons name="business-outline" size={12} color={APP_TEXT_3} />
                    <Text style={s.metaText} numberOfLines={1}>
                      {ref.toFacilityName ?? ref.toProviderName ?? "Facility TBC"}
                    </Text>
                  </View>
                  {ref.toProviderName ? (
                    <View style={s.metaRow}>
                      <Ionicons name="person-circle-outline" size={12} color={APP_TEXT_3} />
                      <Text style={s.metaText}>{ref.toProviderName}</Text>
                    </View>
                  ) : null}
                  <View style={s.metaRow}>
                    <Ionicons name="calendar-outline" size={12} color={APP_TEXT_3} />
                    <Text style={s.metaText}>{new Date(ref.requestedAt).toLocaleDateString()}</Text>
                  </View>
                </View>
                <View style={s.cardRight}>
                  <View style={[s.statusChip, { backgroundColor: cfg.bg }]}>
                    <Text style={[s.statusText, { color: cfg.color }]}>{cfg.label}</Text>
                  </View>
                  <Ionicons name={isOpen ? "chevron-up" : "chevron-down"} size={14} color={APP_TEXT_3} style={{ marginTop: 6 }} />
                </View>
              </View>

              {isOpen ? (
                <View style={s.expanded}>
                  <View style={s.reasonBox}>
                    <Ionicons name="document-text-outline" size={13} color={APP_TEXT_3} />
                    <Text style={s.reasonText}>{ref.reason}</Text>
                  </View>
                  <DetailRow label="From" value={ref.fromFacilityName} />
                  {ref.acceptedAt ? <DetailRow label="Accepted" value={new Date(ref.acceptedAt).toLocaleDateString()} /> : null}
                  {ref.completedAt ? <DetailRow label="Completed" value={new Date(ref.completedAt).toLocaleDateString()} /> : null}
                  {ref.notes ? (
                    <View style={s.noteBox}>
                      <Ionicons name="chatbubble-outline" size={13} color={APP_GREEN} />
                      <Text style={s.noteText}>{ref.notes}</Text>
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
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },
  summaryStrip: { flexDirection: "row", gap: 10 },
  summaryCard: { flex: 1, borderRadius: 14, padding: 14, alignItems: "center", gap: 4 },
  summaryNum: { fontSize: 22, fontWeight: "800" },
  summaryLbl: { fontSize: 11, fontWeight: "600", color: APP_TEXT_2 },
  card: {
    backgroundColor: APP_SURFACE, borderRadius: 16, borderLeftWidth: 4,
    overflow: "hidden", shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 6, elevation: 3,
  },
  cardTop: { flexDirection: "row", alignItems: "flex-start", gap: 12, padding: 14 },
  cardIcon: { width: 42, height: 42, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  cardMain: { flex: 1, gap: 4 },
  cardRight: { alignItems: "flex-end" },
  specialty: { fontSize: 15, fontWeight: "700", color: APP_TEXT, marginBottom: 3 },
  metaRow: { flexDirection: "row", alignItems: "center", gap: 4 },
  metaText: { fontSize: 12, color: APP_TEXT_2, flex: 1 },
  statusChip: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 10 },
  statusText: { fontSize: 11, fontWeight: "700" },
  expanded: {
    paddingHorizontal: 14, paddingBottom: 14, gap: 8,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  reasonBox: { flexDirection: "row", gap: 8, backgroundColor: APP_GREEN_XLIGHT, borderRadius: 10, padding: 10 },
  reasonText: { fontSize: 13, color: APP_TEXT_2, flex: 1, lineHeight: 18 },
  detailRow: { flexDirection: "row", justifyContent: "space-between", paddingTop: 4 },
  detailLabel: { fontSize: 12, color: APP_TEXT_3, width: 100 },
  detailValue: { fontSize: 13, color: APP_TEXT, flex: 1, textAlign: "right" },
  noteBox: { flexDirection: "row", gap: 8, backgroundColor: APP_GREEN_LIGHT, borderRadius: 10, padding: 10 },
  noteText: { fontSize: 12, color: APP_TEXT_2, flex: 1, lineHeight: 17 },
  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
