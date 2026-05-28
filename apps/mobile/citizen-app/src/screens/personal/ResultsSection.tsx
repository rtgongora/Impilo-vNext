import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { fetchLabResults } from "../../services/labResultService";
import type { LabResult, LabResultStatus } from "../../types";
import { USE_SEED_DATA, SEED_LAB_RESULTS } from "../../data/seedHealthRecords";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

type Filter = "ALL" | LabResultStatus;

const STATUS_CFG: Record<LabResultStatus, { color: string; bg: string; label: string }> = {
  ORDERED:    { color: APP_TEXT_2,     bg: "#EEF2F2",        label: "Ordered"    },
  COLLECTED:  { color: APP_GOLD,       bg: APP_GOLD_LIGHT,   label: "Collected"  },
  PROCESSING: { color: APP_GOLD,       bg: APP_GOLD_LIGHT,   label: "Processing" },
  COMPLETED:  { color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT,  label: "Completed"  },
  CANCELLED:  { color: APP_RED,        bg: APP_RED_LIGHT,    label: "Cancelled"  },
};

const CATEGORY_COLORS: Record<string, { color: string; bg: string }> = {
  Endocrinology:  { color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT  },
  Haematology:    { color: APP_RED,        bg: APP_RED_LIGHT    },
  Biochemistry:   { color: APP_GOLD,       bg: APP_GOLD_LIGHT   },
  Nephrology:     { color: APP_GREEN,      bg: APP_GREEN_XLIGHT },
  Microbiology:   { color: APP_GOLD,       bg: APP_GOLD_LIGHT   },
};

export function ResultsSection() {
  const [results, setResults]           = useState<LabResult[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [filter, setFilter]             = useState<Filter>("ALL");
  const [expanded, setExpanded]         = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setResults(SEED_LAB_RESULTS);
      } else {
        const r = await fetchLabResults({ size: 50 });
        setResults(r.items);
      }
    } catch {
      // silent fail — empty state shown
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const completed  = results.filter((r) => r.status === "COMPLETED").length;
  const pending    = results.filter((r) => r.status === "PROCESSING" || r.status === "ORDERED" || r.status === "COLLECTED").length;
  const displayed  = filter === "ALL" ? results : results.filter((r) => r.status === filter);

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <View style={s.root}>
      {/* Toolbar */}
      <ScrollView
        horizontal showsHorizontalScrollIndicator={false}
        style={s.toolbar} contentContainerStyle={s.filterRow}
      >
        {(["ALL", "COMPLETED", "PROCESSING", "ORDERED"] as (Filter)[]).map((f) => (
          <Pressable
            key={f}
            onPress={() => setFilter(f)}
            style={[s.filterPill, filter === f && s.filterPillActive]}
          >
            <Text style={[s.filterPillText, filter === f && s.filterPillTextActive]}>
              {f === "ALL" ? `All (${results.length})` : STATUS_CFG[f as LabResultStatus]?.label ?? f}
            </Text>
          </Pressable>
        ))}
      </ScrollView>

      <ScrollView
        style={s.scroll}
        contentContainerStyle={s.content}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
      >
        {/* Summary */}
        <View style={s.summaryStrip}>
          <View style={[s.summaryCard, { backgroundColor: APP_GREEN_LIGHT }]}>
            <Text style={[s.summaryNum, { color: APP_GREEN_DARK }]}>{completed}</Text>
            <Text style={s.summaryLbl}>Completed</Text>
          </View>
          <View style={[s.summaryCard, { backgroundColor: pending > 0 ? APP_GOLD_LIGHT : "#EEF2F2" }]}>
            <Text style={[s.summaryNum, { color: pending > 0 ? APP_GOLD : APP_TEXT_3 }]}>{pending}</Text>
            <Text style={s.summaryLbl}>Pending</Text>
          </View>
          <View style={[s.summaryCard, { backgroundColor: APP_GREEN_XLIGHT }]}>
            <Text style={[s.summaryNum, { color: APP_GREEN }]}>{results.length}</Text>
            <Text style={s.summaryLbl}>Total</Text>
          </View>
        </View>

        {displayed.length === 0 ? (
          <View style={s.empty}>
            <Ionicons name="flask-outline" size={52} color={APP_GREEN_LIGHT} />
            <Text style={s.emptyTitle}>No results found</Text>
            <Text style={s.emptySub}>Your lab results will appear here when ordered by your doctor.</Text>
          </View>
        ) : (
          displayed.map((lab) => {
            const isOpen  = expanded === lab.id;
            const cfg     = STATUS_CFG[lab.status] ?? STATUS_CFG.ORDERED;
            const catCfg  = CATEGORY_COLORS[lab.category] ?? { color: APP_GREEN, bg: APP_GREEN_XLIGHT };
            return (
              <Pressable
                key={lab.id}
                onPress={() => setExpanded(isOpen ? null : lab.id)}
                style={[s.card, { borderLeftColor: cfg.color }]}
              >
                <View style={s.cardTop}>
                  <View style={[s.labIcon, { backgroundColor: catCfg.bg }]}>
                    <Ionicons name="flask" size={18} color={catCfg.color} />
                  </View>
                  <View style={s.cardMain}>
                    <Text style={s.testName} numberOfLines={1}>{lab.testName}</Text>
                    <View style={s.catRow}>
                      <View style={[s.catPill, { backgroundColor: catCfg.bg }]}>
                        <Text style={[s.catText, { color: catCfg.color }]}>{lab.category}</Text>
                      </View>
                    </View>
                    <View style={s.metaRow}>
                      <Ionicons name="business-outline" size={12} color={APP_TEXT_3} />
                      <Text style={s.metaText} numberOfLines={1}>{lab.facilityName}</Text>
                    </View>
                    {lab.resultAt ? (
                      <View style={s.metaRow}>
                        <Ionicons name="calendar-outline" size={12} color={APP_TEXT_3} />
                        <Text style={s.metaText}>{new Date(lab.resultAt).toLocaleDateString()}</Text>
                      </View>
                    ) : null}
                  </View>
                  <View style={s.cardRight}>
                    <View style={[s.statusChip, { backgroundColor: cfg.bg }]}>
                      <Text style={[s.statusText, { color: cfg.color }]}>{cfg.label}</Text>
                    </View>
                    {lab.value ? (
                      <Text style={s.valueText}>{lab.value} {lab.unit ?? ""}</Text>
                    ) : null}
                    <Ionicons name={isOpen ? "chevron-up" : "chevron-down"} size={14} color={APP_TEXT_3} style={{ marginTop: 4 }} />
                  </View>
                </View>

                {isOpen ? (
                  <View style={s.expanded}>
                    {lab.value ? <DetailRow label="Result" value={`${lab.value} ${lab.unit ?? ""}`} highlight /> : null}
                    {lab.referenceRange ? <DetailRow label="Reference range" value={lab.referenceRange} /> : null}
                    <DetailRow label="Ordered by" value={lab.orderedBy} />
                    {lab.collectedAt ? <DetailRow label="Collected" value={new Date(lab.collectedAt).toLocaleDateString()} /> : null}
                    {lab.interpretation ? (
                      <View style={s.interpretBox}>
                        <Ionicons name="checkmark-done-outline" size={13} color={APP_GREEN} />
                        <Text style={s.interpretText}>{lab.interpretation}</Text>
                      </View>
                    ) : null}
                  </View>
                ) : null}
              </Pressable>
            );
          })
        )}
      </ScrollView>
    </View>
  );
}

