import React, { useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { useAuth } from "@impilo/mobile-auth";
import { useQuery } from "@tanstack/react-query";
import { Card, CardBody, Header, LoadingSpinner, Screen } from "@impilo/mobile-design-system";
import {
  enrolCitizenInCourse,
  fetchCitizenLearningCatalog,
  fetchCitizenLearningSnapshot,
  normalizeCitizenLearningSnapshot,
} from "../../services/fundoLearningService";

export function FundoLearningScreen() {
  const [enrollingId, setEnrollingId] = useState<string | null>(null);
  const auth = useAuth();
  const authUser = auth.user as { sub?: string } | undefined;
  const subjectType = "USER_HEALTH_ID";
  const subjectId = authUser?.sub ?? "citizen";

  const myLearning = useQuery({
    queryKey: ["citizen-fundo", "my-learning", subjectType, subjectId],
    queryFn: () => fetchCitizenLearningSnapshot(subjectType, subjectId),
  });
  const catalog = useQuery({
    queryKey: ["citizen-fundo", "catalog"],
    queryFn: fetchCitizenLearningCatalog,
  });

  const summary = normalizeCitizenLearningSnapshot((myLearning.data ?? {}) as Record<string, unknown>);

  return (
    <Screen>
      <Header title="Fundo Learning" subtitle="Citizen learning, wellness education, and certificates" />
      <ScrollView contentContainerStyle={styles.content}>
        {myLearning.isLoading || catalog.isLoading ? (
          <View style={styles.loading}>
            <LoadingSpinner />
          </View>
        ) : null}

        <View style={styles.metricsRow}>
          {[
            { label: "In progress", value: summary.inProgress },
            { label: "Completed", value: summary.completed },
            { label: "Certificates", value: summary.certificates },
          ].map((item) => (
            <Card key={item.label} style={styles.metricCard}>
              <CardBody>
                <Text style={styles.metricValue}>{String(item.value)}</Text>
                <Text style={styles.metricLabel}>{item.label}</Text>
              </CardBody>
            </Card>
          ))}
        </View>

        <Text style={styles.sectionLabel}>Recommended catalogue</Text>
        {(catalog.data ?? []).slice(0, 8).map((course) => {
          const courseId = String(course.id ?? course.courseId ?? "");
          return (
            <TouchableOpacity
              key={courseId}
              disabled={!courseId || enrollingId === courseId}
              onPress={async () => {
                if (!courseId) return;
                try {
                  setEnrollingId(courseId);
                  await enrolCitizenInCourse(subjectType, subjectId, courseId);
                  await myLearning.refetch();
                  Alert.alert("Enrolled", "Course enrolment submitted through Fundo BFF.");
                } catch (err) {
                  Alert.alert("Enrolment failed", err instanceof Error ? err.message : "Unable to enrol");
                } finally {
                  setEnrollingId(null);
                }
              }}
            >
              <Card style={styles.courseCard}>
                <CardBody>
                  <Text style={styles.courseTitle}>{String(course.title ?? course.id ?? "Course")}</Text>
                  <Text style={styles.courseMeta}>
                    {String(course.category ?? "GENERAL")} • {String(course.level ?? "FOUNDATION")}
                  </Text>
                  <Text style={styles.enrolHint}>
                    {enrollingId === courseId ? "Enrolling…" : "Tap to enrol"}
                  </Text>
                </CardBody>
              </Card>
            </TouchableOpacity>
          );
        })}
        {(catalog.data ?? []).length === 0 && !catalog.isLoading ? (
          <Text style={styles.emptyText}>No learning items are available yet for this profile.</Text>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 10 },
  loading: { paddingVertical: 20, alignItems: "center" },
  metricsRow: { flexDirection: "row", gap: 8 },
  metricCard: { flex: 1 },
  metricValue: { fontSize: 22, fontWeight: "700", color: "#065F46", textAlign: "center" },
  metricLabel: { fontSize: 11, color: "#6B7280", textAlign: "center" },
  sectionLabel: { marginTop: 4, fontSize: 12, fontWeight: "700", color: "#6B7280", textTransform: "uppercase" },
  courseCard: { marginBottom: 8 },
  courseTitle: { fontSize: 14, fontWeight: "600", color: "#0F172A" },
  courseMeta: { marginTop: 2, fontSize: 12, color: "#64748B" },
  enrolHint: { marginTop: 6, fontSize: 11, fontWeight: "600", color: "#047857" },
  emptyText: { color: "#6B7280", fontSize: 13 },
});
