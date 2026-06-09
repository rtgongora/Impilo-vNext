"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoEnrolment, useFundoEnrolmentProgress, useOpenFundoLesson, useRecordFundoProgress } from "@/hooks/queries/useFundoLms";
import { useFundoCourseStructure } from "@/hooks/queries/useFundoCatalog";
type AnyRecord = Record<string, unknown>;

function renderLessonBody(lesson: AnyRecord) {
  const contentType = String(lesson.contentType ?? "TEXT");
  const contentFormat = String(lesson.contentFormat ?? "PLAIN_TEXT");
  const body = String(lesson.contentBody ?? "");
  const ref = String(lesson.contentRef ?? "");
  const blocksRaw = String(lesson.contentBlocksJson ?? "");
  let blocks: Array<Record<string, unknown>> = [];
  if (blocksRaw) {
    try {
      const parsed = JSON.parse(blocksRaw);
      if (Array.isArray(parsed)) blocks = parsed as Array<Record<string, unknown>>;
    } catch {
      blocks = [];
    }
  }
  if (contentFormat === "STRUCTURED_BLOCKS" && blocks.length > 0) {
    return (
      <div className="space-y-2">
        {blocks.map((block, idx) => (
          <div key={`${String(block.type ?? "block")}-${idx}`}>
            {String(block.type) === "heading" ? (
              <h3 className="text-sm font-semibold text-gray-900">{String(block.text ?? "")}</h3>
            ) : (
              <p className="text-sm whitespace-pre-wrap text-gray-700">{String(block.text ?? "")}</p>
            )}
          </div>
        ))}
      </div>
    );
  }
  if (contentType === "TEXT") return <p className="text-sm whitespace-pre-wrap text-gray-700">{body || "No text content provided."}</p>;
  if (contentType === "DOCUMENT") return <p className="text-sm text-gray-700">Document reference: <a className="text-teal-700 hover:underline" href={ref} target="_blank" rel="noreferrer">{ref || "No document link"}</a></p>;
  if (contentType === "LINK") return <p className="text-sm text-gray-700">External link: <a className="text-teal-700 hover:underline" href={ref} target="_blank" rel="noreferrer">{ref || "No link provided"}</a></p>;
  if (contentType === "VIDEO") return <p className="text-sm text-gray-700">Video reference: <a className="text-teal-700 hover:underline" href={ref} target="_blank" rel="noreferrer">{ref || "No video URL provided"}</a></p>;
  return <p className="text-sm text-gray-700">Practical task placeholder: {body || ref || "No details provided."}</p>;
}

