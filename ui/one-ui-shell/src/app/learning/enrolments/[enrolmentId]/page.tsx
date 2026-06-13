"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoCourseAssessments, useFundoEnrolment, useFundoEnrolmentProgress, useIssueFundoCertificate, useStartFundoEnrolment } from "@/hooks/queries/useFundoLms";
import { useFundoCourseStructure } from "@/hooks/queries/useFundoCatalog";

type AnyRecord = Record<string, unknown>;

export default function EnrolmentPlayerPage() {
  const params = useParams<{ enrolmentId: string }>();
  const enrolmentId = params?.enrolmentId;
  const { data: enrolData } = useFundoEnrolment(enrolmentId);
  const { data: progressData } = useFundoEnrolmentProgress(enrolmentId);
  const startMutation = useStartFundoEnrolment();
  const certMutation = useIssueFundoCertificate();
  const enrolment = ((enrolData?.data as AnyRecord)?.enrolment ?? {}) as AnyRecord;
  const courseId = String(enrolment.courseId ?? "");
  const { data: structureData } = useFundoCourseStructure(courseId || undefined);
  const { data: assessmentData } = useFundoCourseAssessments(courseId || undefined);
  const progressItems = (((progressData?.data as AnyRecord)?.items as AnyRecord[]) ?? []).filter(Boolean);
  const structure = ((structureData?.data as AnyRecord)?.structure ?? {}) as AnyRecord;
  const modules: AnyRecord[] = (((structure.modules as AnyRecord[]) ?? []).filter(Boolean)).map((m) => ({
    ...m,
    lessons: (((m.lessons as AnyRecord[]) ?? []).filter(Boolean)),
  })) as AnyRecord[];
  const lessons: AnyRecord[] = modules.flatMap((m) => (m.lessons as AnyRecord[]) ?? []);
  const lessonProgress = new Map(
    progressItems
      .filter((p) => p.lessonId)
      .map((p) => [String(p.lessonId), Number(p.progressPercent ?? 0)]),
  );
  const completedCount = lessons.filter((l) => (lessonProgress.get(String(l.id)) ?? 0) >= 100).length;
  const percent = lessons.length ? Math.round((completedCount * 100) / lessons.length) : 0;
  const firstIncompleteLesson = lessons.find((l) => (lessonProgress.get(String(l.id)) ?? 0) < 100);
  const currentLesson: AnyRecord | undefined = firstIncompleteLesson ?? lessons[0];
  const assessments = (((assessmentData?.data as AnyRecord)?.items as AnyRecord[]) ?? []).filter(Boolean);
  const canIssueCertificate = String(enrolment.status) === "COMPLETED" || percent >= 100;

  return (
    <AppLayout>
      <PageShell title="Enrolment" subtitle={`Course player shell • ${String(enrolment.status ?? "ENROLLED")}`}>
        <div className="mb-3 flex flex-wrap gap-2">
          <button
            onClick={() => enrolmentId && startMutation.mutate(enrolmentId)}
            className="rounded bg-primary px-3 py-1.5 text-sm text-white"
          >
            Start / Continue
          </button>
          {currentLesson ? (
            <Link
              className="rounded border border-teal-700 px-3 py-1.5 text-sm text-teal-700 hover:bg-teal-50"
              href={`/learning/enrolments/${enrolmentId}/lessons/${String(currentLesson.id)}`}
            >
              Open current lesson
            </Link>
          ) : null}
          <button
            onClick={() => enrolmentId && canIssueCertificate && certMutation.mutate(enrolmentId)}
            className="rounded border border-border px-3 py-1.5 text-sm text-foreground disabled:cursor-not-allowed disabled:opacity-50"
            disabled={!canIssueCertificate}
            data-testid="fundo-issue-certificate"
          >
            Issue certificate
          </button>
        </div>
        <div className="rounded border border-border bg-card p-4">
          <p className="text-sm text-muted-foreground">Progress</p>
          <p className="text-lg font-semibold text-foreground">{percent}%</p>
          <div className="mt-2 h-2 rounded bg-neutral-100">
            <div className="h-2 rounded bg-teal-600" style={{ width: `${Math.max(0, Math.min(100, percent))}%` }} />
          </div>
          <p className="mt-2 text-xs text-muted-foreground">{completedCount} of {lessons.length} lessons completed</p>
          {modules.length === 0 ? <p className="mt-2 text-sm text-muted-foreground">No course structure available yet.</p> : null}
          <div className="mt-3 space-y-3">
            {modules.map((module: AnyRecord) => (
              <div key={String(module.id)} className="rounded border border-border p-2">
                <p className="text-sm font-medium text-foreground">{String(module.title ?? "Module")}</p>
                <ul className="mt-2 space-y-1 text-sm">
                  {(((module.lessons as AnyRecord[]) ?? []).map((lesson: AnyRecord) => {
                    const lp = lessonProgress.get(String(lesson.id)) ?? 0;
                    return (
                      <li key={String(lesson.id)} className="flex items-center justify-between gap-3">
                        <span className="text-foreground">{String(lesson.title ?? "Lesson")}</span>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-muted-foreground">{lp}%</span>
                          <Link className="text-teal-700 hover:underline" href={`/learning/enrolments/${enrolmentId}/lessons/${lesson.id}`}>
                            Open
                          </Link>
                        </div>
                      </li>
                    );
                  }))}
                </ul>
              </div>
            ))}
          </div>
        </div>
        <div className="mt-4 rounded border border-border bg-card p-4">
          <p className="text-sm font-medium text-foreground">Assessment prompts</p>
          {assessments.length === 0 ? (
            <p className="mt-2 text-sm text-muted-foreground">No assessments linked to this course yet.</p>
          ) : (
            <ul className="mt-2 space-y-1 text-sm">
              {assessments.map((assessment) => (
                <li key={String(assessment.id)} className="flex items-center justify-between">
                  <span>{String(assessment.title ?? "Assessment")}</span>
                  <Link href={`/learning/assessments/${assessment.id}`} className="text-teal-700 hover:underline">
                    Open assessment
                  </Link>
                </li>
              ))}
            </ul>
          )}
          {canIssueCertificate ? (
            <p className="mt-3 text-xs text-primary-hover">Completion criteria met. Certificate issuance is enabled.</p>
          ) : (
            <p className="mt-3 text-xs text-muted-foreground">Certificate can be issued only after completion.</p>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
