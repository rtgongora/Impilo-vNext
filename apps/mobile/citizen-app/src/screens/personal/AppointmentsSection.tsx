import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  View, Text, ScrollView, Pressable, StyleSheet,
  RefreshControl, Alert, KeyboardAvoidingView, Platform,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner, TextField, Select } from "@impilo/mobile-design-system";
import { fetchAppointments, requestAppointment, cancelAppointment } from "../../services/appointmentService";
import type { Appointment, AppointmentStatus } from "../../types";
import { USE_SEED_DATA, SEED_APPOINTMENTS } from "../../data/seedHealthRecords";
import { useToast } from "../../components/Toast";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

type Filter = "ALL" | "UPCOMING" | "COMPLETED" | "CANCELLED";

const STATUS_CFG: Record<AppointmentStatus, { color: string; bg: string; label: string }> = {
  SCHEDULED:   { color: APP_GREEN,      bg: APP_GREEN_LIGHT,  label: "Scheduled"   },
  CONFIRMED:   { color: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT, label: "Confirmed"   },
  CHECKED_IN:  { color: APP_GREEN,      bg: APP_GREEN_LIGHT,  label: "Checked In"  },
  IN_PROGRESS: { color: APP_GOLD,       bg: APP_GOLD_LIGHT,   label: "In Progress" },
  COMPLETED:   { color: APP_TEXT_2,     bg: "#EEF2F2",        label: "Completed"   },
  CANCELLED:   { color: APP_RED,        bg: APP_RED_LIGHT,    label: "Cancelled"   },
  NO_SHOW:     { color: APP_RED,        bg: APP_RED_LIGHT,    label: "No Show"     },
};

const UPCOMING_STATUSES: AppointmentStatus[] = ["SCHEDULED", "CONFIRMED", "CHECKED_IN", "IN_PROGRESS"];

const APPOINTMENT_TYPES = [
  { label: "General Practice",    value: "GENERAL_PRACTICE"    },
  { label: "Specialist Consult",  value: "SPECIALIST_CONSULT"  },
  { label: "Follow-up",           value: "FOLLOW_UP"           },
  { label: "Procedure",           value: "PROCEDURE"           },
  { label: "Lab / Pathology",     value: "LAB_PATHOLOGY"       },
];

