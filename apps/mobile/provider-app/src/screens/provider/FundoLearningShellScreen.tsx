import React, { useMemo, useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet } from "react-native";
import { Button, Card, CardBody, Header, LoadingSpinner, Screen } from "@impilo/mobile-design-system";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useAppStore } from "../../stores/appStore";
import {
  createEnrolment,
  fetchCatalog,
  fetchCourseStructure,
  fetchMyLearning,
  markLessonComplete,
  openLesson,
  startEnrolment,
} from "../../services/fundoLearningService";

type AnyRecord = Record<string, unknown>;
type Stage = "home" | "catalog" | "course" | "lesson";

export function FundoLearningShellScreen() {
  const { isOnline } = useAppStore();
  const [stage, setStage] = useState<Stage>("home");
  const [activeCourseId, setActiveCourseId] = useState<string>("");
  const [activeLessonId, setActiveLessonId] = useState<string>("");
  const [activeEnrolmentId, setActiveEnrolmentId] = useState<string>("");
  const subjectType = "PROVIDER";
  const subjectId = "mobile-provider";

  const myLearning = useQuery({
    queryKey: ["mobile-fundo", "my-learning", subjectType, subjectId],
    queryFn: () => fetchMyLearning(subjectType, subjectId),
  });
  const catalog = useQuery({
    queryKey: ["mobile-fundo", "catalog"],
    queryFn: fetchCatalog,
    enabled: stage === "catalog" || stage === "course",
  });
  const course = useQuery({
    queryKey: ["mobile-fundo", "course", activeCourseId],
    queryFn: () => fetchCourseStructure(activeCourseId),
    enabled: Boolean(activeCourseId),
  });

  const enrolMutation = useMutation({
    mutationFn: (courseId: string) =>
      createEnrolment({ subjectType, subjectId, courseId, enrolmentType: "SELF", sourceSystem: "mobile-provider" }),
    onSuccess: async (enrol) => {
      const id = String(enrol.id ?? "");
      setActiveEnrolmentId(id);
      if (id) await startEnrolment(id);
    },
  });
  const openLessonMutation = useMutation({
    mutationFn: ({ lessonId, enrolmentId }: { lessonId: string; enrolmentId: string }) => openLesson(lessonId, enrolmentId),
  });
  const completeLessonMutation = useMutation({
    mutationFn: ({ lessonId, enrolmentId }: { lessonId: string; enrolmentId: string }) => markLessonComplete(enrolmentId, lessonId),
  });

  const lessons = useMemo(() => {
    const modules = ((course.data?.modules as Array<AnyRecord>) ?? []).filter(Boolean);
    return modules.flatMap((m) => ((m.lessons as Array<AnyRecord>) ?? []));
  }, [course.data]);
  const activeLesson = lessons.find((l) => String(l.id) === activeLessonId) ?? null;

  return (
    <Screen>
      <Header title="Fundo Learning (Provider)" />
      <View style={styles.modeBanner}>
        <Text style={styles.modeBannerText}>{isOnline ? "Online mode" : "Offline mode (cached learning data)"}</Text>
      </View>
      <View style={styles.navRow}>
        <Button title="Home" size="sm" variant={stage === "home" ? "primary" : "outline"} onPress={() => setStage("home")} />
        <Button title="Catalogue" size="sm" variant={stage === "catalog" ? "primary" : "outline"} onPress={() => setStage("catalog")} />
      </View>
      <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
        {stage === "home" ? (
          <Card>
            <CardBody>
              {myLearning.isLoading ? <LoadingSpinner /> : (
                <View style={styles.section}>
                  <Text style={styles.title}>My Learning Snapshot</Text>
                  <Text style={styles.meta}>In progress: {Array.isArray(myLearning.data?.inProgress) ? myLearning.data?.inProgress.length : 0}</Text>
                  <Text style={styles.meta}>Completed: {Array.isArray(myLearning.data?.completed) ? myLearning.data?.completed.length : 0}</Text>
                  <Text style={styles.meta}>Certificates: {Array.isArray(myLearning.data?.certificates) ? myLearning.data?.certificates.length : 0}</Text>
                </View>
              )}
            </CardBody>
          </Card>
        ) : null}

        {stage === "catalog" ? (
          <View style={styles.section}>
            <Text style={styles.title}>Catalogue</Text>
            {catalog.isLoading ? <LoadingSpinner /> : null}
            {(catalog.data ?? []).map((courseRow) => (
              <Pressable
                key={String(courseRow.id)}
                style={styles.item}
                onPress={() => {
                  setActiveCourseId(String(courseRow.id));
                  setStage("course");
                }}
              >
                <Text style={styles.itemTitle}>{String(courseRow.title ?? courseRow.id)}</Text>
                <Text style={styles.itemMeta}>{String(courseRow.level ?? "-")} • {String(courseRow.language ?? "-")}</Text>
              </Pressable>
            ))}
          </View>
        ) : null}

        {stage === "course" ? (
          <View style={styles.section}>
            <Text style={styles.title}>{String(course.data?.title ?? "Course detail")}</Text>
            <Button
              title={enrolMutation.isPending ? "Enrolling..." : "Enrol + Start"}
              onPress={() => activeCourseId && enrolMutation.mutate(activeCourseId)}
              disabled={!activeCourseId || enrolMutation.isPending}
            />
            <Text style={styles.meta}>Enrolment ID: {activeEnrolmentId || "-"}</Text>
            {lessons.map((lesson) => (
              <Pressable
                key={String(lesson.id)}
                style={styles.item}
                onPress={() => {
                  setActiveLessonId(String(lesson.id));
                  setStage("lesson");
                }}
              >
                <Text style={styles.itemTitle}>{String(lesson.title ?? lesson.id)}</Text>
                <Text style={styles.itemMeta}>{String(lesson.contentType ?? "TEXT")}</Text>
              </Pressable>
            ))}
          </View>
        ) : null}

        {stage === "lesson" && activeLesson ? (
          <View style={styles.section}>
            <Text style={styles.title}>{String(activeLesson.title ?? "Lesson")}</Text>
            <Text style={styles.meta}>Type: {String(activeLesson.contentType ?? "TEXT")}</Text>
            <Text style={styles.body}>{String(activeLesson.contentBody ?? activeLesson.contentRef ?? "No content available")}</Text>
            <View style={styles.navRow}>
              <Button
                title="Open lesson"
                size="sm"
                variant="outline"
                onPress={() => activeEnrolmentId && openLessonMutation.mutate({ lessonId: String(activeLesson.id), enrolmentId: activeEnrolmentId })}
                disabled={!activeEnrolmentId}
              />
              <Button
                title="Mark complete"
                size="sm"
                onPress={() => activeEnrolmentId && completeLessonMutation.mutate({ lessonId: String(activeLesson.id), enrolmentId: activeEnrolmentId })}
                disabled={!activeEnrolmentId}
              />
              <Button title="Back to course" size="sm" variant="outline" onPress={() => setStage("course")} />
            </View>
          </View>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  modeBanner: { backgroundColor: "#E0F2FE", paddingHorizontal: 12, paddingVertical: 6 },
  modeBannerText: { fontSize: 12, color: "#0C4A6E", fontWeight: "600" },
  navRow: { flexDirection: "row", gap: 8, padding: 12 },
  scroll: { flex: 1 },
  content: { padding: 12, gap: 10 },
  section: { gap: 8 },
  title: { fontSize: 16, fontWeight: "700", color: "#111827" },
  meta: { fontSize: 12, color: "#6B7280" },
  body: { fontSize: 14, color: "#374151", lineHeight: 20 },
  item: { backgroundColor: "#FFFFFF", borderColor: "#E5E7EB", borderWidth: 1, borderRadius: 10, padding: 10 },
  itemTitle: { fontSize: 14, fontWeight: "600", color: "#111827" },
  itemMeta: { fontSize: 12, color: "#6B7280", marginTop: 2 },
});
