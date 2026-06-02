"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoLearningRecord, useFundoProgress } from "@/hooks/queries/useFundoLms";

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
      <PageShell title="Learning record / transcript" subtitle="Native Fundo learner transcript including completions, assessments and certificates.">
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded border border-gray-200 bg-white p-3">
            <p className="text-xs text-gray-500">Completed courses</p>
            <p className="text-xl font-semibold text-gray-900">{completedEnrolments.length}</p>
          </div>
          <div className="rounded border border-gray-200 bg-white p-3">
            <p className="text-xs text-gray-500">Assessment attempts</p>
            <p className="text-xl font-semibold text-gray-900">{assessmentAttempts.length}</p>
          </div>
          <div className="rounded border border-gray-200 bg-white p-3">
            <p className="text-xs text-gray-500">Certificates</p>
            <p className="text-xl font-semibold text-gray-900">{certificates.length}</p>
          </div>
        </div>
        <div className="mt-4 rounded border border-gray-200 bg-white p-4">
          <p className="text-sm font-medium text-gray-900">Enrolments</p>
          <ul className="mt-2 space-y-1 text-sm">
            {enrolments.slice(0, 20).map((c) => (
              <li key={String(c.id ?? c.courseId)} className="flex justify-between gap-3">
                <span>{String(c.courseTitle ?? c.courseId ?? "-")}</span>
                <span className="text-xs text-gray-500">{String(c.status ?? "-")}</span>
              </li>
            ))}
            {enrolments.length === 0 ? <li className="text-gray-500">No enrolments yet.</li> : null}
          </ul>
        </div>
        <div className="mt-4 grid gap-4 lg:grid-cols-3">
          <div className="rounded border border-gray-200 bg-white p-4">
            <p className="text-sm font-medium text-gray-900">Progress rows</p>
            <p className="mt-1 text-xs text-gray-500">Transcript aggregate plus live progress endpoint.</p>
            <p className="mt-3 text-xl font-semibold text-gray-900">{progressRows.length || liveProgressRows.length}</p>
          </div>
          <div className="rounded border border-gray-200 bg-white p-4">
            <p className="text-sm font-medium text-gray-900">CPD eligible completions</p>
            <p className="mt-3 text-xl font-semibold text-gray-900">{cpdEligibleCompletions.length}</p>
          </div>
          <div className="rounded border border-gray-200 bg-white p-4">
            <p className="text-sm font-medium text-gray-900">Latest attempt</p>
            <p className="mt-3 text-sm text-gray-700">
              {assessmentAttempts[0] ? `Score ${String(assessmentAttempts[0].score ?? "PENDING")}` : "No attempts yet."}
            </p>
          </div>
        </div>
        <div className="mt-4 rounded border border-gray-200 bg-white p-4">
          <p className="text-sm font-medium text-gray-900">Certificates</p>
          <ul className="mt-2 space-y-1 text-sm">
            {certificates.slice(0, 20).map((cert) => (
              <li key={String(cert.id ?? cert.certificateNumber)} className="flex justify-between gap-3">
                <span>{String(cert.title ?? cert.certificateNumber ?? "-")}</span>
                <span className="text-xs text-gray-500">{String(cert.status ?? "-")}</span>
              </li>
            ))}
            {certificates.length === 0 ? <li className="text-gray-500">No certificates yet.</li> : null}
          </ul>
        </div>
      </PageShell>
    </AppLayout>
  );
}
