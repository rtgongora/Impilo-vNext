/**
 * WellnessJourneysSection — Simba wellness DEPTH on mobile: enrol in a prevention journey,
 * see progress, do a quick habit check-in, and route a concern to care. Reuses the existing
 * NompiloGuidanceSection for guidance ("whisper" then reveal).
 *
 * Parity note: the mobile vitest runner cannot run in this environment (apps/mobile uses pnpm
 * workspace:* protocol; npm install fails with EUNSUPPORTEDPROTOCOL). This screen is written and
 * type-checked against the existing design-system + service exports, mirroring WellnessSection.tsx;
 * its tests were NOT run here (documented honest partial).
 */
import React, { useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet, ScrollView } from "react-native";
import { Card, Button, Badge, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  fetchPlans,
  fetchEnrollments,
  enrollPlan,
  checkInHabit,
  routeToCare,
} from "../../services/wellnessDepthService";
import { NompiloGuidanceSection } from "./NompiloGuidanceSection";
import { useAppStore } from "../../stores/appStore";

export function WellnessJourneysSection() {
  const profile = useAppStore((s) => s.profile);
  const patientId = profile?.cpid ?? "";
  const queryClient = useQueryClient();

  const { data: plans = [], isLoading: plansLoading } = useQuery({
    queryKey: ["wellness-plans"],
    queryFn: fetchPlans,
  });
  const { data: enrollments = [] } = useQuery({
    queryKey: ["wellness-enrollments", patientId],
    queryFn: () => fetchEnrollments(patientId),
    enabled: !!patientId,
  });

  const enrolledIds = new Set(enrollments.map((e) => e.planId));

  const enroll = useMutation({
    mutationFn: (planId: string) => enrollPlan(planId, patientId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["wellness-enrollments"] }),
  });
  const habit = useMutation({
    mutationFn: (code: string) => checkInHabit(patientId, code),
  });
  const [careResult, setCareResult] = useState<string | null>(null);
  const care = useMutation({
    mutationFn: () => routeToCare(patientId, "RISK_THRESHOLD", "ROUTINE"),
    onSuccess: (res) => {
      const status = String(res.referral_status ?? "RECORDED");
      setCareResult(
        status === "ROUTED"
          ? "A referral was created with the care team."
          : status === "EMERGENCY_ROUTED"
            ? String(res.emergency_guidance ?? "Routed to emergency.")
            : "Your concern was recorded; a referral will follow up.",
      );
    },
  });

  return (
    <ScrollView style={styles.container}>
      <NompiloGuidanceSection routePath="/wellness/plans" />

      {/* Quick habit check-in */}
      <Card>
        <View style={styles.row}>
          <Text style={styles.heading}>Today&apos;s habit</Text>
          <Button
            title={habit.isPending ? "Saving…" : "Check in: 10 min move"}
            onPress={() => habit.mutate("MOVE_10MIN")}
            disabled={!patientId || habit.isPending}
          />
        </View>
        {habit.isSuccess && <Text style={styles.success}>Nice — checked in for today.</Text>}
      </Card>

      {/* My journeys */}
      <Text style={styles.sectionTitle}>My journeys</Text>
      {enrollments.length === 0 ? (
        <Text style={styles.muted}>No journey yet — pick one below to start.</Text>
      ) : (
        enrollments.map((e) => (
          <Card key={e.enrollmentId}>
            <View style={styles.row}>
              <Text style={styles.itemTitle}>Journey {e.enrollmentId.slice(0, 8)}</Text>
              <Badge label={`${e.progressPct}%`} variant="success" />
            </View>
            <Text style={styles.muted}>{e.status}</Text>
          </Card>
        ))
      )}

      {/* Available journeys */}
      <Text style={styles.sectionTitle}>Available journeys</Text>
      {plansLoading ? (
        <LoadingSpinner />
      ) : plans.length === 0 ? (
        <Text style={styles.muted}>No journeys available for your area yet.</Text>
      ) : (
        plans.map((p) => (
          <Card key={p.planId}>
            <Text style={styles.itemTitle}>{p.name}</Text>
            <Text style={styles.muted}>{p.description}</Text>
            {p.clinicalCaution && (
              <Text style={styles.caution}>Touches a health risk — care guidance will be offered.</Text>
            )}
            <Button
              title={enrolledIds.has(p.planId) ? "Enrolled" : enroll.isPending ? "Enrolling…" : "Enrol"}
              onPress={() => enroll.mutate(p.planId)}
              disabled={enrolledIds.has(p.planId) || enroll.isPending || !patientId}
            />
          </Card>
        ))
      )}

      {/* Connect to care */}
      <Text style={styles.sectionTitle}>Something feels off?</Text>
      <Card>
        <Text style={styles.muted}>
          Wellness is not a diagnosis. Route a concern to the right care service.
        </Text>
        <Button
          title={care.isPending ? "Routing…" : "Connect to care"}
          onPress={() => care.mutate()}
          disabled={!patientId || care.isPending}
        />
        {careResult && <Text style={styles.success}>{careResult}</Text>}
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  heading: { fontSize: 16, fontWeight: "600" },
  sectionTitle: { fontSize: 14, fontWeight: "700", marginTop: 16, marginBottom: 8, color: "#374151" },
  itemTitle: { fontSize: 15, fontWeight: "600", color: "#111827" },
  muted: { fontSize: 13, color: "#6B7280", marginTop: 2 },
  caution: { fontSize: 12, color: "#B45309", marginTop: 6 },
  success: { fontSize: 13, color: "#047857", marginTop: 8 },
});
