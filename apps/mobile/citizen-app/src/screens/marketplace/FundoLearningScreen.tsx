import React, { useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { useAuth } from "@impilo/mobile-auth";
import { useQuery } from "@tanstack/react-query";
import { Card, CardBody, Header, LoadingSpinner, Screen, colors } from "@impilo/mobile-design-system";
import {
  enrolCitizenInCourse,
  fetchCitizenLearningCatalog,
  fetchCitizenLearningSnapshot,
  normalizeCitizenLearningSnapshot,
} from "../../services/fundoLearningService";
import {
  isJoinableLiveSession,
  listSessions,
  type LearningSession,
} from "../../services/learningSessionsService";
import { LearningClassroomScreen } from "../learning/LearningClassroomScreen";
import { CoursePlayerScreen, type CoursePlayerEnrolment } from "../learning/CoursePlayerScreen";

export function FundoLearningScreen() {
  const [enrollingId, setEnrollingId] = useState<string | null>(null);
  const [classroomSession, setClassroomSession] = useState<LearningSession | null>(null);
  const [playerEnrolment, setPlayerEnrolment] = useState<CoursePlayerEnrolment | null>(null);
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
  // Scheduled/live classroom sessions ride a dedicated v11 endpoint — the
  // my-learning snapshot does not carry sessions, so this is its own cheap query.
  const sessions = useQuery({
    queryKey: ["citizen-fundo", "sessions"],
    queryFn: () => listSessions(),
  });

  const summary = normalizeCitizenLearningSnapshot((myLearning.data ?? {}) as Record<string, unknown>);

  if (classroomSession) {
    return (
      <LearningClassroomScreen
        session={classroomSession}
        onBack={() => setClassroomSession(null)}
      />
    );
  }

  if (playerEnrolment) {
    return <CoursePlayerScreen enrolment={playerEnrolment} onBack={() => setPlayerEnrolment(null)} />;
  }

  // Active enrolments carry recorded lessons (adopted live replays) — the
  // course player surfaces them with watch progress (W4 LEARNING_RECORDING).
  const snapshot = (myLearning.data ?? {}) as Record<string, unknown>;
  const continueLearning = ([] as Array<Record<string, unknown>>)
    .concat(Array.isArray(snapshot.inProgress) ? (snapshot.inProgress as Array<Record<string, unknown>>) : [])
    .concat(Array.isArray(snapshot.enrolled) ? (snapshot.enrolled as Array<Record<string, unknown>>) : [])
    .filter((e) => e.id && e.courseId)
    .slice(0, 5);

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

        {continueLearning.length > 0 ? (
          <View testID="learning-continue" style={styles.liveSessions}>
            <Text style={styles.sectionLabel}>Continue learning</Text>
            {continueLearning.map((enrolment) => (
              <TouchableOpacity
                key={String(enrolment.id)}
                testID={`learning-continue-${String(enrolment.id)}`}
                onPress={() =>
                  setPlayerEnrolment({
                    id: String(enrolment.id),
                    courseId: String(enrolment.courseId),
                    title: String(enrolment.courseTitle ?? "Course"),
                  })
                }
              >
                <Card style={styles.courseCard}>
                  <CardBody>
                    <Text style={styles.courseTitle}>{String(enrolment.courseTitle ?? "Course")}</Text>
                    <Text style={styles.courseMeta}>{String(enrolment.status ?? "IN_PROGRESS")}</Text>
                    <Text style={styles.enrolHint}>Tap to open recorded lessons</Text>
                  </CardBody>
                </Card>
              </TouchableOpacity>
            ))}
          </View>
        ) : null}

        {(sessions.data ?? []).length > 0 ? (
          <View testID="learning-live-sessions" style={styles.liveSessions}>
            <Text style={styles.sectionLabel}>Live sessions</Text>
            {(sessions.data ?? []).slice(0, 5).map((session) => {
              const joinable = isJoinableLiveSession(session);
              return (
                <TouchableOpacity
                  key={session.id}
                  testID={`learning-live-session-${session.id}`}
                  onPress={() => setClassroomSession(session)}
                >
                  <Card style={styles.courseCard}>
                    <CardBody>
                      <Text style={styles.courseTitle}>{session.title}</Text>
                      <Text style={styles.courseMeta}>
                        {session.sessionMode} •{" "}
                        {session.startsAt ? new Date(session.startsAt).toLocaleString() : "Schedule TBC"}
                      </Text>
                      <Text style={styles.enrolHint}>
                        {joinable ? "Tap to open the live classroom" : "Tap for session details"}
                      </Text>
                    </CardBody>
                  </Card>
                </TouchableOpacity>
              );
            })}
          </View>
        ) : null}

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
  metricValue: { fontSize: 22, fontWeight: "700", color: colors.ui.success.text, textAlign: "center" },
  metricLabel: { fontSize: 11, color: colors.gray[500], textAlign: "center" },
  sectionLabel: { marginTop: 4, fontSize: 12, fontWeight: "700", color: colors.gray[500], textTransform: "uppercase" },
  liveSessions: { gap: 8 },
  courseCard: { marginBottom: 8 },
  courseTitle: { fontSize: 14, fontWeight: "600", color: "#0F172A" },
  courseMeta: { marginTop: 2, fontSize: 12, color: "#64748B" },
  enrolHint: { marginTop: 6, fontSize: 11, fontWeight: "600", color: "#047857" },
  emptyText: { color: colors.gray[500], fontSize: 13 },
});
