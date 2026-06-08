import React, { useEffect, useMemo, useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet } from "react-native";
import { Button, Card, CardBody, Header, LoadingSpinner, Screen } from "@impilo/mobile-design-system";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useAuth } from "@impilo/mobile-auth";
import { useAppStore } from "../../stores/appStore";
import {
  createEnrolment,
  fetchCatalog,
  fetchCourseAssessments,
  fetchCourseStructure,
  fetchCpdEvidence,
  fetchMyLearning,
  fetchPathways,
  markLessonComplete,
  openLesson,
  startEnrolment,
  summarizeCpdCredits,
} from "../../services/fundoLearningService";

type AnyRecord = Record<string, unknown>;
type Stage = "home" | "catalog" | "pathways" | "course" | "lesson" | "assessment";

export function FundoLearningShellScreen() {
  const auth = useAuth();
  const { isOnline, appsFocus, learningSubjectType, learningSubjectId } = useAppStore();
  const [stage, setStage] = useState<Stage>("home");
  const [activeCourseId, setActiveCourseId] = useState<string>("");
  const [activePathwayId, setActivePathwayId] = useState<string>("");
  const [activeLessonId, setActiveLessonId] = useState<string>("");
  const [activeEnrolmentId, setActiveEnrolmentId] = useState<string>("");
  const [activeAssessmentId, setActiveAssessmentId] = useState<string>("");
  const authUser = auth.user as { sub?: string; provider_id?: string; providerId?: string } | undefined;
  const subjectType = learningSubjectType ?? "PROVIDER";
  const subjectId = learningSubjectId ?? authUser?.provider_id ?? authUser?.providerId ?? authUser?.sub ?? "mobile-provider";

  const myLearning = useQuery({
    queryKey: ["mobile-fundo", "my-learning", subjectType, subjectId],
    queryFn: () => fetchMyLearning(subjectType, subjectId),
  });
  const cpdEvidence = useQuery({
    queryKey: ["mobile-fundo", "cpd", subjectType, subjectId],
    queryFn: () => fetchCpdEvidence(subjectType, subjectId),
  });
  const catalog = useQuery({
    queryKey: ["mobile-fundo", "catalog"],
    queryFn: fetchCatalog,
    enabled: stage === "catalog" || stage === "course",
  });
  const pathways = useQuery({
    queryKey: ["mobile-fundo", "pathways"],
    queryFn: fetchPathways,
    enabled: stage === "pathways" || stage === "home",
  });
  const course = useQuery({
    queryKey: ["mobile-fundo", "course", activeCourseId],
    queryFn: () => fetchCourseStructure(activeCourseId),
    enabled: Boolean(activeCourseId),
  });
  const assessments = useQuery({
    queryKey: ["mobile-fundo", "assessments", activeCourseId],
    queryFn: () => fetchCourseAssessments(activeCourseId),
    enabled: Boolean(activeCourseId) && (stage === "course" || stage === "assessment"),
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
  const cpdCredits = summarizeCpdCredits(cpdEvidence.data ?? {});
  const activeAssessment = (assessments.data ?? []).find((a) => String(a.id) === activeAssessmentId) ?? null;

  useEffect(() => {
    if (appsFocus && stage === "home") {
      setStage("catalog");
    }
  }, [appsFocus, stage]);

  return (
    <Screen>
      <Header title="Fundo Learning (Provider)" />
      <View style={styles.modeBanner}>
        <Text style={styles.modeBannerText}>{isOnline ? "Online mode" : "Offline mode (cached learning data)"}</Text>
      </View>
      {appsFocus ? (
        <View style={styles.focusBanner}>
          <Text style={styles.focusBannerText}>
            {appsFocus === "required"
              ? "Required learning focus"
              : appsFocus === "overdue"
                ? "Overdue learning focus"
                : "CPD evidence focus"}
          </Text>
        </View>
      ) : null}
      <View style={styles.navRow}>
        <Button title="Home" size="sm" variant={stage === "home" ? "primary" : "outline"} onPress={() => setStage("home")} />
        <Button title="Catalogue" size="sm" variant={stage === "catalog" ? "primary" : "outline"} onPress={() => setStage("catalog")} />
        <Button title="Pathways" size="sm" variant={stage === "pathways" ? "primary" : "outline"} onPress={() => setStage("pathways")} />
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
                  <Text style={styles.meta}>
                    CPD credits: {cpdEvidence.isLoading ? "…" : cpdCredits}
                  </Text>
                  <Text style={styles.meta}>
                    Pathways: {pathways.isLoading ? "…" : (pathways.data ?? []).length} published
                  </Text>
                </View>
              )}
            </CardBody>
          </Card>
        ) : null}

        {stage === "pathways" ? (
          <View style={styles.section}>
            <Text style={styles.title}>Learning pathways</Text>
            {pathways.isLoading ? <LoadingSpinner /> : null}
            {(pathways.data ?? []).length === 0 && !pathways.isLoading ? (
              <Text style={styles.meta}>No published pathways from BFF.</Text>
            ) : null}
            {(pathways.data ?? []).map((pathway) => (
              <Pressable
                key={String(pathway.id)}
                style={styles.item}
                onPress={() => {
                  setActivePathwayId(String(pathway.id));
                }}
              >
                <Text style={styles.itemTitle}>{String(pathway.title ?? pathway.name ?? pathway.id)}</Text>
                <Text style={styles.itemMeta}>{String(pathway.status ?? "PUBLISHED")}</Text>
                {activePathwayId === String(pathway.id) ? (
                  <Text style={styles.body}>{String(pathway.description ?? "Pathway detail from /learning/v11/pathways")}</Text>
                ) : null}
              </Pressable>
            ))}
          </View>
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
            <Text style={styles.meta}>CPD credits (evidence): {cpdCredits}</Text>
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
            <Text style={[styles.title, { marginTop: 12 }]}>Assessments</Text>
            {assessments.isLoading ? <LoadingSpinner /> : null}
            {(assessments.data ?? []).length === 0 && !assessments.isLoading ? (
              <Text style={styles.meta}>No assessments returned for this course.</Text>
            ) : null}
            {(assessments.data ?? []).map((assessment) => (
              <Pressable
                key={String(assessment.id)}
                style={styles.item}
                onPress={() => {
                  setActiveAssessmentId(String(assessment.id));
                  setStage("assessment");
                }}
              >
                <Text style={styles.itemTitle}>{String(assessment.title ?? assessment.id)}</Text>
                <Text style={styles.itemMeta}>{String(assessment.assessmentType ?? assessment.type ?? "ASSESSMENT")}</Text>
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

        {stage === "assessment" && activeAssessment ? (
          <View style={styles.section}>
            <Text style={styles.title}>{String(activeAssessment.title ?? "Assessment")}</Text>
            <Text style={styles.meta}>
              BFF: GET /internal/v1/learning/v11/assessments/{String(activeAssessment.id)}
            </Text>
            <Text style={styles.body}>
              Assessment attempt UI opens on web at /learning/assessments/{String(activeAssessment.id)}/attempt.
              Mobile placeholder wired to course assessments endpoint; start attempt when enrolment is active.
            </Text>
            <Text style={styles.meta}>Enrolment: {activeEnrolmentId || "Enrol in course first"}</Text>
            <Button title="Back to course" size="sm" variant="outline" onPress={() => setStage("course")} />
          </View>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  modeBanner: { backgroundColor: "#E0F2FE", paddingHorizontal: 12, paddingVertical: 6 },
  modeBannerText: { fontSize: 12, color: "#0C4A6E", fontWeight: "600" },
  focusBanner: { backgroundColor: "#ECFEFF", paddingHorizontal: 12, paddingVertical: 6, borderBottomColor: "#A5F3FC", borderBottomWidth: 1 },
  focusBannerText: { fontSize: 12, color: "#0F766E", fontWeight: "700" },
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
