import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { fetchPrograms, enrollInProgram, fetchMyEnrollments } from "../../services/programService";
import type { WellnessProgram, Enrollment } from "../../types";
import { USE_SEED_DATA, SEED_PROGRAMS, SEED_ENROLLMENTS } from "../../data/seedHealthRecords";
import { useToast } from "../../components/Toast";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const CATEGORY_CFG: Record<string, { icon: React.ComponentProps<typeof Ionicons>["name"]; color: string; bg: string }> = {
  "Chronic Disease": { icon: "medical-outline",   color: APP_GREEN,      bg: APP_GREEN_LIGHT  },
  "Cardiovascular":  { icon: "heart-outline",      color: "#DC2626",      bg: "#FEF2F2"        },
  "Mental Health":   { icon: "happy-outline",      color: "#7C3AED",      bg: "#F5F3FF"        },
  "Fitness":         { icon: "fitness-outline",    color: APP_GOLD,       bg: APP_GOLD_LIGHT   },
  "Nutrition":       { icon: "nutrition-outline",  color: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT },
};

function ProgressRing({ progress }: { progress: number }) {
  const color = progress >= 80 ? APP_GREEN : progress >= 50 ? APP_GREEN_DARK : APP_GOLD;
  return (
    <View style={pr.wrap}>
      <View style={[pr.track, { borderColor: color + "33" }]}>
        <Text style={[pr.num, { color }]}>{progress}%</Text>
      </View>
    </View>
  );
}
const pr = StyleSheet.create({
  wrap: { alignItems: "center", justifyContent: "center" },
  track: { width: 52, height: 52, borderRadius: 26, borderWidth: 4, alignItems: "center", justifyContent: "center" },
  num: { fontSize: 13, fontWeight: "800" },
});

