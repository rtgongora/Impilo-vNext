"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoLearningRecord, useFundoProgress } from "@/hooks/queries/useFundoLms";

function recordTitle(row: Record<string, unknown>) {
  return String(row.courseTitle ?? row.title ?? row.courseCode ?? row.certificateNumber ?? "Course");
}

function RecordList({
  title,
  rows,
  emptyText,
  renderMeta,
}: {
  title: string;
  rows: Array<Record<string, unknown>>;
  emptyText: string;
  renderMeta: (row: Record<string, unknown>) => string;
}) {
  return (
    <section className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-gray-900">{title}</p>
        <span className="rounded-full border border-gray-200 px-2 py-0.5 text-xs font-medium text-gray-500">{rows.length}</span>
      </div>
      <ul className="space-y-2 text-sm">
        {rows.slice(0, 20).map((row) => (
          <li key={String(row.id ?? row.courseId ?? row.certificateNumber)} className="flex items-start justify-between gap-3 rounded-lg border border-gray-100 bg-gray-50 px-3 py-2">
            <span className="min-w-0 font-medium text-gray-900">{recordTitle(row)}</span>
            <span className="shrink-0 text-xs text-gray-500">{renderMeta(row)}</span>
          </li>
        ))}
        {rows.length === 0 ? <li className="rounded-lg border border-dashed border-gray-200 px-3 py-4 text-gray-500">{emptyText}</li> : null}
      </ul>
    </section>
  );
}

export default function LearningRecordPage() {
  const subject = useLearningSubject();
  const { data } = useFundoLearningRecord(subject);
  const { data: progressData } = useFundoProgress(subject);
  const record = ((data?.data as Record<string, unknown>)?.record as Record<string, unknown>) ?? {};
  const enrolments = ((record.enrolments as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  const progressRows = ((record.progressRows as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  const liveProgressRows = ((((progressData?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? [])).filter(Boolean);
  const assessmentAttempts = ((record.assessmentAttempts as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  const certificates = ((record.certificates as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  const cpdEligibleCompletions = ((record.cpdEligibleCompletions as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  const completedEnrolments = enrolments.filter((e) => String(e.status) === "COMPLETED");

  return (
    <AppLayout>
      <PageShell
        title="Learning record / transcript"
        subtitle="Native Fundo learner transcript including completions, assessments and certificates."
        actions={
          <Link href="/learning/my-learning" className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
            Back to My learning
          </Link>
        }
      >
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <p className="text-xs text-gray-500">Completed courses</p>
            <p className="mt-2 text-2xl font-semibold text-gray-900">{completedEnrolments.length}</p>
          </div>
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <p className="text-xs text-gray-500">Assessment attempts</p>
            <p className="mt-2 text-2xl font-semibold text-gray-900">{assessmentAttempts.length}</p>
          </div>
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <p className="text-xs text-gray-500">Certificates</p>
            <p className="mt-2 text-2xl font-semibold text-gray-900">{certificates.length}</p>
          </div>
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <p className="text-xs text-gray-500">Progress rows</p>
            <p className="mt-2 text-2xl font-semibold text-gray-900">{progressRows.length || liveProgressRows.length}</p>
            <p className="mt-1 text-xs text-gray-500">Transcript aggregate plus live progress endpoint.</p>
          </div>
        </div>

        <div className="mt-5 grid gap-5 lg:grid-cols-2">
          <RecordList title="Enrolments" rows={enrolments} emptyText="No enrolments yet." renderMeta={(row) => String(row.status ?? "-")} />
          <RecordList title="Certificates" rows={certificates} emptyText="No certificates yet." renderMeta={(row) => String(row.status ?? "-")} />
          <RecordList title="CPD eligible completions" rows={cpdEligibleCompletions} emptyText="No CPD completions yet." renderMeta={(row) => `${String(row.cpdPoints ?? "-")} pts`} />
          <RecordList
            title="Assessment attempts"
            rows={assessmentAttempts}
            emptyText="No assessment attempts yet."
            renderMeta={(row) => `Score ${String(row.score ?? "PENDING")}`}
          />
        </div>
      </PageShell>
    </AppLayout>
  );
}