function DetailRow({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <View style={s.detailRow}>
      <Text style={s.detailLabel}>{label}</Text>
      <Text style={[s.detailValue, highlight && { color: APP_GREEN_DARK, fontWeight: "700" }]}>{value}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },
  toolbar: {
    flexGrow: 0, backgroundColor: APP_SURFACE,
    borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER,
  },
  filterRow: { paddingHorizontal: 16, paddingVertical: 10, gap: 8 },
  filterPill: { paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20, backgroundColor: "#EEF2F2" },
  filterPillActive: { backgroundColor: APP_GREEN_XLIGHT, borderWidth: 1, borderColor: APP_GREEN_LIGHT },
  filterPillText: { fontSize: 12, fontWeight: "500", color: APP_TEXT_2 },
  filterPillTextActive: { color: APP_GREEN, fontWeight: "700" },
  scroll: { flex: 1 },
  content: { padding: 16, gap: 12, paddingBottom: 40 },

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
  labIcon: { width: 42, height: 42, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  cardMain: { flex: 1, gap: 4 },
  cardRight: { alignItems: "flex-end", gap: 4 },
  testName: { fontSize: 14, fontWeight: "700", color: APP_TEXT },
  catRow: { flexDirection: "row" },
  catPill: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 8 },
  catText: { fontSize: 10, fontWeight: "600" },
  metaRow: { flexDirection: "row", alignItems: "center", gap: 4 },
  metaText: { fontSize: 12, color: APP_TEXT_2, flex: 1 },
  statusChip: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10 },
  statusText: { fontSize: 11, fontWeight: "700" },
  valueText: { fontSize: 13, fontWeight: "700", color: APP_GREEN_DARK },

  expanded: {
    paddingHorizontal: 14, paddingBottom: 14, gap: 6,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  detailRow: { flexDirection: "row", justifyContent: "space-between", paddingTop: 6 },
  detailLabel: { fontSize: 12, color: APP_TEXT_3, width: 120 },
  detailValue: { fontSize: 13, color: APP_TEXT, flex: 1, textAlign: "right" },
  interpretBox: { flexDirection: "row", gap: 8, backgroundColor: APP_GREEN_XLIGHT, borderRadius: 10, padding: 10, marginTop: 4 },
  interpretText: { fontSize: 12, color: APP_TEXT_2, flex: 1, lineHeight: 18 },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
