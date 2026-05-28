import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import type { Assessment } from "../../types";
import { USE_SEED_DATA, SEED_ASSESSMENTS } from "../../data/seedHealthRecords";
// When USE_SEED_DATA is false, replace seed block with:
// import { fetchAssessments } from "../../services/assessmentsService";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

type AStatus = Assessment["status"];

// Status chip — text label only. Left border is the fast-scan status indicator.
const STATUS_CFG: Record<AStatus, { label: string; color: string; bg: string }> = {
  COMPLETED:   { label: "Completed",   color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT  },
  IN_PROGRESS: { label: "In Progress", color: APP_GOLD,       bg: APP_GOLD_LIGHT   },
  NOT_STARTED: { label: "Not Started", color: APP_TEXT_2,     bg: "#EEF2F2"        },
};

const BORDER_COLOR: Record<AStatus, string> = {
  COMPLETED:   APP_GREEN,
  IN_PROGRESS: APP_GOLD,
  NOT_STARTED: APP_BORDER,
};

function ScoreBar({ score, max }: { score: number; max: number }) {
  const pct = Math.min(100, (score / max) * 100);
  const color = pct >= 70 ? APP_GREEN : pct >= 40 ? APP_GOLD : APP_RED;
  return (
    <View style={sb.wrap}>
      <View style={sb.track}>
        <View style={[sb.fill, { width: `${pct}%` as never, backgroundColor: color }]} />
      </View>
      <Text style={[sb.label, { color }]}>{score}/{max}</Text>
    </View>
  );
}

const sb = StyleSheet.create({
  wrap: { flexDirection: "row", alignItems: "center", gap: 10, marginTop: 8 },
  track: { flex: 1, height: 6, backgroundColor: "#EEF2F2", borderRadius: 3, overflow: "hidden" },
  fill: { height: "100%", borderRadius: 3 },
  label: { fontSize: 12, fontWeight: "700", width: 40, textAlign: "right" },
});

export function AssessmentsSection() {
  const [assessments, setAssessments]   = useState<Assessment[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError]               = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    setError(null);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setAssessments(SEED_ASSESSMENTS);
      } else {
        // const r = await fetchAssessments(); setAssessments(r.items);
        setAssessments([]);
      }
    } catch {
      setError("Could not load assessments. Pull down to retry.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const completed   = assessments.filter((a) => a.status === "COMPLETED").length;
  const inProgress  = assessments.filter((a) => a.status === "IN_PROGRESS").length;

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
          <Text style={[s.summaryNum, { color: APP_GOLD }]}>{inProgress}</Text>
          <Text style={s.summaryLbl}>In Progress</Text>
        </View>
        <View style={s.summaryCard}>
          <Text style={[s.summaryNum, { color: APP_TEXT_2 }]}>{assessments.length}</Text>
          <Text style={s.summaryLbl}>Total</Text>
        </View>
      </View>

      {assessments.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="clipboard-outline" size={52} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyTitle}>No assessments yet</Text>
          <Text style={s.emptySub}>Complete health assessments to help your care team understand your progress.</Text>
        </View>
      ) : (
        assessments.map((a) => {
          const cfg       = STATUS_CFG[a.status];
          const borderCol = BORDER_COLOR[a.status] ?? APP_BORDER;
          return (
            <View key={a.id} style={[s.card, { borderLeftColor: borderCol }]}>
              <View style={s.cardTop}>
                {/* Uniform teal icon — status shown only via chip text + left border */}
                <View style={s.cardIcon}>
                  <Ionicons name="clipboard-outline" size={20} color={APP_GREEN} />
                </View>
                <View style={s.cardMain}>
                  <Text style={s.assessName}>{a.name}</Text>
                  <Text style={s.assessCategory}>{a.category}</Text>
                  {a.description ? <Text style={s.assessDesc} numberOfLines={2}>{a.description}</Text> : null}
                  {a.score !== undefined && a.maxScore !== undefined ? (
                    <ScoreBar score={a.score} max={a.maxScore} />
                  ) : null}
                  {a.resultSummary ? (
                    <View style={s.resultBox}>
                      <Text style={s.resultText}>{a.resultSummary}</Text>
                    </View>
                  ) : null}
                  {a.completedAt ? (
                    <Text style={s.dateText}>
                      Completed {new Date(a.completedAt).toLocaleDateString()}
                    </Text>
                  ) : a.startedAt ? (
                    <Text style={s.dateText}>
                      Started {new Date(a.startedAt).toLocaleDateString()}
                    </Text>
                  ) : null}
                </View>
                {/* Status chip — text only */}
                <View style={[s.statusChip, { backgroundColor: cfg.bg }]}>
                  <Text style={[s.statusText, { color: cfg.color }]}>{cfg.label}</Text>
                </View>
              </View>

              {a.status === "NOT_STARTED" || a.status === "IN_PROGRESS" ? (
                <Pressable style={s.startBtn}>
                  <Text style={s.startBtnText}>
                    {a.status === "IN_PROGRESS" ? "Continue Assessment" : "Start Assessment"}
                  </Text>
                  <Ionicons name="arrow-forward" size={14} color={APP_GREEN} />
                </Pressable>
              ) : null}
            </View>
          );
        })
      )}
    </ScrollView>
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
  summaryLbl: { fontSize: 11, fontWeight: "600", color: APP_TEXT_2 },

  card: {
    backgroundColor: APP_SURFACE, borderRadius: 16, borderLeftWidth: 4,
    overflow: "hidden", shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 6, elevation: 3,
  },
  cardTop: { flexDirection: "row", alignItems: "flex-start", gap: 12, padding: 14 },

  /* Uniform teal icon */
  cardIcon: {
    width: 44, height: 44, borderRadius: 13,
    backgroundColor: APP_GREEN_XLIGHT,
    alignItems: "center", justifyContent: "center",
  },
  cardMain: { flex: 1 },
  assessName: { fontSize: 14, fontWeight: "700", color: APP_TEXT, marginBottom: 2 },
  assessCategory: { fontSize: 11, fontWeight: "600", color: APP_GREEN, marginBottom: 4 },
  assessDesc: { fontSize: 12, color: APP_TEXT_2, lineHeight: 17 },
  /* Result box — text only, no icon */
  resultBox: { backgroundColor: APP_GREEN_XLIGHT, borderRadius: 8, padding: 8, marginTop: 8 },
  resultText: { fontSize: 12, color: APP_TEXT_2, lineHeight: 17 },
  dateText: { fontSize: 11, color: APP_TEXT_3, marginTop: 6 },
  statusChip: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 10, alignSelf: "flex-start" },
  statusText: { fontSize: 11, fontWeight: "700" },

  startBtn: {
    flexDirection: "row", alignItems: "center", justifyContent: "center",
    gap: 6, paddingVertical: 12,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  startBtnText: { fontSize: 13, fontWeight: "700", color: APP_GREEN },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