export function ProgramsScreen() {
  const toast    = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const [programs, setPrograms]         = useState<WellnessProgram[]>([]);
  const [enrollments, setEnrollments]   = useState<Enrollment[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [enrollingId, setEnrollingId]   = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setPrograms(SEED_PROGRAMS);
        setEnrollments(SEED_ENROLLMENTS);
      } else {
        const [progs, enrs] = await Promise.all([fetchPrograms(), fetchMyEnrollments()]);
        setPrograms(progs);
        setEnrollments(enrs);
      }
    } catch {
      toastRef.current.error("Could not load programmes.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const handleEnroll = useCallback(async (programId: string, programName: string) => {
    if (USE_SEED_DATA) { toastRef.current.success(`Enrolled in ${programName}!`); return; }
    setEnrollingId(programId);
    try {
      await enrollInProgram(programId);
      toastRef.current.success(`Enrolled in ${programName}!`);
      void load(true);
    } catch {
      toastRef.current.error("Could not enrol. Please try again.");
    } finally {
      setEnrollingId(null);
    }
  }, [load]);

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  const enrolledIds = new Set(enrollments.map((e) => e.programId));
  const myPrograms  = enrollments;
  const available   = programs.filter((p) => !enrolledIds.has(p.id));

  return (
    <ScrollView
      style={s.root}
      contentContainerStyle={s.content}
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
    >
      {/* My enrollments */}
      {myPrograms.length > 0 ? (
        <>
          <Text style={s.sectionLabel}>Enrolled Programmes</Text>
          {myPrograms.map((enr) => {
            const prog = programs.find((p) => p.id === enr.programId);
            const cfg  = CATEGORY_CFG[prog?.category ?? ""] ?? CATEGORY_CFG.Fitness;
            return (
              <View key={enr.id} style={[s.card, { borderLeftWidth: 4, borderLeftColor: APP_GREEN }]}>
                <View style={s.cardTop}>
                  <View style={[s.cardIcon, { backgroundColor: cfg.bg }]}>
                    <Ionicons name={cfg.icon} size={22} color={cfg.color} />
                  </View>
                  <View style={s.cardMain}>
                    <Text style={s.cardTitle}>{enr.programName}</Text>
                    {prog?.category ? <Text style={s.cardCategory}>{prog.category}</Text> : null}
                    {prog?.duration ? (
                      <View style={s.metaRow}>
                        <Ionicons name="time-outline" size={12} color={APP_TEXT_3} />
                        <Text style={s.metaText}>{prog.duration}</Text>
                      </View>
                    ) : null}
                    <View style={s.metaRow}>
                      <Ionicons name="calendar-outline" size={12} color={APP_TEXT_3} />
                      <Text style={s.metaText}>Enrolled {new Date(enr.enrolledAt).toLocaleDateString()}</Text>
                    </View>
                  </View>
                  <ProgressRing progress={enr.progress} />
                </View>
                {/* Progress bar */}
                <View style={s.progBarWrap}>
                  <View style={s.progBarTrack}>
                    <View style={[s.progBarFill, { width: `${enr.progress}%` as never }]} />
                  </View>
                  <Text style={s.progBarLabel}>{enr.progress}% complete</Text>
                </View>
                <Pressable style={s.continueBtn}>
                  <Ionicons name="play-circle-outline" size={15} color={APP_GREEN} />
                  <Text style={s.continueBtnText}>Continue Programme</Text>
                </Pressable>
              </View>
            );
          })}
        </>
      ) : null}

      {/* Available programmes */}
      {available.length > 0 ? (
        <>
          <Text style={s.sectionLabel}>Available Programmes</Text>
          {available.map((prog) => {
            const cfg       = CATEGORY_CFG[prog.category] ?? CATEGORY_CFG.Fitness;
            const isEnrolling = enrollingId === prog.id;
            return (
              <View key={prog.id} style={s.card}>
                <View style={s.cardTop}>
                  <View style={[s.cardIcon, { backgroundColor: cfg.bg }]}>
                    <Ionicons name={cfg.icon} size={22} color={cfg.color} />
                  </View>
                  <View style={s.cardMain}>
                    <Text style={s.cardTitle}>{prog.name}</Text>
                    <Text style={s.cardCategory}>{prog.category}</Text>
                    <Text style={s.cardDesc} numberOfLines={2}>{prog.description}</Text>
                    <View style={s.metaRow}>
                      <Ionicons name="time-outline" size={12} color={APP_TEXT_3} />
                      <Text style={s.metaText}>{prog.duration}</Text>
                    </View>
                  </View>
                </View>
                <Pressable
                  onPress={() => handleEnroll(prog.id, prog.name)}
                  disabled={isEnrolling}
                  style={[s.enrollBtn, isEnrolling && s.enrollBtnDisabled]}
                >
                  {isEnrolling ? <LoadingSpinner size="sm" /> : (
                    <>
                      <Ionicons name="add-circle-outline" size={15} color="#FFFFFF" />
                      <Text style={s.enrollBtnText}>Enrol Now</Text>
                    </>
                  )}
                </Pressable>
              </View>
            );
          })}
        </>
      ) : null}

      {programs.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="ribbon-outline" size={52} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyTitle}>No programmes available</Text>
          <Text style={s.emptySub}>Wellness programmes will appear here when available.</Text>
        </View>
      ) : null}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 14, paddingBottom: 40 },
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },
  sectionLabel: { fontSize: 12, fontWeight: "700", color: APP_TEXT_2, textTransform: "uppercase", letterSpacing: 0.8, paddingHorizontal: 4 },
  card: {
    backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 12,
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3,
  },
  cardTop: { flexDirection: "row", alignItems: "flex-start", gap: 12 },
  cardIcon: { width: 48, height: 48, borderRadius: 14, alignItems: "center", justifyContent: "center" },
  cardMain: { flex: 1, gap: 4 },
  cardTitle: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  cardCategory: { fontSize: 11, fontWeight: "600", color: APP_GREEN },
  cardDesc: { fontSize: 12, color: APP_TEXT_2, lineHeight: 17 },
  metaRow: { flexDirection: "row", alignItems: "center", gap: 5 },
  metaText: { fontSize: 12, color: APP_TEXT_3 },
  progBarWrap: { gap: 5 },
  progBarTrack: { height: 6, backgroundColor: APP_GREEN_LIGHT, borderRadius: 3, overflow: "hidden" },
  progBarFill: { height: "100%", backgroundColor: APP_GREEN, borderRadius: 3 },
  progBarLabel: { fontSize: 11, color: APP_TEXT_2, textAlign: "right" },
  continueBtn: {
    flexDirection: "row", alignItems: "center", justifyContent: "center",
    gap: 6, paddingVertical: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  continueBtnText: { fontSize: 13, fontWeight: "700", color: APP_GREEN },
  enrollBtn: {
    flexDirection: "row", alignItems: "center", justifyContent: "center",
    gap: 6, backgroundColor: APP_GREEN, paddingVertical: 12, borderRadius: 12,
  },
  enrollBtnDisabled: { opacity: 0.5 },
  enrollBtnText: { fontSize: 14, fontWeight: "700", color: "#FFFFFF" },
  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