export function AppointmentsSection() {
  const toast    = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [filter, setFilter]             = useState<Filter>("ALL");
  const [expanded, setExpanded]         = useState<string | null>(null);
  const [showBook, setShowBook]         = useState(false);
  const [submitting, setSubmitting]     = useState(false);

  // Booking form
  const [facilityId, setFacilityId]         = useState("");
  const [appointmentType, setAppointmentType] = useState("GENERAL_PRACTICE");
  const [preferredDate, setPreferredDate]   = useState("");
  const [reason, setReason]                 = useState("");

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setAppointments(SEED_APPOINTMENTS);
      } else {
        const r = await fetchAppointments({ size: 50 });
        setAppointments(r.items);
      }
    } catch {
      toastRef.current.error("Could not load appointments.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const handleBook = useCallback(async () => {
    if (!facilityId.trim()) { toastRef.current.warning("Please enter a facility ID."); return; }
    setSubmitting(true);
    try {
      await requestAppointment({ facilityId, appointmentType, preferredDate, reason });
      setShowBook(false); setFacilityId(""); setReason(""); setPreferredDate("");
      toastRef.current.success("Appointment requested!");
      void load(true);
    } catch {
      toastRef.current.error("Could not request appointment. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }, [facilityId, appointmentType, preferredDate, reason, load]);

  const handleCancel = useCallback((appt: Appointment) => {
    Alert.alert(
      "Cancel Appointment",
      `Cancel your ${appt.appointmentType} on ${new Date(appt.scheduledAt).toLocaleDateString()}?`,
      [
        { text: "Keep it", style: "cancel" },
        {
          text: "Cancel Appointment", style: "destructive",
          onPress: async () => {
            try {
              await cancelAppointment(appt.id);
              toastRef.current.success("Appointment cancelled.");
              void load(true);
            } catch {
              toastRef.current.error("Could not cancel. Please try again.");
            }
          },
        },
      ],
    );
  }, [load]);

  const upcoming   = appointments.filter((a) => UPCOMING_STATUSES.includes(a.status));
  const completed  = appointments.filter((a) => a.status === "COMPLETED");
  const cancelled  = appointments.filter((a) => a.status === "CANCELLED" || a.status === "NO_SHOW");

  const displayed =
    filter === "UPCOMING"  ? upcoming  :
    filter === "COMPLETED" ? completed :
    filter === "CANCELLED" ? cancelled :
    appointments;

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <View style={s.root}>
      {/* Toolbar */}
      <View style={s.toolbar}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={s.filterRow}>
          {(["ALL", "UPCOMING", "COMPLETED", "CANCELLED"] as Filter[]).map((f) => (
            <Pressable
              key={f}
              onPress={() => setFilter(f)}
              style={[s.filterPill, filter === f && s.filterPillActive]}
            >
              <Text style={[s.filterPillText, filter === f && s.filterPillTextActive]}>
                {f === "ALL" ? `All (${appointments.length})` :
                 f === "UPCOMING" ? `Upcoming (${upcoming.length})` :
                 f === "COMPLETED" ? `Completed (${completed.length})` :
                 `Cancelled (${cancelled.length})`}
              </Text>
            </Pressable>
          ))}
        </ScrollView>
        <Pressable
          onPress={() => setShowBook(!showBook)}
          style={[s.bookBtn, showBook && s.bookBtnActive]}
          hitSlop={6}
        >
          <Ionicons name={showBook ? "close" : "add"} size={16} color={showBook ? APP_TEXT_2 : "#FFFFFF"} />
          {!showBook ? <Text style={s.bookBtnText}>Book</Text> : null}
        </Pressable>
      </View>

      <ScrollView
        style={s.scroll}
        contentContainerStyle={s.content}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
      >
        {/* Booking form */}
        {showBook ? (
          <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined}>
            <View style={s.formCard}>
              <Text style={s.formTitle}>Request Appointment</Text>
              <TextField label="Facility ID" value={facilityId} onChange={setFacilityId} placeholder="Enter facility ID" />
              <Select
                label="Appointment Type"
                value={appointmentType}
                onChange={setAppointmentType}
                options={APPOINTMENT_TYPES}
              />
              <TextField label="Preferred Date (optional)" value={preferredDate} onChange={setPreferredDate} placeholder="e.g. 2026-07-15" />
              <TextField label="Reason" value={reason} onChange={setReason} placeholder="Brief reason for visit" />
              <Pressable
                onPress={handleBook}
                disabled={submitting}
                style={[s.submitBtn, submitting && s.submitBtnDisabled]}
              >
                {submitting ? <LoadingSpinner size="sm" /> : (
                  <>
                    <Ionicons name="calendar-outline" size={16} color="#FFFFFF" />
                    <Text style={s.submitBtnText}>Request Appointment</Text>
                  </>
                )}
              </Pressable>
            </View>
          </KeyboardAvoidingView>
        ) : null}

        {/* List */}
        {displayed.length === 0 ? (
          <View style={s.empty}>
            <Ionicons name="calendar-outline" size={52} color={APP_GREEN_LIGHT} />
            <Text style={s.emptyTitle}>No appointments</Text>
            <Text style={s.emptySub}>
              {filter === "ALL" ? "Tap Book to request your next visit." : `No ${filter.toLowerCase()} appointments.`}
            </Text>
          </View>
        ) : (
          displayed.map((appt) => {
            const isOpen = expanded === appt.id;
            const cfg    = STATUS_CFG[appt.status] ?? STATUS_CFG.SCHEDULED;
            const d      = new Date(appt.scheduledAt);
            const canCancel = UPCOMING_STATUSES.includes(appt.status);
            return (
              <Pressable
                key={appt.id}
                onPress={() => setExpanded(isOpen ? null : appt.id)}
                style={[s.card, { borderLeftColor: cfg.color }]}
              >
                <View style={s.cardTop}>
                  {/* Date block */}
                  <View style={[s.dateBlock, { backgroundColor: cfg.bg }]}>
                    <Text style={[s.dateDay, { color: cfg.color }]}>{d.toLocaleDateString([], { weekday: "short" }).toUpperCase()}</Text>
                    <Text style={[s.dateNum, { color: cfg.color }]}>{d.getDate()}</Text>
                    <Text style={[s.dateMon, { color: cfg.color }]}>{d.toLocaleDateString([], { month: "short" }).toUpperCase()}</Text>
                  </View>

                  <View style={s.cardMain}>
                    <Text style={s.apptType} numberOfLines={1}>{appt.appointmentType}</Text>
                    <View style={s.metaRow}>
                      <Ionicons name="person-circle-outline" size={12} color={APP_TEXT_3} />
                      <Text style={s.metaText}>{appt.providerName ?? "Provider TBC"}</Text>
                    </View>
                    <View style={s.metaRow}>
                      <Ionicons name="business-outline" size={12} color={APP_TEXT_3} />
                      <Text style={s.metaText} numberOfLines={1}>{appt.facilityName}</Text>
                    </View>
                    <View style={s.metaRow}>
                      <Ionicons name="time-outline" size={12} color={APP_TEXT_3} />
                      <Text style={s.metaText}>{d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })} · {appt.duration} min</Text>
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
                    {appt.reason ? (
                      <View style={s.reasonBox}>
                        <Ionicons name="document-text-outline" size={13} color={APP_TEXT_3} />
                        <Text style={s.reasonText}>{appt.reason}</Text>
                      </View>
                    ) : null}
                    {canCancel ? (
                      <Pressable onPress={() => handleCancel(appt)} style={s.cancelBtn}>
                        <Ionicons name="close-circle-outline" size={15} color={APP_RED} />
                        <Text style={s.cancelBtnText}>Cancel Appointment</Text>
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
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },
  toolbar: {
    flexDirection: "row", alignItems: "center", gap: 8,
    paddingHorizontal: 12, paddingVertical: 8,
    backgroundColor: APP_SURFACE,
    borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER,
  },
  filterRow: { gap: 6 },
  filterPill: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, backgroundColor: "#EEF2F2" },
  filterPillActive: { backgroundColor: APP_GREEN_XLIGHT, borderWidth: 1, borderColor: APP_GREEN_LIGHT },
  filterPillText: { fontSize: 12, fontWeight: "500", color: APP_TEXT_2 },
  filterPillTextActive: { color: APP_GREEN, fontWeight: "700" },
  bookBtn: {
    flexDirection: "row", alignItems: "center", gap: 4,
    backgroundColor: APP_GREEN, paddingHorizontal: 12, paddingVertical: 7, borderRadius: 20,
  },
  bookBtnActive: { backgroundColor: "#EEF2F2" },
  bookBtnText: { fontSize: 13, fontWeight: "700", color: "#FFFFFF" },

  scroll: { flex: 1 },
  content: { padding: 16, gap: 12, paddingBottom: 40 },

  formCard: {
    backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 12,
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3,
  },
  formTitle: { fontSize: 16, fontWeight: "700", color: APP_TEXT },
  submitBtn: {
    flexDirection: "row", alignItems: "center", justifyContent: "center",
    gap: 8, backgroundColor: APP_GREEN, paddingVertical: 14, borderRadius: 14,
  },
  submitBtnDisabled: { opacity: 0.5 },
  submitBtnText: { fontSize: 14, fontWeight: "700", color: "#FFFFFF" },

  card: {
    backgroundColor: APP_SURFACE, borderRadius: 16, borderLeftWidth: 4,
    overflow: "hidden", shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 6, elevation: 3,
  },
  cardTop: { flexDirection: "row", alignItems: "stretch", gap: 12 },
  dateBlock: {
    width: 58, alignItems: "center", justifyContent: "center",
    paddingVertical: 14, gap: 1,
  },
  dateDay: { fontSize: 9, fontWeight: "700", letterSpacing: 0.5 },
  dateNum: { fontSize: 24, fontWeight: "900", lineHeight: 28 },
  dateMon: { fontSize: 9, fontWeight: "700", letterSpacing: 0.5 },
  cardMain: { flex: 1, paddingVertical: 14, gap: 4 },
  cardRight: { paddingVertical: 12, paddingRight: 12, alignItems: "flex-end" },
  apptType: { fontSize: 14, fontWeight: "700", color: APP_TEXT },
  metaRow: { flexDirection: "row", alignItems: "center", gap: 4 },
  metaText: { fontSize: 12, color: APP_TEXT_2, flex: 1 },
  statusChip: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 10 },
  statusText: { fontSize: 11, fontWeight: "700" },

  expanded: {
    paddingHorizontal: 14, paddingBottom: 14, gap: 10,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  reasonBox: { flexDirection: "row", gap: 8, backgroundColor: APP_GREEN_XLIGHT, borderRadius: 10, padding: 10 },
  reasonText: { fontSize: 13, color: APP_TEXT_2, flex: 1, lineHeight: 18 },
  cancelBtn: {
    flexDirection: "row", alignItems: "center", gap: 6,
    paddingVertical: 10, paddingHorizontal: 14,
    borderRadius: 12, borderWidth: 1, borderColor: "#FECACA", backgroundColor: APP_RED_LIGHT,
    alignSelf: "flex-start",
  },
  cancelBtnText: { fontSize: 13, fontWeight: "600", color: APP_RED },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