export default function LessonPlayerPage() {
  const params = useParams<{ enrolmentId: string; lessonId: string }>();
  const enrolmentId = params?.enrolmentId;
  const lessonId = params?.lessonId;
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const { data: enrolData, refetch: refetchEnrolment } = useFundoEnrolment(enrolmentId);
  const enrolment = ((enrolData?.data as AnyRecord)?.enrolment ?? {}) as AnyRecord;
  const courseId = String(enrolment.courseId ?? "");
  const { data: structureData } = useFundoCourseStructure(courseId || undefined);
  const { data: progressData, refetch: refetchProgress } = useFundoEnrolmentProgress(enrolmentId);
  const openMutation = useOpenFundoLesson();
  const progressMutation = useRecordFundoProgress();
  const structure = ((structureData?.data as AnyRecord)?.structure ?? {}) as AnyRecord;
  const modules = (structure.modules as AnyRecord[]) ?? [];
  const lessons = modules.flatMap((m) => (m.lessons as AnyRecord[]) ?? []).filter(Boolean);
  const currentIdx = lessons.findIndex((l) => String(l.id) === lessonId);
  const currentLesson = currentIdx >= 0 ? lessons[currentIdx] : ({} as AnyRecord);
  const previousLesson = currentIdx > 0 ? lessons[currentIdx - 1] : null;
  const nextLesson = currentIdx >= 0 && currentIdx < lessons.length - 1 ? lessons[currentIdx + 1] : null;
  const progressItems = (((progressData?.data as AnyRecord)?.items as AnyRecord[]) ?? []).filter(Boolean);
  const currentProgress = progressItems.find((p) => String(p.lessonId ?? "") === String(lessonId));
  const currentPercent = Number(currentProgress?.progressPercent ?? 0);
  const courseTitle = String(structure.title ?? enrolment.courseTitle ?? "Course");
  const mutationError = openMutation.error ?? progressMutation.error;

  function readableError(err: unknown) {
    if (err && typeof err === "object" && "message" in err) return String((err as { message?: unknown }).message);
    return "The learning service did not accept the update. Please try again.";
  }

  function openLesson() {
    if (!lessonId || !enrolmentId) return;
    setMessage("");
    setError("");
    openMutation.mutate(
      { lessonId, enrolmentId },
      {
        onSuccess: () => {
          setMessage("Lesson opened and progress refreshed.");
          void refetchProgress();
          void refetchEnrolment();
        },
        onError: (err) => setError(readableError(err)),
      },
    );
  }

  function markComplete() {
    if (!lessonId || !enrolmentId) return;
    setMessage("");
    setError("");
    progressMutation.mutate(
      { enrolmentId, lessonId, progressPercent: 100 },
      {
        onSuccess: () => {
          setMessage("Lesson marked complete.");
          void refetchProgress();
          void refetchEnrolment();
        },
        onError: (err) => setError(readableError(err)),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title={String(currentLesson.title ?? "Lesson player")} subtitle={courseTitle}>
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_280px]">
          <article className="rounded-lg border border-gray-200 bg-white p-4">
            <div className="rounded-lg border border-gray-100 bg-gray-50 p-4">
              {renderLessonBody(currentLesson)}
            </div>
          </article>

          <aside className="space-y-3">
            <section className="rounded-lg border border-gray-200 bg-white p-4">
              <p className="text-xs font-semibold uppercase tracking-normal text-gray-500">Progress</p>
              <p className="mt-2 text-2xl font-semibold text-gray-900">{currentPercent}%</p>
              <div className="mt-3 h-2 rounded bg-gray-200">
                <div className="h-2 rounded bg-teal-600" style={{ width: `${Math.max(0, Math.min(100, currentPercent))}%` }} />
              </div>
              <div className="mt-4 grid gap-2">
                <button
                  type="button"
                  onClick={openLesson}
                  disabled={!lessonId || !enrolmentId || openMutation.isPending}
                  className="rounded-lg bg-teal-700 px-3 py-2 text-sm font-medium text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {openMutation.isPending ? "Opening..." : "Open lesson"}
                </button>
                <button
                  type="button"
                  onClick={markComplete}
                  disabled={!lessonId || !enrolmentId || progressMutation.isPending || currentPercent >= 100}
                  className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {progressMutation.isPending ? "Saving..." : currentPercent >= 100 ? "Completed" : "Mark complete"}
                </button>
              </div>
              {message ? <p className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-xs text-emerald-700">{message}</p> : null}
              {error || mutationError ? <p className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-700">{error || readableError(mutationError)}</p> : null}
            </section>

            <section className="rounded-lg border border-gray-200 bg-white p-4 text-sm">
              <div className="grid gap-2">
                {previousLesson ? (
                  <Link href={`/learning/enrolments/${enrolmentId}/lessons/${previousLesson.id}`} className="rounded-lg border border-gray-200 px-3 py-2 text-gray-700 hover:bg-gray-50">
                    Previous lesson
                  </Link>
                ) : (
                  <span className="rounded-lg border border-gray-100 px-3 py-2 text-gray-400">Previous lesson</span>
                )}
                {nextLesson ? (
                  <Link href={`/learning/enrolments/${enrolmentId}/lessons/${nextLesson.id}`} className="rounded-lg border border-teal-200 px-3 py-2 text-teal-700 hover:bg-teal-50">
                    Next lesson
                  </Link>
                ) : (
                  <span className="rounded-lg border border-gray-100 px-3 py-2 text-gray-400">Next lesson</span>
                )}
                <Link href={`/learning/enrolments/${enrolmentId}`} className="rounded-lg border border-gray-200 px-3 py-2 text-gray-700 hover:bg-gray-50">
                  Back to course player
                </Link>
              </div>
            </section>
          </aside>
        </div>
      </PageShell>
    </AppLayout>
  );
}
