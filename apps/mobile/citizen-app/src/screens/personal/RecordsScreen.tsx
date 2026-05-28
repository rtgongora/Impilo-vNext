import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { getRecords } from "../../services/recordsService";
import type { MedicalRecord, RecordType } from "../../types";
import { USE_SEED_DATA, SEED_RECORDS } from "../../data/seedHealthRecords";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

type FilterType = "ALL" | RecordType;

// Filter chips — labels only, no per-chip icons (text is clear enough)
const FILTERS: { label: string; value: FilterType }[] = [
  { label: "All",       value: "ALL"               },
  { label: "Lab",       value: "LAB_REPORT"        },
  { label: "Notes",     value: "CLINICAL_NOTE"     },
  { label: "Discharge", value: "DISCHARGE_SUMMARY" },
  { label: "Imaging",   value: "IMAGING"           },
];

// Consistent icon treatment — all outline, all teal (single colour system)
// Type label kept for text differentiation; no per-type colour variation
const TYPE_CFG: Record<RecordType, { label: string; icon: React.ComponentProps<typeof Ionicons>["name"] }> = {
  LAB_REPORT:        { label: "Lab Report",    icon: "flask-outline"          },
  CLINICAL_NOTE:     { label: "Clinical Note", icon: "document-text-outline"  },
  DISCHARGE_SUMMARY: { label: "Discharge",     icon: "document-outline"       },
  IMAGING:           { label: "Imaging",       icon: "scan-outline"           },
};

