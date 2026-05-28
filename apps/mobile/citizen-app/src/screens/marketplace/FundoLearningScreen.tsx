import React from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { useAuth } from "@impilo/mobile-auth";
import { useQuery } from "@tanstack/react-query";
import { Card, CardBody, Header, LoadingSpinner, Screen } from "@impilo/mobile-design-system";
import {
  fetchCitizenLearningCatalog,
  fetchCitizenLearningSnapshot,
  normalizeCitizenLearningSnapshot,
} from "../../services/fundoLearningService";

export function FundoLearningScreen() {
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
        {(catalog.data ?? []).slice(0, 8).map((course) => (
          <Card key={String(course.id)} style={styles.courseCard}>
            <CardBody>
              <Text style={styles.courseTitle}>{String(course.title ?? course.id ?? "Course")}</Text>
              <Text style={styles.courseMeta}>
                {String(course.category ?? "GENERAL")} • {String(course.level ?? "FOUNDATION")}
              </Text>
            </CardBody>
          </Card>
        ))}
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
  emptyText: { color: "#6B7280", fontSize: 13 },
});
