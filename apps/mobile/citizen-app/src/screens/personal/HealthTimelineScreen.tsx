import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { getTimeline } from "../../services/healthTimelineService";
import type { TimelineEntry, TimelineEntryType } from "../../types";
import { USE_SEED_DATA, SEED_TIMELINE } from "../../data/seedHealthRecords";
import {
  APP_GREEN, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

// Consistent icon set — all outline variants, differentiated by shape not colour.
// Colour is applied uniformly (teal for past, gold for upcoming).
const TYPE_CFG: Record<TimelineEntryType, {
  icon: React.ComponentProps<typeof Ionicons>["name"];
  label: string;
}> = {
  ENCOUNTER:    { icon: "business-outline",          label: "Encounter"    },
  LAB_RESULT:   { icon: "flask-outline",             label: "Lab Result"   },
  PRESCRIPTION: { icon: "medical-outline",           label: "Prescription" },
  APPOINTMENT:  { icon: "calendar-outline",          label: "Appointment"  },
  IMMUNIZATION: { icon: "shield-checkmark-outline",  label: "Vaccine"      },
};

type SortOrder = "desc" | "asc";

function groupByMonth(
  entries: TimelineEntry[],
  order: SortOrder,
): { month: string; items: TimelineEntry[] }[] {
  const map = new Map<string, TimelineEntry[]>();
  const sorted = [...entries].sort((a, b) => {
    const diff = new Date(a.date).getTime() - new Date(b.date).getTime();
    return order === "desc" ? -diff : diff;
  });
  for (const entry of sorted) {
    const key = new Date(entry.date).toLocaleDateString([], { month: "long", year: "numeric" });
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(entry);
  }
  return Array.from(map.entries()).map(([month, items]) => ({ month, items }));
}

export function HealthTimelineScreen() {
  const [entries, setEntries]           = useState<TimelineEntry[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError]               = useState<string | null>(null);
  const [expanded, setExpanded]         = useState<string | null>(null);
  const [sortOrder, setSortOrder]       = useState<SortOrder>("desc");

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    setError(null);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setEntries(SEED_TIMELINE);
      } else {
        const result = await getTimeline({ size: 100 });
        setEntries(result.items);
      }
    } catch {
      setError("Could not load timeline. Pull down to retry.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const isFuture = (date: string) => new Date(date) > new Date();
  const groups   = groupByMonth(entries, sortOrder);

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <View style={s.root}>
      {/* Sort toolbar */}
      {entries.length > 0 ? (
        <View style={s.toolbar}>
          <Text style={s.toolbarLabel}>
            {entries.length} event{entries.length !== 1 ? "s" : ""}
          </Text>
          <Pressable
            onPress={() => setSortOrder((o) => (o === "desc" ? "asc" : "desc"))}
            style={s.sortBtn}
            hitSlop={8}
          >
            <Ionicons
              name={sortOrder === "desc" ? "arrow-down-outline" : "arrow-up-outline"}
              size={14}
              color={APP_GREEN}
            />
            <Text style={s.sortBtnText}>
              {sortOrder === "desc" ? "Newest first" : "Oldest first"}
            </Text>
          </Pressable>
        </View>
      ) : null}

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

        {entries.length === 0 ? (
          <View style={s.empty}>
            <Ionicons name="git-branch-outline" size={52} color={APP_GREEN_LIGHT} />
            <Text style={s.emptyTitle}>No timeline entries</Text>
            <Text style={s.emptySub}>Your health history will build here as you receive care.</Text>
          </View>
        ) : (
          groups.map(({ month, items }) => (
            <View key={month}>
              {/* Month divider */}
              <View style={s.monthHeader}>
                <View style={s.monthLine} />
                <Text style={s.monthLabel}>{month}</Text>
                <View style={s.monthLine} />
              </View>

              {items.map((entry, idx) => {
                const isOpen  = expanded === entry.id;
                const cfg     = TYPE_CFG[entry.type] ?? TYPE_CFG.ENCOUNTER;
                const future  = isFuture(entry.date);
                const isLast  = idx === items.length - 1;

                // Two colour states only: gold for upcoming, teal for past
                const accent  = future ? APP_GOLD      : APP_GREEN;
                const accentBg= future ? APP_GOLD_LIGHT : APP_GREEN_XLIGHT;

                return (
                  <View key={entry.id} style={s.entryWrap}>
                    {/* Rail — single teal dot for past, gold dot for upcoming */}
                    <View style={s.rail}>
                      <View style={[s.railDot, { backgroundColor: accent }]} />
                      {!isLast ? <View style={s.railLine} /> : null}
                    </View>

                    <Pressable
                      onPress={() => setExpanded(isOpen ? null : entry.id)}
                      style={({ pressed }) => [
                        s.entryCard,
                        future && s.entryCardFuture,
                        pressed && { opacity: 0.93 },
                      ]}
                    >
                      <View style={s.entryTop}>
                        {/* Consistent icon: teal for past, gold for upcoming */}
                        <View style={[s.entryIcon, { backgroundColor: accentBg }]}>
                          <Ionicons name={cfg.icon} size={16} color={accent} />
                        </View>

                        <View style={s.entryMain}>
                          <Text style={s.entryTitle} numberOfLines={isOpen ? undefined : 1}>
                            {entry.title}
                          </Text>
                          <Text style={s.entryDate}>
                            {new Date(entry.date).toLocaleDateString([], { day: "numeric", month: "short" })}
                            {entry.facilityName ? ` · ${entry.facilityName}` : ""}
                          </Text>
                        </View>

                        <View style={s.entryRight}>
                          {/* Type pill — single teal for past, gold for upcoming */}
                          <View style={[s.typePill, { backgroundColor: accentBg }]}>
                            <Text style={[s.typePillText, { color: accent }]}>
                              {future ? "Upcoming" : cfg.label}
                            </Text>
                          </View>
                          <Ionicons
                            name={isOpen ? "chevron-up" : "chevron-down"}
                            size={13}
                            color={APP_TEXT_3}
                            style={{ marginTop: 4 }}
                          />
                        </View>
                      </View>

                      {isOpen && (entry.description || entry.provider) ? (
                        <View style={s.entryExpanded}>
                          {entry.description ? (
                            <Text style={s.entryDesc}>{entry.description}</Text>
                          ) : null}
                          {entry.provider ? (
                            <Text style={s.providerText}>{entry.provider}</Text>
                          ) : null}
                        </View>
                      ) : null}
                    </Pressable>
                  </View>
                );
              })}
            </View>
          ))
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
    backgroundColor: APP_RED_LIGHT, padding: 12, borderRadius: 12, marginBottom: 12,
  },
  errorText: { fontSize: 13, color: APP_RED, flex: 1 },

  toolbar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: APP_SURFACE,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: APP_BORDER,
  },
  toolbarLabel: { fontSize: 13, color: APP_TEXT_2 },
  sortBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    backgroundColor: APP_GREEN_XLIGHT,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: APP_GREEN_LIGHT,
  },
  sortBtnText: { fontSize: 13, fontWeight: "600", color: APP_GREEN },

  scroll: { flex: 1 },
  content: { padding: 16, paddingBottom: 40 },

  monthHeader: { flexDirection: "row", alignItems: "center", gap: 10, marginVertical: 16 },
  monthLine: { flex: 1, height: StyleSheet.hairlineWidth, backgroundColor: APP_BORDER },
  monthLabel: {
    fontSize: 11, fontWeight: "700", color: APP_TEXT_2,
    textTransform: "uppercase", letterSpacing: 0.8,
  },

  entryWrap: { flexDirection: "row", gap: 12, marginBottom: 10 },

  rail: { width: 24, alignItems: "center", paddingTop: 14 },
  railDot: { width: 9, height: 9, borderRadius: 5 },
  railLine: { width: 2, flex: 1, backgroundColor: APP_BORDER, marginTop: 4 },

  entryCard: {
    flex: 1, backgroundColor: APP_SURFACE, borderRadius: 14,
    overflow: "hidden",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  entryCardFuture: {
    borderWidth: 1,
    borderColor: APP_GOLD_LIGHT,
  },
  entryTop: { flexDirection: "row", alignItems: "flex-start", gap: 10, padding: 12 },
  entryIcon: { width: 34, height: 34, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  entryMain: { flex: 1 },
  entryRight: { alignItems: "flex-end" },
  entryTitle: { fontSize: 13, fontWeight: "600", color: APP_TEXT, marginBottom: 3 },
  entryDate: { fontSize: 11, color: APP_TEXT_3 },
  typePill: { paddingHorizontal: 7, paddingVertical: 3, borderRadius: 8 },
  typePillText: { fontSize: 10, fontWeight: "700" },

  entryExpanded: {
    paddingHorizontal: 12, paddingBottom: 12, gap: 6,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  entryDesc: { fontSize: 13, color: APP_TEXT_2, lineHeight: 18 },
  providerText: { fontSize: 12, color: APP_TEXT_3 },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
