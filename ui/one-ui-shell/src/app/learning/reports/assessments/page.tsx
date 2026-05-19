"use client";

import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoPendingReviews, useFundoReportFiltered } from "@/hooks/queries/useFundoLms";

export default function AssessmentPerformanceReportPage() {
  const [courseId, setCourseId] = useState("");
  const [subjectType, setSubjectType] = useState("");
  const [assessmentId, setAssessmentId] = useState("");
  const { data } = useFundoReportFiltered("assessment-performance", { courseId, subjectType, limit: 100 });
  const { data: pendingData } = useFundoPendingReviews(assessmentId || undefined, 100);
  const items = (((data?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  const pendingItems = ((((pendingData?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean));
  return (
    <AppLayout>
      <PageShell title="Assessment performance" subtitle="Attempt volumes, pass rate and scoring indicators by assessment.">
        <div className="mb-3 grid gap-2 rounded border border-gray-200 bg-white p-3 sm:grid-cols-3">
          <input value={courseId} onChange={(e) => setCourseId(e.target.value)} placeholder="Course ID" className="rounded border border-gray-300 px-2 py-1 text-sm" />
          <input value={subjectType} onChange={(e) => setSubjectType(e.target.value)} placeholder="Subject type" className="rounded border border-gray-300 px-2 py-1 text-sm" />
          <input value={assessmentId} onChange={(e) => setAssessmentId(e.target.value)} placeholder="Assessment ID (manual review queue)" className="rounded border border-gray-300 px-2 py-1 text-sm" />
        </div>
        <div className="overflow-auto rounded border border-gray-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
              <tr>
                <th className="px-3 py-2">Assessment</th>
                <th className="px-3 py-2">Attempts</th>
                <th className="px-3 py-2">Average score</th>
                <th className="px-3 py-2">Pass rate %</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={String(item.assessmentId)} className="border-t border-gray-100">
                  <td className="px-3 py-2">{String(item.assessmentTitle ?? item.assessmentId)}</td>
                  <td className="px-3 py-2">{String(item.attempts ?? 0)}</td>
                  <td className="px-3 py-2">{String(item.averageScore ?? 0)}</td>
                  <td className="px-3 py-2">{String(item.passRatePercent ?? 0)}</td>
                </tr>
              ))}
              {items.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-3 py-6 text-center text-gray-500">No assessment analytics for current filters.</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
        {assessmentId ? (
          <div className="mt-4 rounded border border-gray-200 bg-white p-4">
            <p className="text-sm font-medium text-gray-900">Manual review queue</p>
            <p className="mt-1 text-xs text-gray-500">Pending non-objective attempts requiring rubric-based review.</p>
            <ul className="mt-2 space-y-1 text-sm">
              {pendingItems.map((item) => (
                <li key={String(item.id)} className="flex items-center justify-between border-b border-gray-100 py-1">
                  <span>Attempt {String(item.attemptNo ?? "-")} • {String(item.subjectType ?? "-")}/{String(item.subjectId ?? "-")}</span>
                  <span className="text-xs text-amber-700">{String(item.manualReviewStatus ?? "PENDING")}</span>
                </li>
              ))}
              {pendingItems.length === 0 ? <li className="text-gray-500">No pending manual reviews.</li> : null}
            </ul>
          </div>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
