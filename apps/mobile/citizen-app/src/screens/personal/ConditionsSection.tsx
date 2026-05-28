import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import type { Condition, ConditionStatus } from "../../types";
import { USE_SEED_DATA, SEED_CONDITIONS } from "../../data/seedHealthRecords";
// When USE_SEED_DATA is false: import { fetchConditions } from "../../services/conditionsService";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

type FilterValue = "ALL" | ConditionStatus;

// Status chip keeps meaningful colour (Active = amber caution, Resolved = green, rest = neutral).
// Card icon is uniform teal — status communicated by chip + left border alone.
const STATUS_LABEL: Record<ConditionStatus, string> = {
  ACTIVE:    "Active",
  RESOLVED:  "Resolved",
  INACTIVE:  "Inactive",
  REMISSION: "Remission",
};

const STATUS_CHIP: Record<ConditionStatus, { color: string; bg: string }> = {
  ACTIVE:    { color: APP_GOLD,       bg: APP_GOLD_LIGHT   },
  RESOLVED:  { color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT  },
  INACTIVE:  { color: APP_TEXT_2,     bg: "#EEF2F2"        },
  REMISSION: { color: APP_GREEN,      bg: APP_GREEN_XLIGHT },
};

const STATUS_BORDER: Record<ConditionStatus, string> = {
  ACTIVE:    APP_GOLD,
  RESOLVED:  APP_GREEN,
  INACTIVE:  APP_BORDER,
  REMISSION: APP_GREEN,
};

const FILTERS: { label: string; value: FilterValue }[] = [
  { label: "All",      value: "ALL"      },
  { label: "Active",   value: "ACTIVE"   },
  { label: "Resolved", value: "RESOLVED" },
];

export function ConditionsSection() {
  const [conditions, setConditions]     = useState<Condition[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError]               = useState<string | null>(null);
  const [filter, setFilter]             = useState<FilterValue>("ALL");
  const [expanded, setExpanded]         = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    setError(null);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setConditions(SEED_CONDITIONS);
      } else {
        // const r = await fetchConditions({ status: filter !== "ALL" ? filter : undefined });
        // setConditions(r.items);
        setConditions([]);
      }
    } catch {
      setError("Could not load conditions. Pull down to retry.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const activeCount   = conditions.filter((c) => c.status === "ACTIVE").length;
  const resolvedCount = conditions.filter((c) => c.status === "RESOLVED").length;
  const displayed     = filter === "ALL" ? conditions : conditions.filter((c) => c.status === filter);

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

      {/* Summary strip — neutral uniform background, numbers tell the story */}
      <View style={s.summaryStrip}>
        <View style={s.summaryCard}>
          <Text style={[s.summaryNum, { color: APP_GOLD }]}>{activeCount}</Text>
          <Text style={s.summaryLbl}>Active</Text>
        </View>
        <View style={s.summaryCard}>
          <Text style={[s.summaryNum, { color: APP_GREEN_DARK }]}>{resolvedCount}</Text>
          <Text style={s.summaryLbl}>Resolved</Text>
        </View>
        <View style={s.summaryCard}>
          <Text style={[s.summaryNum, { color: APP_TEXT_2 }]}>{conditions.length}</Text>
          <Text style={s.summaryLbl}>Total</Text>
        </View>
      </View>

      {/* Filter pills — text only */}
      <View style={s.filterRow}>
        {FILTERS.map((f) => (
          <Pressable
            key={f.value}
            onPress={() => setFilter(f.value)}
            style={[s.filterPill, filter === f.value && s.filterPillActive]}
          >
            <Text style={[s.filterPillText, filter === f.value && s.filterPillTextActive]}>
              {f.label}
            </Text>
          </Pressable>
        ))}
      </View>

      {displayed.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="checkmark-circle-outline" size={52} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyTitle}>No conditions found</Text>
          <Text style={s.emptySub}>
            {filter === "ALL"
              ? "Your medical history will appear here."
              : `No ${filter.toLowerCase()} conditions on record.`}
          </Text>
        </View>
      ) : (
        displayed.map((c) => {
          const isOpen     = expanded === c.id;
          const chip       = STATUS_CHIP[c.status] ?? STATUS_CHIP.INACTIVE;
          const borderColor= STATUS_BORDER[c.status] ?? APP_BORDER;
          return (
            <Pressable
              key={c.id}
              onPress={() => setExpanded(isOpen ? null : c.id)}
              style={[s.card, { borderLeftColor: borderColor }]}
            >
              <View style={s.cardTop}>
                {/* Uniform teal icon — status shown only via chip + left border */}
                <View style={s.cardIcon}>
                  <Ionicons name="pulse-outline" size={18} color={APP_GREEN} />
                </View>

                <View style={s.cardMain}>
                  <View style={s.cardTitleRow}>
                    <Text style={s.conditionName} numberOfLines={1}>{c.name}</Text>
                    {c.code ? <Text style={s.icdCode}>{c.code}</Text> : null}
                  </View>
                  {c.category ? <Text style={s.categoryText}>{c.category}</Text> : null}
                </View>

                <View style={s.cardRight}>
                  <View style={[s.statusChip, { backgroundColor: chip.bg }]}>
                    <Text style={[s.statusText, { color: chip.color }]}>
                      {STATUS_LABEL[c.status]}
                    </Text>
                  </View>
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
                  {c.onsetDate ? <DetailRow label="Onset" value={new Date(c.onsetDate).toLocaleDateString()} /> : null}
                  {c.resolvedAt ? <DetailRow label="Resolved" value={new Date(c.resolvedAt).toLocaleDateString()} /> : null}
                  <DetailRow label="Recorded" value={new Date(c.recordedAt).toLocaleDateString()} />
                  {c.recordedBy ? <DetailRow label="Clinician" value={c.recordedBy} /> : null}
                  {c.facilityName ? <DetailRow label="Facility" value={c.facilityName} /> : null}
                  {c.severity ? <DetailRow label="Severity" value={c.severity} /> : null}
                  {c.notes ? (
                    <View style={s.noteBox}>
                      <Text style={s.noteText}>{c.notes}</Text>
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

  /* Summary — uniform white cards, colour only on the number */
  summaryStrip: { flexDirection: "row", gap: 10 },
  summaryCard: {
    flex: 1, backgroundColor: APP_SURFACE, borderRadius: 14,
    padding: 14, alignItems: "center", gap: 4,
    shadowColor: "#000", shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04, shadowRadius: 3, elevation: 2,
  },
  summaryNum: { fontSize: 24, fontWeight: "800" },
  summaryLbl: { fontSize: 11, fontWeight: "600", color: APP_TEXT_2 },

  /* Filters — text only */
  filterRow: { flexDirection: "row", gap: 8 },
  filterPill: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, backgroundColor: "#EEF2F2" },
  filterPillActive: { backgroundColor: APP_GREEN_XLIGHT, borderWidth: 1, borderColor: APP_GREEN_LIGHT },
  filterPillText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_2 },
  filterPillTextActive: { color: APP_GREEN, fontWeight: "700" },

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
  cardTitleRow: { flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" },
  cardRight: { alignItems: "flex-end" },
  conditionName: { fontSize: 15, fontWeight: "600", color: APP_TEXT, flex: 1 },
  icdCode: {
    fontSize: 11, fontWeight: "600", color: APP_TEXT_3,
    backgroundColor: "#F3F4F6", paddingHorizontal: 6, paddingVertical: 2, borderRadius: 6,
  },
  categoryText: { fontSize: 12, color: APP_TEXT_3 },
  statusChip: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 10 },
  statusText: { fontSize: 11, fontWeight: "700" },

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