export function RecordsScreen() {
  const [records, setRecords]           = useState<MedicalRecord[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError]               = useState<string | null>(null);
  const [filter, setFilter]             = useState<FilterType>("ALL");
  const [expanded, setExpanded]         = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    setError(null);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setRecords(SEED_RECORDS);
      } else {
        const params: { type?: RecordType; size?: number } = { size: 50 };
        if (filter !== "ALL") params.type = filter;
        const result = await getRecords(params);
        setRecords(result.items);
      }
    } catch {
      setError("Could not load records. Pull down to retry.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [filter]);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const displayed = filter === "ALL" ? records : records.filter((r) => r.type === filter);

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <View style={s.root}>
      {/* Filter bar — text chips only, no icons */}
      <View style={s.filterBar}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={s.filterRow}
        >
          {FILTERS.map((f) => (
            <Pressable
              key={f.value}
              onPress={() => setFilter(f.value)}
              style={[s.filterChip, filter === f.value && s.filterChipActive]}
            >
              <Text style={[s.filterChipText, filter === f.value && s.filterChipTextActive]}>
                {f.label}
              </Text>
            </Pressable>
          ))}
        </ScrollView>
      </View>

      <ScrollView
        style={s.scroll}
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

        {/* Record count */}
        {displayed.length > 0 ? (
          <Text style={s.countLabel}>
            {displayed.length} record{displayed.length !== 1 ? "s" : ""}
            {filter !== "ALL" ? ` · ${FILTERS.find((f) => f.value === filter)?.label}` : ""}
          </Text>
        ) : null}

        {displayed.length === 0 ? (
          <View style={s.empty}>
            <Ionicons name="folder-open-outline" size={52} color={APP_GREEN_LIGHT} />
            <Text style={s.emptyTitle}>No records found</Text>
            <Text style={s.emptySub}>
              {filter === "ALL"
                ? "Your medical records will appear here."
                : `No ${(FILTERS.find((f) => f.value === filter)?.label ?? "").toLowerCase()} records on file.`}
            </Text>
          </View>
        ) : (
          displayed.map((rec) => {
            const isOpen = expanded === rec.id;
            const cfg    = TYPE_CFG[rec.type] ?? TYPE_CFG.CLINICAL_NOTE;
            return (
              <Pressable
                key={rec.id}
                onPress={() => setExpanded(isOpen ? null : rec.id)}
                style={({ pressed }) => [s.card, pressed && { opacity: 0.94 }]}
              >
                <View style={s.cardTop}>
                  {/* Consistent icon: single teal background, single teal colour */}
                  <View style={s.cardIcon}>
                    <Ionicons name={cfg.icon} size={19} color={APP_GREEN} />
                  </View>

                  <View style={s.cardMain}>
                    <Text style={s.recTitle} numberOfLines={isOpen ? undefined : 2}>
                      {rec.title}
                    </Text>
                    <View style={s.recMeta}>
                      <Text style={s.recDate}>
                        {new Date(rec.date).toLocaleDateString([], { day: "numeric", month: "short", year: "numeric" })}
                      </Text>
                      <Text style={s.recDot}>·</Text>
                      <Text style={s.recProvider} numberOfLines={1}>{rec.provider}</Text>
                    </View>
                  </View>

                  <View style={s.cardRight}>
                    {/* Type label — single teal pill, no multi-colour variation */}
                    <View style={s.typePill}>
                      <Text style={s.typePillText}>{cfg.label}</Text>
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
                    <View style={s.detailRow}>
                      <Text style={s.detailLabel}>Facility</Text>
                      <Text style={s.detailValue}>{rec.facilityName}</Text>
                    </View>
                    <View style={s.detailRow}>
                      <Text style={s.detailLabel}>Provider</Text>
                      <Text style={s.detailValue}>{rec.provider}</Text>
                    </View>
                    {rec.summary ? (
                      <View style={s.summaryBox}>
                        <Text style={s.summaryBoxLabel}>Summary</Text>
                        <Text style={s.summaryBoxText}>{rec.summary}</Text>
                      </View>
                    ) : null}
                    {rec.documentUrl ? (
                      <Pressable style={s.viewDocBtn}>
                        <Ionicons name="open-outline" size={14} color={APP_GREEN} />
                        <Text style={s.viewDocText}>View document</Text>
                      </Pressable>
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

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  centred: { flex: 1, alignItems: "center", justifyContent: "center", padding: 40 },
  errorBanner: {
    flexDirection: "row", alignItems: "center", gap: 8,
    backgroundColor: APP_RED_LIGHT, padding: 12, borderRadius: 12,
  },
  errorText: { fontSize: 13, color: APP_RED, flex: 1 },

  /* Filter bar — text chips only */
  filterBar: {
    backgroundColor: APP_SURFACE,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: APP_BORDER,
  },
  filterRow: { paddingHorizontal: 16, paddingVertical: 10, gap: 8 },
  filterChip: {
    paddingHorizontal: 16, paddingVertical: 8,
    borderRadius: 20, backgroundColor: "#EEF2F2",
  },
  filterChipActive: {
    backgroundColor: APP_GREEN_XLIGHT,
    borderWidth: 1, borderColor: APP_GREEN_LIGHT,
  },
  filterChipText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_2 },
  filterChipTextActive: { color: APP_GREEN, fontWeight: "700" },

  countLabel: {
    fontSize: 12, fontWeight: "600", color: APP_TEXT_3,
    paddingHorizontal: 2,
  },

  scroll: { flex: 1 },
  content: { padding: 16, gap: 10, paddingBottom: 40 },

  /* Record card */
  card: {
    backgroundColor: APP_SURFACE,
    borderRadius: 14,
    overflow: "hidden",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 5,
    elevation: 2,
  },
  cardTop: { flexDirection: "row", alignItems: "flex-start", gap: 12, padding: 14 },

  /* Single consistent icon treatment */
  cardIcon: {
    width: 38,
    height: 38,
    borderRadius: 10,
    backgroundColor: APP_GREEN_XLIGHT,
    alignItems: "center",
    justifyContent: "center",
  },

  cardMain: { flex: 1 },
  cardRight: { alignItems: "flex-end" },
  recTitle: { fontSize: 14, fontWeight: "600", color: APP_TEXT, marginBottom: 5, lineHeight: 19 },
  recMeta: { flexDirection: "row", alignItems: "center", gap: 5 },
  recDate: { fontSize: 12, color: APP_TEXT_3 },
  recDot: { fontSize: 12, color: APP_TEXT_3 },
  recProvider: { fontSize: 12, color: APP_TEXT_2, flex: 1 },

  /* Single teal type pill — no per-type colour variation */
  typePill: {
    backgroundColor: APP_GREEN_XLIGHT,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 10,
  },
  typePillText: { fontSize: 11, fontWeight: "600", color: APP_GREEN_DARK },

  cardExpanded: {
    paddingHorizontal: 14, paddingBottom: 14, gap: 8,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  detailRow: { flexDirection: "row", justifyContent: "space-between", paddingTop: 6 },
  detailLabel: { fontSize: 12, color: APP_TEXT_3, width: 90 },
  detailValue: { fontSize: 13, color: APP_TEXT, flex: 1, textAlign: "right" },
  summaryBox: {
    backgroundColor: APP_GREEN_XLIGHT,
    borderRadius: 10, padding: 12, gap: 5, marginTop: 4,
  },
  summaryBoxLabel: {
    fontSize: 11, fontWeight: "700", color: APP_GREEN_DARK,
    textTransform: "uppercase", letterSpacing: 0.5,
  },
  summaryBoxText: { fontSize: 13, color: APP_TEXT_2, lineHeight: 19 },
  viewDocBtn: { flexDirection: "row", alignItems: "center", gap: 6, paddingTop: 8 },
  viewDocText: { fontSize: 13, fontWeight: "600", color: APP_GREEN },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: {
    fontSize: 13, color: APP_TEXT_3,
    textAlign: "center", paddingHorizontal: 32, lineHeight: 19,
  },
});
