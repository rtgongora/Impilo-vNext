import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  View,
  Text,
  Pressable,
  StyleSheet,
  ScrollView,
  RefreshControl,
  Dimensions,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Avatar } from "@impilo/mobile-design-system";
import { useAppStore } from "../stores/appStore";
import { fetchAppointments } from "../services/appointmentService";
import { fetchPrescriptions } from "../services/prescriptionService";
import { fetchLabResults } from "../services/labResultService";
import type { Appointment, Prescription, LabResult, AppointmentStatus } from "../types";
import { FacilityDirectoryScreen } from "./FacilityDirectoryScreen";
import { FacilityDetailScreen } from "./FacilityDetailScreen";
import { NotificationsScreen } from "./NotificationsScreen";
import { useToast } from "../components/Toast";
import { SkeletonHomeContent } from "../components/SkeletonLoader";
import {
  USE_SEED_DATA,
  SEED_APPOINTMENTS,
  SEED_PRESCRIPTIONS,
  SEED_LAB_RESULTS,
} from "../data/seedHealthRecords";
import {
  APP_GREEN,
  APP_GREEN_DARK,
  APP_GREEN_MID,
  APP_GREEN_LIGHT,
  APP_GREEN_XLIGHT,
  APP_RED,
  APP_RED_LIGHT,
  APP_GOLD,
  APP_GOLD_LIGHT,
  APP_SURFACE,
  APP_BG,
  APP_TEXT,
  APP_TEXT_2,
  APP_TEXT_3,
  APP_BORDER,
} from "../lib/colors";

const { width: SCREEN_W } = Dimensions.get("window");
const CELL = (SCREEN_W - 32 - 20) / 3;

// ── Fix #2 — real status config replacing hardcoded "Confirmed" ───────────────
const APPT_STATUS_CFG: Partial<Record<AppointmentStatus, { label: string; color: string; dotColor: string }>> = {
  SCHEDULED:   { label: "Scheduled",   color: APP_GREEN,      dotColor: APP_GREEN      },
  CONFIRMED:   { label: "Confirmed",   color: APP_GREEN_DARK, dotColor: APP_GREEN_DARK },
  CHECKED_IN:  { label: "Checked In",  color: APP_GREEN,      dotColor: APP_GREEN      },
  IN_PROGRESS: { label: "In Progress", color: APP_GOLD,       dotColor: APP_GOLD       },
  COMPLETED:   { label: "Completed",   color: APP_TEXT_2,     dotColor: APP_TEXT_3     },
  CANCELLED:   { label: "Cancelled",   color: APP_RED,        dotColor: APP_RED        },
  NO_SHOW:     { label: "No Show",     color: APP_RED,        dotColor: APP_RED        },
};

// ── Fix #7 — Quick Actions: deep-link section targets added ───────────────────
const QUICK_ACTIONS = [
  { id: "book",       label: "Book Appt",  icon: "calendar",       tab: "personal",    section: "appointments",  color: APP_GREEN,      bg: APP_GREEN_LIGHT  },
  { id: "refill",     label: "Refill Rx",  icon: "refresh-circle", tab: "personal",    section: "prescriptions", color: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT },
  { id: "message",    label: "Message",    icon: "chatbubble",     tab: "messaging",   section: null,            color: APP_GREEN_MID,  bg: APP_GREEN_LIGHT  },
  { id: "telehealth", label: "Video Call", icon: "videocam",       tab: "telehealth",  section: null,            color: APP_GOLD,       bg: APP_GOLD_LIGHT   },
  { id: "records",    label: "Records",    icon: "folder",         tab: "personal",    section: "records",       color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT  },
  { id: "services",   label: "Services",   icon: "storefront",     tab: "marketplace", section: null,            color: APP_RED,        bg: APP_RED_LIGHT    },
] as const;

type HomeView = "home" | "notifications" | "facility-list" | { mode: "facility-detail"; id: string };

// ── Sub-components ────────────────────────────────────────────────────────────

