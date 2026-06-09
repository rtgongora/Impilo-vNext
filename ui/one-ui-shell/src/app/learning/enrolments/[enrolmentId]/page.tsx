"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Award, BookOpenCheck, CheckCircle2, Clock, PlayCircle, XCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import {
  useCancelFundoEnrolment,
  useCreateFundoEnrolment,
  useFundoCourseAssessments,
  useFundoEnrolment,
  useFundoEnrolmentProgress,
  useIssueFundoCertificate,
  useStartFundoEnrolment,
} from "@/hooks/queries/useFundoLms";
import { useFundoCourseStructure } from "@/hooks/queries/useFundoCatalog";

type AnyRecord = Record<string, unknown>;

export default function EnrolmentPlayerPage() {
  const params = useParams<{ enrolmentId: string }>();
  const router = useRouter();
  const enrolmentId = params?.enrolmentId;
  const subject = useLearningSubject();
  const [confirmCancelOpen, setConfirmCancelOpen] = useState(false);
  const { data: enrolData, refetch: refetchEnrolment } = useFundoEnrolment(enrolmentId);
  const { data: progressData, refetch: refetchProgress } = useFundoEnrolmentProgress(enrolmentId);
  const startMutation = useStartFundoEnrolment();
  const cancelMutation = useCancelFundoEnrolment();
  const createEnrolment = useCreateFundoEnrolment();
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
  const status = String(enrolment.status ?? "ENROLLED");
  const canCancel = !["COMPLETED", "CANCELLED"].includes(status);
  const canReEnrol = status === "CANCELLED" && Boolean(courseId);
  const needsStart = status === "ENROLLED";
  const currentModule = modules.find((module) => (((module.lessons as AnyRecord[]) ?? []).some((lesson) => String(lesson.id) === String(currentLesson?.id))));

  return (
    <AppLayout>
      <PageShell
        title={String(structure.title ?? "Enrolment")}
        subtitle={`Course player • ${String(enrolment.status ?? "ENROLLED")}`}
        actions={
          <Link href="/learning/my-learning" className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
            My learning
          </Link>
        }
      >
        <div className="grid min-h-[560px] gap-4 lg:grid-cols-[320px_minmax(0,1fr)] 2xl:grid-cols-[360px_minmax(0,1fr)]">
          <aside className="rounded-lg border border-gray-200 bg-white lg:sticky lg:top-4 lg:self-start">
            <div className="border-b border-gray-100 p-4">
              <p className="text-sm font-semibold text-gray-900">Course progress</p>
              <div className="mt-3 flex items-end justify-between gap-3">
                <p className="text-3xl font-semibold text-gray-900">{percent}%</p>
                <p className="pb-1 text-xs text-gray-500">{completedCount} of {lessons.length} lessons</p>
              </div>
              <div className="mt-3 h-2 rounded bg-gray-200">
                <div className="h-2 rounded bg-teal-600" style={{ width: `${Math.max(0, Math.min(100, percent))}%` }} />
              </div>
            </div>

            <div className="max-h-[460px] overflow-auto p-3 lg:max-h-[calc(100vh-260px)]">
              {modules.length === 0 ? <p className="p-2 text-sm text-gray-500">No course structure available yet.</p> : null}
              <div className="space-y-3">
                {modules.map((module: AnyRecord, moduleIndex) => (
                  <section key={String(module.id)} className="rounded-lg border border-gray-100 bg-gray-50 p-3">
                    <p className="text-xs font-semibold uppercase tracking-normal text-gray-500">Module {moduleIndex + 1}</p>
                    <p className="mt-1 text-sm font-semibold text-gray-900">{String(module.title ?? "Module")}</p>
                    <ul className="mt-3 space-y-1">
                      {(((module.lessons as AnyRecord[]) ?? []).map((lesson: AnyRecord) => {
                        const lp = lessonProgress.get(String(lesson.id)) ?? 0;
                        const active = String(lesson.id) === String(currentLesson?.id);
                        return (
                          <li key={String(lesson.id)}>
                            <Link
                              className={
                                active
                                  ? "flex items-center justify-between gap-3 rounded-md border border-teal-200 bg-white px-2 py-2 text-sm text-teal-800 shadow-sm"
                                  : "flex items-center justify-between gap-3 rounded-md px-2 py-2 text-sm text-gray-700 hover:bg-white"
                              }
                              href={`/learning/enrolments/${enrolmentId}/lessons/${lesson.id}`}
                            >
                              <span className="flex min-w-0 items-center gap-2">
                                {lp >= 100 ? <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" /> : <BookOpenCheck className="h-4 w-4 shrink-0 text-gray-400" />}
                                <span className="truncate">{String(lesson.title ?? "Lesson")}</span>
                              </span>
                              <span className="shrink-0 text-xs text-gray-500">{lp}%</span>
                            </Link>
                          </li>
                        );
                      }))}
                    </ul>
                  </section>
                ))}
              </div>
            </div>
          </aside>

          <div className="space-y-4">
            <section className="rounded-lg border border-gray-200 bg-white p-5">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div className="min-w-0">
                  <p className="text-xs font-semibold uppercase tracking-normal text-gray-500">Current lesson</p>
                  <h2 className="mt-2 text-2xl font-semibold text-gray-900">{String(currentLesson?.title ?? "No lesson selected")}</h2>
                  <p className="mt-2 text-sm text-gray-600">
                    {currentModule ? String(currentModule.title ?? "Module") : "Start the first available lesson to begin this course."}
                  </p>
                  {currentLesson?.estimatedDurationMinutes ? (
                    <p className="mt-3 inline-flex items-center gap-1 text-sm text-gray-500">
                      <Clock className="h-4 w-4" /> {String(currentLesson.estimatedDurationMinutes)} min
                    </p>
                  ) : null}
                </div>
                <div className="flex flex-wrap gap-2">
                  {needsStart ? (
                    <button
                      onClick={() =>
                        enrolmentId &&
                        startMutation.mutate(enrolmentId, {
                          onSuccess: () => {
                            void refetchEnrolment();
                            void refetchProgress();
                          },
                        })
                      }
                      className="inline-flex items-center gap-2 rounded-lg bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                      disabled={startMutation.isPending}
                    >
                      <PlayCircle className="h-4 w-4" /> Start course
                    </button>
                  ) : currentLesson && status !== "CANCELLED" ? (
                    <Link
                      className="inline-flex items-center gap-2 rounded-lg bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700"
                      href={`/learning/enrolments/${enrolmentId}/lessons/${String(currentLesson.id)}`}
                    >
                      <PlayCircle className="h-4 w-4" /> Continue lesson
                    </Link>
                  ) : null}
                  {canReEnrol ? (
                    <button
                      onClick={() =>
                        createEnrolment.mutate(
                          {
                            courseId,
                            subjectType: subject.subjectType,
                            subjectId: subject.subjectId,
                            enrolmentType: "SELF",
                          },
                          {
                            onSuccess: (res) => {
                              const envelope = (res as Record<string, unknown>).data as Record<string, unknown> | undefined;
                              const next = envelope?.enrolment as Record<string, unknown> | undefined;
                              const nextId = String(next?.id ?? "");
                              if (nextId) router.push(`/learning/enrolments/${nextId}`);
                            },
                          },
                        )
                      }
                      className="inline-flex items-center gap-2 rounded-lg bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                      disabled={createEnrolment.isPending}
                    >
                      Re-enrol
                    </button>
                  ) : null}
                </div>
              </div>
            </section>

            <section className="grid gap-3 md:grid-cols-3">
              <div className="rounded-lg border border-gray-200 bg-white p-4">
                <p className="text-xs text-gray-500">Status</p>
                <p className="mt-2 text-lg font-semibold text-gray-900">{String(enrolment.status ?? "ENROLLED")}</p>
              </div>
              <div className="rounded-lg border border-gray-200 bg-white p-4">
                <p className="text-xs text-gray-500">Lessons remaining</p>
                <p className="mt-2 text-lg font-semibold text-gray-900">{Math.max(0, lessons.length - completedCount)}</p>
              </div>
              <div className="rounded-lg border border-gray-200 bg-white p-4">
                <p className="text-xs text-gray-500">Assessments</p>
                <p className="mt-2 text-lg font-semibold text-gray-900">{assessments.length}</p>
              </div>
            </section>

            <section className="rounded-lg border border-gray-200 bg-white p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-gray-900">Assessments and certificate</p>
                  <p className="mt-1 text-xs text-gray-500">Complete lessons first, then attempt assessments and issue certificate when eligible.</p>
                </div>
                <button
                  onClick={() =>
                    enrolmentId &&
                    canIssueCertificate &&
                    certMutation.mutate(enrolmentId, {
                      onSuccess: () => void refetchEnrolment(),
                    })
                  }
                  className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={!canIssueCertificate}
                >
                  <Award className="h-4 w-4" /> Issue certificate
                </button>
              </div>
              {assessments.length === 0 ? (
                <p className="mt-4 rounded-lg border border-dashed border-gray-200 px-3 py-4 text-sm text-gray-500">No assessments linked to this course yet.</p>
              ) : (
                <ul className="mt-4 grid gap-2 md:grid-cols-2">
                  {assessments.map((assessment) => (
                    <li key={String(assessment.id)} className="flex items-center justify-between gap-3 rounded-lg border border-gray-100 bg-gray-50 px-3 py-2 text-sm">
                      <span className="font-medium text-gray-900">{String(assessment.title ?? "Assessment")}</span>
                      <Link href={`/learning/assessments/${assessment.id}`} className="text-teal-700 hover:underline">Open</Link>
                    </li>
                  ))}
                </ul>
              )}
              {canIssueCertificate ? (
                <p className="mt-3 text-xs text-emerald-700">Completion criteria met. Certificate issuance is enabled.</p>
              ) : (
                <p className="mt-3 text-xs text-gray-500">Certificate can be issued only after completion.</p>
              )}
            </section>

            <section className="rounded-lg border border-gray-200 bg-white p-4">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-sm font-semibold text-gray-900">Course actions</p>
                  <p className="mt-1 text-xs text-gray-500">Navigation is kept separate from enrolment cancellation.</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Link
                    href="/learning/my-learning"
                    className="inline-flex items-center justify-center rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                  >
                    Back to My learning
                  </Link>
                  <button
                    onClick={() => setConfirmCancelOpen(true)}
                    className="inline-flex items-center gap-2 rounded-lg border border-rose-200 px-3 py-2 text-sm font-medium text-rose-700 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-50"
                    disabled={!canCancel || cancelMutation.isPending}
                  >
                    <XCircle className="h-4 w-4" /> Cancel enrolment
                  </button>
                </div>
              </div>
            </section>
          </div>
        </div>
        {confirmCancelOpen ? (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-gray-950/40 px-4 backdrop-blur-sm">
            <section className="w-full max-w-md rounded-lg border border-gray-200 bg-white p-5 shadow-xl">
              <p className="text-base font-semibold text-gray-900">Cancel enrolment?</p>
              <p className="mt-2 text-sm leading-6 text-gray-600">
                This will cancel the current enrolment and stop it from appearing as active learning. You can re-enrol afterwards if this was a mistake.
              </p>
              <div className="mt-5 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setConfirmCancelOpen(false)}
                  className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                >
                  Keep enrolment
                </button>
                <button
                  type="button"
                  onClick={() =>
                    enrolmentId &&
                    cancelMutation.mutate(
                      { enrolmentId, reason: "Cancelled from Fundo learner workspace" },
                      {
                        onSuccess: () => {
                          setConfirmCancelOpen(false);
                          void refetchEnrolment();
                        },
                      },
                    )
                  }
                  className="rounded-lg bg-rose-600 px-3 py-2 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-50"
                  disabled={cancelMutation.isPending}
                >
                  Cancel enrolment
                </button>
              </div>
            </section>
          </div>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
