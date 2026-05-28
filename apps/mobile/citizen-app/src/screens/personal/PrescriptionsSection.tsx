import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { fetchPrescriptions, requestRefill } from "../../services/prescriptionService";
import type { Prescription, PrescriptionStatus } from "../../types";
import { USE_SEED_DATA, SEED_PRESCRIPTIONS } from "../../data/seedHealthRecords";
import { useToast } from "../../components/Toast";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

type Filter = "ALL" | "ACTIVE" | "PAST";

const STATUS_CFG: Record<PrescriptionStatus, { color: string; bg: string; label: string }> = {
  ACTIVE:     { color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT,  label: "Active"     },
  PENDING:    { color: APP_GOLD,       bg: APP_GOLD_LIGHT,   label: "Pending"    },
  DISPENSED:  { color: APP_TEXT_2,     bg: "#EEF2F2",        label: "Dispensed"  },
  EXPIRED:    { color: APP_TEXT_3,     bg: "#F3F4F6",        label: "Expired"    },
  CANCELLED:  { color: APP_RED,        bg: APP_RED_LIGHT,    label: "Cancelled"  },
};

export function PrescriptionsSection() {
  const toast    = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const [prescriptions, setPrescriptions] = useState<Prescription[]>([]);
  const [isLoading, setIsLoading]         = useState(true);
  const [isRefreshing, setIsRefreshing]   = useState(false);
  const [filter, setFilter]               = useState<Filter>("ALL");
  const [expanded, setExpanded]           = useState<string | null>(null);
  const [refilling, setRefilling]         = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setPrescriptions(SEED_PRESCRIPTIONS);
      } else {
        const r = await fetchPrescriptions({ size: 50 });
        setPrescriptions(r.items);
      }
    } catch {
      toastRef.current.error("Could not load prescriptions.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const handleRefill = useCallback((rx: Prescription) => {
    Alert.alert(
      "Request Refill",
      `Request a refill for ${rx.medicationName}?`,
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Request Refill",
          onPress: async () => {
            setRefilling(rx.id);
            try {
              await requestRefill(rx.id);
              toastRef.current.success(`Refill requested for ${rx.medicationName}.`);
              void load(true);
            } catch {
              toastRef.current.error("Could not request refill. Try again.");
            } finally {
              setRefilling(null);
            }
          },
        },
      ],
    );
  }, [load]);

  const active  = prescriptions.filter((r) => r.status === "ACTIVE" || r.status === "PENDING");
  const past    = prescriptions.filter((r) => r.status === "DISPENSED" || r.status === "EXPIRED" || r.status === "CANCELLED");
  const displayed = filter === "ACTIVE" ? active : filter === "PAST" ? past : prescriptions;

  const lowRefills = active.filter((r) => r.refillsRemaining === 0 && r.status === "ACTIVE").length;

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <View style={s.root}>
      {/* Toolbar */}
      <View style={s.toolbar}>
        {(["ALL", "ACTIVE", "PAST"] as Filter[]).map((f) => (
          <Pressable
            key={f}
            onPress={() => setFilter(f)}
            style={[s.filterPill, filter === f && s.filterPillActive]}
          >
            <Text style={[s.filterPillText, filter === f && s.filterPillTextActive]}>
              {f === "ALL" ? `All (${prescriptions.length})` :
               f === "ACTIVE" ? `Active (${active.length})` :
               `Past (${past.length})`}
            </Text>
          </Pressable>
        ))}
      </View>

      <ScrollView
        style={s.scroll}
        contentContainerStyle={s.content}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
      >
        {/* Low-refill alert */}
        {lowRefills > 0 ? (
          <View style={s.alertBanner}>
            <Ionicons name="alert-circle" size={15} color={APP_GOLD} />
            <Text style={s.alertText}>
              {lowRefills} medication{lowRefills > 1 ? "s" : ""} with no refills remaining — contact your prescriber.
            </Text>
          </View>
        ) : null}

        {/* Summary */}
        <View style={s.summaryStrip}>
          <View style={[s.summaryCard, { backgroundColor: APP_GREEN_LIGHT }]}>
            <Text style={[s.summaryNum, { color: APP_GREEN_DARK }]}>{active.length}</Text>
            <Text style={s.summaryLbl}>Active</Text>
          </View>
          <View style={[s.summaryCard, { backgroundColor: lowRefills > 0 ? APP_GOLD_LIGHT : APP_GREEN_XLIGHT }]}>
            <Text style={[s.summaryNum, { color: lowRefills > 0 ? APP_GOLD : APP_GREEN }]}>{lowRefills}</Text>
            <Text style={s.summaryLbl}>Refills Needed</Text>
          </View>
          <View style={[s.summaryCard, { backgroundColor: "#EEF2F2" }]}>
            <Text style={[s.summaryNum, { color: APP_TEXT_2 }]}>{past.length}</Text>
            <Text style={s.summaryLbl}>Past</Text>
          </View>
        </View>

        {displayed.length === 0 ? (
          <View style={s.empty}>
            <Ionicons name="receipt-outline" size={52} color={APP_GREEN_LIGHT} />
            <Text style={s.emptyTitle}>No prescriptions</Text>
            <Text style={s.emptySub}>Your medications will appear here after a consultation.</Text>
          </View>
        ) : (
          displayed.map((rx) => {
            const isOpen = expanded === rx.id;
            const cfg    = STATUS_CFG[rx.status] ?? STATUS_CFG.ACTIVE;
            const noRefills = rx.refillsRemaining === 0 && rx.status === "ACTIVE";
            return (
              <Pressable
                key={rx.id}
                onPress={() => setExpanded(isOpen ? null : rx.id)}
                style={[s.card, { borderLeftColor: cfg.color }]}
              >
                <View style={s.cardTop}>
                  <View style={[s.medIcon, { backgroundColor: cfg.bg }]}>
                    <Ionicons name="medical" size={20} color={cfg.color} />
                  </View>
                  <View style={s.cardMain}>
                    <Text style={s.medName}>{rx.medicationName}</Text>
                    <Text style={s.medDosage}>{rx.dosage} · {rx.frequency}</Text>
                    <View style={s.metaRow}>
                      <Ionicons name="person-circle-outline" size={12} color={APP_TEXT_3} />
                      <Text style={s.metaText}>{rx.prescribedBy}</Text>
                    </View>
                  </View>
                  <View style={s.cardRight}>
                    <View style={[s.statusChip, { backgroundColor: cfg.bg }]}>
                      <Text style={[s.statusText, { color: cfg.color }]}>{cfg.label}</Text>
                    </View>
                    {rx.status === "ACTIVE" ? (
                      <View style={[s.refillBadge, noRefills ? s.refillBadgeEmpty : s.refillBadgeGood]}>
                        <Text style={[s.refillBadgeText, { color: noRefills ? APP_RED : APP_GREEN_DARK }]}>
                          {noRefills ? "No refills" : `${rx.refillsRemaining} refill${rx.refillsRemaining !== 1 ? "s" : ""}`}
                        </Text>
                      </View>
                    ) : null}
                    <Ionicons name={isOpen ? "chevron-up" : "chevron-down"} size={14} color={APP_TEXT_3} style={{ marginTop: 4 }} />
                  </View>
                </View>

                {isOpen ? (
                  <View style={s.expanded}>
                    <DetailRow label="Facility"    value={rx.facilityName} />
                    <DetailRow label="Duration"    value={rx.duration} />
                    <DetailRow label="Quantity"    value={`${rx.quantity} units`} />
                    {rx.lastFilledAt ? <DetailRow label="Last filled" value={new Date(rx.lastFilledAt).toLocaleDateString()} /> : null}
                    <DetailRow label="Prescribed"  value={new Date(rx.createdAt).toLocaleDateString()} />

                    {rx.status === "ACTIVE" ? (
                      <Pressable
                        onPress={() => handleRefill(rx)}
                        disabled={refilling === rx.id}
                        style={[s.refillBtn, refilling === rx.id && s.refillBtnDisabled]}
                      >
                        {refilling === rx.id ? <LoadingSpinner size="sm" /> : (
                          <>
                            <Ionicons name="refresh-circle-outline" size={16} color="#FFFFFF" />
                            <Text style={s.refillBtnText}>Request Refill</Text>
                          </>
                        )}
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
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },
  toolbar: {
    flexDirection: "row", gap: 8, paddingHorizontal: 16, paddingVertical: 10,
    backgroundColor: APP_SURFACE,
    borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER,
  },
  filterPill: { paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20, backgroundColor: "#EEF2F2" },
  filterPillActive: { backgroundColor: APP_GREEN_XLIGHT, borderWidth: 1, borderColor: APP_GREEN_LIGHT },
  filterPillText: { fontSize: 12, fontWeight: "500", color: APP_TEXT_2 },
  filterPillTextActive: { color: APP_GREEN, fontWeight: "700" },
  scroll: { flex: 1 },
  content: { padding: 16, gap: 12, paddingBottom: 40 },

  alertBanner: {
    flexDirection: "row", alignItems: "flex-start", gap: 8,
    backgroundColor: APP_GOLD_LIGHT, borderRadius: 12, padding: 12,
    borderLeftWidth: 3, borderLeftColor: APP_GOLD,
  },
  alertText: { fontSize: 13, color: APP_GOLD, flex: 1, lineHeight: 18 },

  summaryStrip: { flexDirection: "row", gap: 10 },
  summaryCard: { flex: 1, borderRadius: 14, padding: 14, alignItems: "center", gap: 4 },
  summaryNum: { fontSize: 22, fontWeight: "800" },
  summaryLbl: { fontSize: 10, fontWeight: "600", color: APP_TEXT_2, textAlign: "center" },

  card: {
    backgroundColor: APP_SURFACE, borderRadius: 16, borderLeftWidth: 4,
    overflow: "hidden", shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 6, elevation: 3,
  },
  cardTop: { flexDirection: "row", alignItems: "center", gap: 12, padding: 14 },
  medIcon: { width: 44, height: 44, borderRadius: 13, alignItems: "center", justifyContent: "center" },
  cardMain: { flex: 1, gap: 3 },
  cardRight: { alignItems: "flex-end", gap: 5 },
  medName: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  medDosage: { fontSize: 13, color: APP_TEXT_2 },
  metaRow: { flexDirection: "row", alignItems: "center", gap: 4 },
  metaText: { fontSize: 12, color: APP_TEXT_3 },
  statusChip: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10 },
  statusText: { fontSize: 11, fontWeight: "700" },
  refillBadge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10 },
  refillBadgeGood: { backgroundColor: APP_GREEN_XLIGHT },
  refillBadgeEmpty: { backgroundColor: APP_RED_LIGHT },
  refillBadgeText: { fontSize: 11, fontWeight: "600" },

  expanded: {
    paddingHorizontal: 14, paddingBottom: 14, gap: 6,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  detailRow: { flexDirection: "row", justifyContent: "space-between", paddingTop: 6 },
  detailLabel: { fontSize: 12, color: APP_TEXT_3, width: 100 },
  detailValue: { fontSize: 13, color: APP_TEXT, flex: 1, textAlign: "right" },
  refillBtn: {
    flexDirection: "row", alignItems: "center", justifyContent: "center",
    gap: 8, backgroundColor: APP_GREEN, paddingVertical: 12, borderRadius: 12, marginTop: 6,
  },
  refillBtnDisabled: { opacity: 0.5 },
  refillBtnText: { fontSize: 14, fontWeight: "700", color: "#FFFFFF" },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
