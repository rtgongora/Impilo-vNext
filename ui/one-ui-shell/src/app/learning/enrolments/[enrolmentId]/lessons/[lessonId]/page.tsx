"use client";

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
  const { data: enrolData } = useFundoEnrolment(enrolmentId);
  const enrolment = ((enrolData?.data as AnyRecord)?.enrolment ?? {}) as AnyRecord;
  const courseId = String(enrolment.courseId ?? "");
  const { data: structureData } = useFundoCourseStructure(courseId || undefined);
  const { data: progressData } = useFundoEnrolmentProgress(enrolmentId);
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

  return (
    <AppLayout>
      <PageShell title={String(currentLesson.title ?? "Lesson player")} subtitle="Text/document/video/practical task rendering with completion controls.">
        <div className="rounded border border-gray-200 bg-white p-4">
          <p className="text-sm text-gray-600">Lesson ID: {lessonId}</p>
          <p className="mt-2 text-xs text-gray-500">Current progress: {currentPercent}%</p>
          <div className="mt-3 rounded border border-gray-100 bg-gray-50 p-3">
            {renderLessonBody(currentLesson)}
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <button
              onClick={() => lessonId && enrolmentId && openMutation.mutate({ lessonId, enrolmentId })}
              className="rounded bg-teal-700 px-3 py-1.5 text-sm text-white"
            >
              Open lesson
            </button>
            <button
              onClick={() =>
                enrolmentId &&
                lessonId &&
                progressMutation.mutate({ enrolmentId, lessonId, progressPercent: 100 })
              }
              className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700"
            >
              Mark complete
            </button>
          </div>
          <div className="mt-4 flex items-center justify-between text-sm">
            <div>
              {previousLesson ? (
                <Link href={`/learning/enrolments/${enrolmentId}/lessons/${previousLesson.id}`} className="text-teal-700 hover:underline">
                  Previous lesson
                </Link>
              ) : (
                <span className="text-gray-400">Previous lesson</span>
              )}
            </div>
            <Link href={`/learning/enrolments/${enrolmentId}`} className="text-gray-600 hover:underline">
              Back to course player
            </Link>
            <div>
              {nextLesson ? (
                <Link href={`/learning/enrolments/${enrolmentId}/lessons/${nextLesson.id}`} className="text-teal-700 hover:underline">
                  Next lesson
                </Link>
              ) : (
                <span className="text-gray-400">Next lesson</span>
              )}
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
