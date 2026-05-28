import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import type { Immunization } from "../../types";
import { USE_SEED_DATA, SEED_IMMUNIZATIONS } from "../../data/seedHealthRecords";
// When USE_SEED_DATA is false, replace seed block with:
// import { fetchImmunizations } from "../../services/immunizationsService";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

type ImmStatus = NonNullable<Immunization["status"]>;

// Status chip — text label only. Left border is the fast-scan severity indicator.
const STATUS_CFG: Record<ImmStatus, { label: string; color: string; bg: string }> = {
  COMPLETED: { label: "Completed", color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT  },
  SCHEDULED: { label: "Scheduled", color: APP_GREEN,      bg: APP_GREEN_XLIGHT },
  DUE:       { label: "Due",       color: APP_GOLD,       bg: APP_GOLD_LIGHT   },
  OVERDUE:   { label: "Overdue",   color: APP_RED,        bg: APP_RED_LIGHT    },
};

const BORDER_COLOR: Record<ImmStatus, string> = {
  COMPLETED: APP_GREEN,
  SCHEDULED: APP_GREEN,
  DUE:       APP_GOLD,
  OVERDUE:   APP_RED,
};

export function ImmunizationsSection() {
  const [immunizations, setImmunizations] = useState<Immunization[]>([]);
  const [isLoading, setIsLoading]         = useState(true);
  const [isRefreshing, setIsRefreshing]   = useState(false);
  const [error, setError]                 = useState<string | null>(null);
  const [expanded, setExpanded]           = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    setError(null);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setImmunizations(SEED_IMMUNIZATIONS);
      } else {
        // const r = await fetchImmunizations(); setImmunizations(r.items);
        setImmunizations([]);
      }
    } catch {
      setError("Could not load immunizations. Pull down to retry.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const completed = immunizations.filter((i) => i.status === "COMPLETED").length;
  const due       = immunizations.filter((i) => i.status === "DUE" || i.status === "OVERDUE").length;
  const upcoming  = immunizations.filter((i) => i.status === "SCHEDULED").length;

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
          <Text style={[s.summaryNum, { color: APP_GREEN_DARK }]}>{completed}</Text>
          <Text style={s.summaryLbl}>Completed</Text>
        </View>
        <View style={s.summaryCard}>
          <Text style={[s.summaryNum, { color: APP_GOLD }]}>{due}</Text>
          <Text style={s.summaryLbl}>Due / Overdue</Text>
        </View>
        <View style={s.summaryCard}>
          <Text style={[s.summaryNum, { color: APP_GREEN }]}>{upcoming}</Text>
          <Text style={s.summaryLbl}>Scheduled</Text>
        </View>
      </View>

      {due > 0 ? (
        <View style={s.dueBanner}>
          <Ionicons name="time-outline" size={15} color={APP_GOLD} />
          <Text style={s.dueBannerText}>
            {due} vaccine{due > 1 ? "s" : ""} due — contact your facility to schedule.
          </Text>
        </View>
      ) : null}

      {/* Immunization cards */}
      {immunizations.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="shield-checkmark-outline" size={52} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyTitle}>No immunizations recorded</Text>
          <Text style={s.emptySub}>Your vaccination history will appear here.</Text>
        </View>
      ) : (
        immunizations.map((imm) => {
          const isOpen    = expanded === imm.id;
          const status    = imm.status ?? "COMPLETED";
          const cfg       = STATUS_CFG[status] ?? STATUS_CFG.COMPLETED;
          const borderCol = BORDER_COLOR[status] ?? APP_BORDER;
          return (
            <Pressable
              key={imm.id}
              onPress={() => setExpanded(isOpen ? null : imm.id)}
              style={[s.card, { borderLeftColor: borderCol }]}
            >
              <View style={s.cardTop}>
                {/* Uniform teal dose indicator — severity shown via left border + chip text */}
                <View style={s.doseCircle}>
                  <Text style={s.doseNum}>{imm.doseNumber}</Text>
                  {imm.totalDoses ? (
                    <Text style={s.doseDenom}>/{imm.totalDoses}</Text>
                  ) : null}
                </View>

                <View style={s.cardMain}>
                  <Text style={s.vaccineName} numberOfLines={1}>{imm.vaccineName}</Text>
                  <Text style={s.adminDate}>
                    {new Date(imm.administeredAt).toLocaleDateString([], { day: "numeric", month: "short", year: "numeric" })}
                    {imm.facilityName ? ` · ${imm.facilityName}` : ""}
                  </Text>
                </View>

                <View style={s.cardRight}>
                  {/* Status pill — text only, no icon */}
                  <View style={[s.statusChip, { backgroundColor: cfg.bg }]}>
                    <Text style={[s.statusText, { color: cfg.color }]}>{cfg.label}</Text>
                  </View>
                  <Ionicons name={isOpen ? "chevron-up" : "chevron-down"} size={14} color={APP_TEXT_3} style={{ marginTop: 6 }} />
                </View>
              </View>

              {isOpen ? (
                <View style={s.cardExpanded}>
                  {imm.administeredBy ? <DetailRow label="Administered by" value={imm.administeredBy} /> : null}
                  {imm.facilityName ? <DetailRow label="Facility" value={imm.facilityName} /> : null}
                  {imm.lotNumber ? <DetailRow label="Lot number" value={imm.lotNumber} /> : null}
                  {imm.expirationDate ? <DetailRow label="Certificate expires" value={new Date(imm.expirationDate).toLocaleDateString()} /> : null}
                  {imm.nextDoseDate ? (
                    <View style={s.nextDoseBox}>
                      <Text style={s.nextDoseText}>
                        Next dose: {new Date(imm.nextDoseDate).toLocaleDateString([], { day: "numeric", month: "long", year: "numeric" })}
                      </Text>
                    </View>
                  ) : null}
                  {imm.notes ? (
                    <View style={s.noteBox}>
                      <Text style={s.noteText}>{imm.notes}</Text>
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
  errorBanner: { flexDirection: "row", alignItems: "center", gap: 8, backgroundColor: APP_RED_LIGHT, padding: 12, borderRadius: 12 },
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
  summaryLbl: { fontSize: 10, fontWeight: "600", color: APP_TEXT_2, textAlign: "center" },

  dueBanner: {
    flexDirection: "row", alignItems: "center", gap: 8,
    backgroundColor: APP_GOLD_LIGHT, borderRadius: 12, padding: 12,
    borderLeftWidth: 3, borderLeftColor: APP_GOLD,
  },
  dueBannerText: { fontSize: 13, color: APP_GOLD, flex: 1 },

  card: {
    backgroundColor: APP_SURFACE, borderRadius: 16, borderLeftWidth: 4,
    overflow: "hidden", shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 6, elevation: 3,
  },
  cardTop: { flexDirection: "row", alignItems: "center", gap: 12, padding: 14 },

  /* Uniform teal dose circle */
  doseCircle: {
    width: 48, height: 48, borderRadius: 14,
    backgroundColor: APP_GREEN_XLIGHT,
    alignItems: "center", justifyContent: "center", flexDirection: "row",
  },
  doseNum: { fontSize: 18, fontWeight: "800", color: APP_GREEN },
  doseDenom: { fontSize: 12, fontWeight: "600", color: APP_GREEN, opacity: 0.7 },

  cardMain: { flex: 1 },
  cardRight: { alignItems: "flex-end" },
  vaccineName: { fontSize: 14, fontWeight: "700", color: APP_TEXT, marginBottom: 4 },
  adminDate: { fontSize: 12, color: APP_TEXT_2 },

  /* Status chip — text only, no icon */
  statusChip: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 10 },
  statusText: { fontSize: 11, fontWeight: "700" },

  cardExpanded: {
    paddingHorizontal: 14, paddingBottom: 14, gap: 6,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  detailRow: { flexDirection: "row", justifyContent: "space-between", paddingTop: 8 },
  detailLabel: { fontSize: 12, color: APP_TEXT_3, width: 130 },
  detailValue: { fontSize: 13, color: APP_TEXT, flex: 1, textAlign: "right" },
  /* Next dose and notes — text only, no icons */
  nextDoseBox: { backgroundColor: APP_GREEN_XLIGHT, borderRadius: 10, padding: 10, marginTop: 4 },
  nextDoseText: { fontSize: 13, color: APP_GREEN_DARK, fontWeight: "600" },
  noteBox: { backgroundColor: APP_GREEN_XLIGHT, borderRadius: 10, padding: 10, marginTop: 4 },
  noteText: { fontSize: 12, color: APP_TEXT_2, lineHeight: 17 },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