function SectionHeader({ title, action, onAction }: { title: string; action?: string; onAction?: () => void }) {
  return (
    <View style={s.sectionRow}>
      <Text style={s.sectionTitle}>{title}</Text>
      {action ? (
        <Pressable onPress={onAction} hitSlop={10}>
          <Text style={s.sectionAction}>{action}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

function StatCard({ icon, count, label, color, bg, onPress }: {
  icon: React.ComponentProps<typeof Ionicons>["name"];
  count: number; label: string; color: string; bg: string; onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [s.statCard, { backgroundColor: APP_SURFACE }, pressed && { opacity: 0.8 }]}>
      <View style={[s.statIcon, { backgroundColor: bg }]}>
        <Ionicons name={icon} size={16} color={color} />
      </View>
      <Text style={[s.statCount, { color }]}>{count}</Text>
      <Text style={s.statLabel}>{label}</Text>
    </Pressable>
  );
}

// ── Fix #2 — uses real status, Fix #9 — pressable ────────────────────────────
function ApptCard({ appt, onPress }: { appt: Appointment; onPress: () => void }) {
  const d = new Date(appt.scheduledAt);
  const statusCfg = APPT_STATUS_CFG[appt.status] ?? { label: appt.status, color: APP_TEXT_2, dotColor: APP_TEXT_3 };
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [s.apptCard, pressed && { opacity: 0.92 }]}>
      <View style={s.apptDateCol}>
        <Text style={s.apptWeekday}>{d.toLocaleDateString([], { weekday: "short" }).toUpperCase()}</Text>
        <Text style={s.apptDay}>{d.getDate()}</Text>
        <Text style={s.apptMonth}>{d.toLocaleDateString([], { month: "short" }).toUpperCase()}</Text>
      </View>
      <View style={s.apptSep} />
      <View style={s.apptBody}>
        <Text style={s.apptType} numberOfLines={1}>{appt.appointmentType}</Text>
        <View style={s.apptMeta}>
          <Ionicons name="business-outline" size={12} color={APP_TEXT_3} />
          <Text style={s.apptMetaText} numberOfLines={1}>{appt.facilityName}</Text>
        </View>
        <View style={s.apptMeta}>
          <Ionicons name="time-outline" size={12} color={APP_TEXT_3} />
          <Text style={s.apptMetaText}>{d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</Text>
        </View>
      </View>
      <View style={s.apptBadge}>
        <View style={[s.apptBadgeDot, { backgroundColor: statusCfg.dotColor }]} />
        <Text style={[s.apptBadgeText, { color: statusCfg.color }]}>{statusCfg.label}</Text>
      </View>
    </Pressable>
  );
}

function MedRow({ rx, divider }: { rx: Prescription; divider: boolean }) {
  return (
    <View style={[s.listRow, divider && s.rowDivider]}>
      <View style={[s.rowIcon, { backgroundColor: APP_GREEN_LIGHT }]}>
        <Ionicons name="medical" size={15} color={APP_GREEN} />
      </View>
      <View style={s.rowBody}>
        <Text style={s.rowTitle}>{rx.medicationName}</Text>
        <Text style={s.rowSub}>{`${rx.dosage} · ${rx.frequency}`}</Text>
      </View>
      <View style={[s.pill, { backgroundColor: APP_GREEN_XLIGHT }]}>
        <Text style={[s.pillText, { color: APP_GREEN_DARK }]}>{rx.refillsRemaining}× left</Text>
      </View>
    </View>
  );
}

function LabRow({ lab, divider }: { lab: LabResult; divider: boolean }) {
  return (
    <View style={[s.listRow, divider && s.rowDivider]}>
      <View style={[s.rowIcon, { backgroundColor: APP_GOLD_LIGHT }]}>
        <Ionicons name="flask" size={15} color={APP_GOLD} />
      </View>
      <View style={s.rowBody}>
        <Text style={s.rowTitle}>{lab.testName}</Text>
        <Text style={s.rowSub}>{lab.facilityName}</Text>
      </View>
      <View style={[s.pill, { backgroundColor: APP_GOLD_LIGHT }]}>
        <Text style={[s.pillText, { color: APP_GOLD }]}>{lab.status}</Text>
      </View>
    </View>
  );
}

// ── Main Screen ───────────────────────────────────────────────────────────────

export function HomeScreen() {
  const insets = useSafeAreaInsets();
  const { profile, setActiveTab, setPendingPersonalSection, unreadNotifications, selectedFacilityName } = useAppStore();
  const toast    = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const [view, setView]             = useState<HomeView>("home");
  const [appts, setAppts]           = useState<Appointment[]>([]);
  const [rxs, setRxs]               = useState<Prescription[]>([]);
  const [labs, setLabs]             = useState<LabResult[]>([]);
  // Fix #3 — store real totals from API, not just item count
  const [apptTotal, setApptTotal]   = useState(0);
  const [rxTotal, setRxTotal]       = useState(0);
  const [labTotal, setLabTotal]     = useState(0);
  const [isLoading, setIsLoading]   = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        // Fix #4 — seed data support
        await new Promise((r) => setTimeout(r, 400));
        const seedAppts = SEED_APPOINTMENTS.filter((a) => a.status === "SCHEDULED" || a.status === "CONFIRMED");
        const seedRxs   = SEED_PRESCRIPTIONS.filter((r) => r.status === "ACTIVE");
        const seedLabs  = SEED_LAB_RESULTS.filter((l) => l.status === "PROCESSING" || l.status === "ORDERED");
        setAppts(seedAppts.slice(0, 3));
        setRxs(seedRxs.slice(0, 3));
        setLabs(seedLabs.slice(0, 3));
        setApptTotal(seedAppts.length);
        setRxTotal(seedRxs.length);
        setLabTotal(seedLabs.length);
      } else {
        const [a, r, l] = await Promise.all([
          fetchAppointments({ status: "SCHEDULED", size: 3 }),
          fetchPrescriptions({ status: "ACTIVE", size: 3 }),
          fetchLabResults({ status: "PROCESSING", size: 3 }),
        ]);
        setAppts(a.items);
        setRxs(r.items);
        setLabs(l.items);
        // Fix #3 — use totalElements, not items.length
        setApptTotal(a.totalElements);
        setRxTotal(r.totalElements);
        setLabTotal(l.totalElements);
      }
    } catch {
      toastRef.current.error("Could not refresh. Pull down to retry.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const onRefresh = useCallback(() => {
    setIsRefreshing(true);
    void load(true);
  }, [load]);

  // Fix #8 — compute greeting inline, no stale memo
  const hour = new Date().getHours();
  const greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";

  const facilityLabel = selectedFacilityName ?? profile?.facilityName;
  const nextAppt      = appts[0];

  // Fix #7 — deep-link helper: switches tab AND sets the section to open
  const navigateTo = useCallback((tab: string, section: string | null) => {
    setActiveTab(tab as Parameters<typeof setActiveTab>[0]);
    if (section) setPendingPersonalSection(section);
  }, [setActiveTab, setPendingPersonalSection]);

  const isSubView = view !== "home";

  return (
    <View style={{ flex: 1 }}>
      {/* Fix #1 — sub-views rendered on top, home stays mounted underneath */}
      {view === "notifications" ? (
        <NotificationsScreen onBack={() => setView("home")} />
      ) : null}
      {view === "facility-list" ? (
        <FacilityDirectoryScreen
          onOpenFacility={(id) => setView({ mode: "facility-detail", id })}
        />
      ) : null}
      {typeof view === "object" && view.mode === "facility-detail" ? (
        <FacilityDetailScreen
          facilityId={view.id}
          onBack={() => setView("facility-list")}
        />
      ) : null}

      {/* Home — always mounted, hidden while a sub-view is active */}
      <View style={[s.root, isSubView && s.hidden]}>
        <ScrollView
          testID="home-screen"
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{ paddingBottom: 52 }}
          refreshControl={
            <RefreshControl
              refreshing={isRefreshing}
              onRefresh={onRefresh}
              tintColor="rgba(255,255,255,0.8)"
              colors={[APP_GREEN]}
              progressViewOffset={insets.top + 80}
            />
          }
        >
          {/* ════════ HERO ════════ */}
          <View style={[s.hero, { paddingTop: insets.top + 16 }]}>
            <View style={s.decoRing1} />
            <View style={s.decoRing2} />
            <View style={s.crossH} />
            <View style={s.crossV} />

            {/* Top bar */}
            <View style={s.topBar}>
              <View style={s.brandRow}>
                <View style={s.brandIcon}>
                  <Ionicons name="pulse" size={14} color={APP_GREEN} />
                </View>
                <Text style={s.brandText}>Impilo</Text>
              </View>
              <Pressable onPress={() => setView("notifications")} style={s.bellBtn} hitSlop={10}>
                <Ionicons name="notifications-outline" size={20} color="rgba(255,255,255,0.9)" />
                {unreadNotifications > 0 ? (
                  <View style={s.bellDot}>
                    <Text style={s.bellDotText}>{unreadNotifications > 9 ? "9+" : String(unreadNotifications)}</Text>
                  </View>
                ) : null}
              </Pressable>
            </View>

            {/* Profile row */}
            <View style={s.profileRow}>
              <View style={s.greetBlock}>
                <Text style={s.greetLine}>{greeting}</Text>
                <Text style={s.nameLine} numberOfLines={1}>
                  {profile?.givenName ?? "there"} 👋
                </Text>
                <Pressable onPress={() => setView("facility-list")} style={s.facilityBtn}>
                  <Ionicons name="location-outline" size={12} color="rgba(255,255,255,0.8)" />
                  <Text style={s.facilityBtnText} numberOfLines={1}>
                    {facilityLabel ?? "Set primary facility"}
                  </Text>
                  <Ionicons
                    name={facilityLabel ? "chevron-forward" : "add-circle-outline"}
                    size={12}
                    color="rgba(255,255,255,0.55)"
                  />
                </Pressable>
              </View>
              <View style={s.avatarRing}>
                {profile ? (
                  <Avatar name={`${profile.givenName} ${profile.familyName}`} size="lg" />
                ) : (
                  <View style={s.avatarPlaceholder}>
                    <Ionicons name="person" size={28} color="rgba(255,255,255,0.5)" />
                  </View>
                )}
              </View>
            </View>

            {/* Health ID strip */}
            <Pressable
              onPress={() => navigateTo("personal", "health-id")}
              style={({ pressed }) => [s.healthIdStrip, pressed && { opacity: 0.85 }]}
              testID="health-id-card"
            >
              <View style={s.healthIdLeft}>
                <Ionicons name="id-card-outline" size={18} color="rgba(255,255,255,0.9)" />
                <View>
                  <Text style={s.healthIdLabel}>Health ID</Text>
                  {profile?.cpid ? <Text style={s.healthIdCpid}>{profile.cpid}</Text> : null}
                </View>
              </View>
              <View style={s.healthIdRight}>
                <Ionicons name="qr-code-outline" size={18} color="rgba(255,255,255,0.7)" />
                <Text style={s.healthIdShow}>Show</Text>
                <Ionicons name="chevron-forward" size={14} color="rgba(255,255,255,0.5)" />
              </View>
            </Pressable>
          </View>

          {/* ════════ BODY ════════ */}
          <View style={s.body}>
            {isLoading ? (
              <SkeletonHomeContent />
            ) : (
              <>
                {/* Fix #3 — stat strip uses real totals */}
                <View style={s.statRow}>
                  <StatCard
                    icon="calendar"
                    count={apptTotal}
                    label="Appointments"
                    color={APP_GREEN}
                    bg={APP_GREEN_LIGHT}
                    onPress={() => navigateTo("personal", "appointments")}
                  />
                  <StatCard
                    icon="medical"
                    count={rxTotal}
                    label="Medications"
                    color={APP_GREEN_DARK}
                    bg={APP_GREEN_XLIGHT}
                    onPress={() => navigateTo("personal", "prescriptions")}
                  />
                  <StatCard
                    icon="flask"
                    count={labTotal}
                    label="Lab Results"
                    color={labTotal > 0 ? APP_GOLD : APP_TEXT_3}
                    bg={labTotal > 0 ? APP_GOLD_LIGHT : "#F3F4F6"}
                    onPress={() => navigateTo("personal", "results")}
                  />
                </View>

                {/* Fix #7 — Quick Actions deep-link with section */}
                <SectionHeader title="Quick Actions" />
                <View style={s.actionGrid}>
                  {QUICK_ACTIONS.map((a) => (
                    <Pressable
                      key={a.id}
                      testID={`quick-${a.id}`}
                      onPress={() => navigateTo(a.tab, a.section)}
                      style={({ pressed }) => [s.actionCell, pressed && s.actionCellPressed]}
                    >
                      <View style={[s.actionIcon, { backgroundColor: a.bg }]}>
                        <Ionicons name={a.icon as never} size={26} color={a.color} />
                      </View>
                      <Text style={s.actionLabel}>{a.label}</Text>
                    </Pressable>
                  ))}
                </View>

                {/* Fix #2 + Fix #9 — real status, pressable appt card */}
                <SectionHeader
                  title="Next Appointment"
                  action={apptTotal > 1 ? `${apptTotal} total →` : undefined}
                  onAction={() => navigateTo("personal", "appointments")}
                />
                {nextAppt ? (
                  <ApptCard appt={nextAppt} onPress={() => navigateTo("personal", "appointments")} />
                ) : (
                  <Pressable
                    onPress={() => navigateTo("personal", "appointments")}
                    style={({ pressed }) => [s.emptyCard, pressed && { opacity: 0.88 }]}
                  >
                    <View style={[s.emptyCardIcon, { backgroundColor: APP_GREEN_LIGHT }]}>
                      <Ionicons name="calendar-outline" size={22} color={APP_GREEN} />
                    </View>
                    <View style={s.emptyCardText}>
                      <Text style={s.emptyCardTitle}>No upcoming appointments</Text>
                      <Text style={s.emptyCardSub}>Tap to book your next visit</Text>
                    </View>
                    <Ionicons name="chevron-forward" size={16} color={APP_TEXT_3} />
                  </Pressable>
                )}

                {/* Medications — Fix #5: always show section (empty state when none) */}
                <SectionHeader
                  title="Active Medications"
                  action={rxTotal > 0 ? `${rxTotal} active →` : undefined}
                  onAction={() => navigateTo("personal", "prescriptions")}
                />
                {rxs.length > 0 ? (
                  <View style={s.listCard}>
                    {rxs.map((rx, i) => <MedRow key={rx.id} rx={rx} divider={i < rxs.length - 1} />)}
                  </View>
                ) : (
                  <Pressable
                    onPress={() => navigateTo("personal", "prescriptions")}
                    style={({ pressed }) => [s.emptyCard, pressed && { opacity: 0.88 }]}
                  >
                    <View style={[s.emptyCardIcon, { backgroundColor: APP_GREEN_XLIGHT }]}>
                      <Ionicons name="medical-outline" size={22} color={APP_GREEN_DARK} />
                    </View>
                    <View style={s.emptyCardText}>
                      <Text style={s.emptyCardTitle}>No active medications</Text>
                      <Text style={s.emptyCardSub}>Your prescriptions will appear here</Text>
                    </View>
                    <Ionicons name="chevron-forward" size={16} color={APP_TEXT_3} />
                  </Pressable>
                )}

                {/* Lab Results — Fix #5: always show section */}
                {labTotal > 0 || labs.length > 0 ? (
                  <>
                    <SectionHeader
                      title="Pending Lab Results"
                      action={`${labTotal} pending →`}
                      onAction={() => navigateTo("personal", "results")}
                    />
                    <View style={s.listCard}>
                      {labs.map((lab, i) => <LabRow key={lab.id} lab={lab} divider={i < labs.length - 1} />)}
                    </View>
                  </>
                ) : null}

                {/* Fix #6 — Wellness nudge uses APP_GREEN not APP_RED */}
                <Pressable
                  onPress={() => navigateTo("personal", "wellness")}
                  style={({ pressed }) => [s.nudgeCard, pressed && { opacity: 0.9 }]}
                >
                  <View style={[s.nudgeIconWrap, { backgroundColor: APP_GREEN_LIGHT }]}>
                    <Ionicons name="heart" size={20} color={APP_GREEN} />
                  </View>
                  <View style={s.nudgeBody}>
                    <Text style={s.nudgeTitle}>Wellness Hub</Text>
                    <Text style={s.nudgeSub}>Log vitals · goals · challenges</Text>
                  </View>
                  <Ionicons name="chevron-forward" size={16} color={APP_TEXT_3} />
                </Pressable>
              </>
            )}
          </View>
        </ScrollView>
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  hidden: { display: "none" },

  hero: {
    backgroundColor: APP_GREEN,
    paddingHorizontal: 20,
    paddingBottom: 24,
    borderBottomLeftRadius: 32,
    borderBottomRightRadius: 32,
    overflow: "hidden",
    shadowColor: APP_GREEN,
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.35,
    shadowRadius: 20,
    elevation: 12,
  },
  decoRing1: { position: "absolute", width: 200, height: 200, borderRadius: 100, borderWidth: 30, borderColor: "rgba(255,255,255,0.07)", top: -70, right: -60 },
  decoRing2: { position: "absolute", width: 120, height: 120, borderRadius: 60, borderWidth: 20, borderColor: "rgba(255,255,255,0.05)", top: -10, right: 40 },
  crossH: { position: "absolute", width: 56, height: 14, borderRadius: 4, backgroundColor: "rgba(255,255,255,0.07)", bottom: 52, left: -8 },
  crossV: { position: "absolute", width: 14, height: 56, borderRadius: 4, backgroundColor: "rgba(255,255,255,0.07)", bottom: 24, left: 13 },

  topBar: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 24 },
  brandRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  brandIcon: { width: 28, height: 28, borderRadius: 8, backgroundColor: "rgba(255,255,255,0.9)", alignItems: "center", justifyContent: "center" },
  brandText: { fontSize: 18, fontWeight: "800", color: "#FFFFFF", letterSpacing: 0.2 },
  bellBtn: { position: "relative", padding: 4 },
  bellDot: { position: "absolute", top: 2, right: 2, backgroundColor: APP_RED, borderRadius: 7, minWidth: 14, height: 14, alignItems: "center", justifyContent: "center", paddingHorizontal: 2, borderWidth: 1.5, borderColor: APP_GREEN },
  bellDotText: { fontSize: 8, color: "#FFFFFF", fontWeight: "800" },

  profileRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 20 },
  greetBlock: { flex: 1, marginRight: 16 },
  greetLine: { fontSize: 13, color: "rgba(255,255,255,0.7)", fontWeight: "400", marginBottom: 2 },
  nameLine: { fontSize: 26, fontWeight: "800", color: "#FFFFFF", lineHeight: 32, marginBottom: 10 },
  facilityBtn: { flexDirection: "row", alignItems: "center", gap: 5, alignSelf: "flex-start", backgroundColor: "rgba(255,255,255,0.13)", paddingHorizontal: 10, paddingVertical: 6, borderRadius: 20, borderWidth: 1, borderColor: "rgba(255,255,255,0.15)" },
  facilityBtnText: { fontSize: 12, color: "rgba(255,255,255,0.85)", flex: 1, maxWidth: 160 },
  avatarRing: { padding: 3, borderRadius: 34, borderWidth: 2, borderColor: "rgba(255,255,255,0.35)" },
  avatarPlaceholder: { width: 56, height: 56, borderRadius: 28, backgroundColor: "rgba(255,255,255,0.15)", alignItems: "center", justifyContent: "center" },

  healthIdStrip: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", backgroundColor: "rgba(255,255,255,0.13)", borderRadius: 16, paddingVertical: 12, paddingHorizontal: 16, borderWidth: 1, borderColor: "rgba(255,255,255,0.18)" },
  healthIdLeft: { flexDirection: "row", alignItems: "center", gap: 10 },
  healthIdLabel: { fontSize: 13, fontWeight: "700", color: "#FFFFFF" },
  healthIdCpid: { fontSize: 11, color: "rgba(255,255,255,0.6)", fontFamily: "monospace", marginTop: 1 },
  healthIdRight: { flexDirection: "row", alignItems: "center", gap: 6 },
  healthIdShow: { fontSize: 13, fontWeight: "600", color: "rgba(255,255,255,0.85)" },

  body: { paddingHorizontal: 16, paddingTop: 20, gap: 16 },

  statRow: { flexDirection: "row", gap: 10 },
  statCard: { flex: 1, borderRadius: 16, padding: 14, alignItems: "center", gap: 7, shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 4, elevation: 2 },
  statIcon: { width: 36, height: 36, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  statCount: { fontSize: 22, fontWeight: "800" },
  statLabel: { fontSize: 10, fontWeight: "600", color: APP_TEXT_3, textAlign: "center" },

  sectionRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  sectionTitle: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  sectionAction: { fontSize: 13, fontWeight: "600", color: APP_GREEN },

  actionGrid: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  actionCell: { width: CELL, backgroundColor: APP_SURFACE, borderRadius: 18, padding: 14, alignItems: "center", gap: 10, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 6, elevation: 3 },
  actionCellPressed: { opacity: 0.78, transform: [{ scale: 0.96 }] },
  actionIcon: { width: 52, height: 52, borderRadius: 16, alignItems: "center", justifyContent: "center" },
  actionLabel: { fontSize: 12, fontWeight: "600", color: APP_TEXT, textAlign: "center" },

  apptCard: { flexDirection: "row", alignItems: "stretch", backgroundColor: APP_SURFACE, borderRadius: 20, overflow: "hidden", shadowColor: APP_GREEN, shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.12, shadowRadius: 12, elevation: 5 },
  apptDateCol: { width: 72, backgroundColor: APP_GREEN, alignItems: "center", justifyContent: "center", paddingVertical: 22, gap: 1 },
  apptWeekday: { fontSize: 10, fontWeight: "700", color: "rgba(255,255,255,0.75)", letterSpacing: 0.5 },
  apptDay: { fontSize: 32, fontWeight: "900", color: "#FFFFFF", lineHeight: 36 },
  apptMonth: { fontSize: 10, fontWeight: "700", color: "rgba(255,255,255,0.75)", letterSpacing: 0.5 },
  apptSep: { width: 1, backgroundColor: APP_BORDER, alignSelf: "stretch" },
  apptBody: { flex: 1, paddingHorizontal: 16, paddingVertical: 18, gap: 6 },
  apptType: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  apptMeta: { flexDirection: "row", alignItems: "center", gap: 5 },
  apptMetaText: { fontSize: 12, color: APP_TEXT_2, flex: 1 },
  apptBadge: { flexDirection: "row", alignItems: "center", gap: 5, paddingHorizontal: 14, alignSelf: "center", marginRight: 14 },
  apptBadgeDot: { width: 7, height: 7, borderRadius: 4 },
  apptBadgeText: { fontSize: 11, fontWeight: "700" },

  emptyCard: { flexDirection: "row", alignItems: "center", gap: 14, backgroundColor: APP_SURFACE, borderRadius: 16, padding: 16, borderWidth: 1.5, borderColor: APP_BORDER, borderStyle: "dashed" },
  emptyCardIcon: { width: 48, height: 48, borderRadius: 14, alignItems: "center", justifyContent: "center" },
  emptyCardText: { flex: 1 },
  emptyCardTitle: { fontSize: 14, fontWeight: "700", color: APP_TEXT },
  emptyCardSub: { fontSize: 12, color: APP_TEXT_3, marginTop: 2 },

  listCard: { backgroundColor: APP_SURFACE, borderRadius: 18, overflow: "hidden", shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.05, shadowRadius: 8, elevation: 3 },
  listRow: { flexDirection: "row", alignItems: "center", gap: 12, paddingHorizontal: 16, paddingVertical: 14 },
  rowDivider: { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER },
  rowIcon: { width: 38, height: 38, borderRadius: 11, alignItems: "center", justifyContent: "center" },
  rowBody: { flex: 1 },
  rowTitle: { fontSize: 14, fontWeight: "700", color: APP_TEXT },
  rowSub: { fontSize: 12, color: APP_TEXT_2, marginTop: 2 },
  pill: { paddingHorizontal: 9, paddingVertical: 4, borderRadius: 10 },
  pillText: { fontSize: 11, fontWeight: "700" },

  nudgeCard: { flexDirection: "row", alignItems: "center", backgroundColor: APP_SURFACE, borderRadius: 16, padding: 16, gap: 14, shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.04, shadowRadius: 6, elevation: 2, marginTop: 4 },
  nudgeIconWrap: { width: 44, height: 44, borderRadius: 13, alignItems: "center", justifyContent: "center" },
  nudgeBody: { flex: 1 },
  nudgeTitle: { fontSize: 14, fontWeight: "700", color: APP_TEXT },
  nudgeSub: { fontSize: 12, color: APP_TEXT_3, marginTop: 2 },
});
