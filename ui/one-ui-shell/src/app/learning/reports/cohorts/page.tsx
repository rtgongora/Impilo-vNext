"use client";

import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoReportFiltered } from "@/hooks/queries/useFundoLms";

export default function CohortReportPage() {
  const [pathwayId, setPathwayId] = useState("");
  const [courseId, setCourseId] = useState("");
  const { data } = useFundoReportFiltered("cohort-completions", { pathwayId, courseId, limit: 100 });
  const payload = (data?.data ?? {}) as Record<string, unknown>;
  const totals = (payload.totals ?? {}) as Record<string, unknown>;
  const items = ((payload.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  return (
    <AppLayout>
      <PageShell title="Cohort completions" subtitle="Completion and certificate metrics by cohort/course.">
        <div className="mb-3 grid gap-2 rounded border border-gray-200 bg-white p-3 sm:grid-cols-2">
          <input value={pathwayId} onChange={(e) => setPathwayId(e.target.value)} placeholder="Filter pathway ID" className="rounded border border-gray-300 px-2 py-1 text-sm" />
          <input value={courseId} onChange={(e) => setCourseId(e.target.value)} placeholder="Filter course ID" className="rounded border border-gray-300 px-2 py-1 text-sm" />
        </div>
        <div className="mb-3 grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {[
            ["Courses", totals.courses],
            ["Enrolled", totals.enrolledCount],
            ["In progress", totals.inProgressCount],
            ["Completed", totals.completedCount],
            ["Cancelled", totals.cancelledCount],
            ["Certificates", totals.certificatesIssued],
          ].map(([label, value]) => (
            <div key={String(label)} className="rounded border border-gray-200 bg-white p-3">
              <p className="text-xs text-gray-500">{String(label)}</p>
              <p className="text-lg font-semibold text-gray-900">{String(value ?? 0)}</p>
            </div>
          ))}
        </div>
        <div className="overflow-auto rounded border border-gray-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
              <tr>
                <th className="px-3 py-2">Cohort</th>
                <th className="px-3 py-2">Course</th>
                <th className="px-3 py-2">Completed</th>
                <th className="px-3 py-2">Completion %</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item, idx) => (
                <tr key={String(item.cohortId ?? idx)} className="border-t border-gray-100">
                  <td className="px-3 py-2">{String(item.cohortCode ?? item.cohortId ?? "-")}</td>
                  <td className="px-3 py-2">{String(item.courseTitle ?? item.courseCode ?? "-")}</td>
                  <td className="px-3 py-2">{String(item.completedCount ?? item.completed ?? 0)}</td>
                  <td className="px-3 py-2">{String(item.completionRatePercent ?? 0)}</td>
                </tr>
              ))}
              {items.length === 0 ? (
                <tr>
                  <td className="px-3 py-6 text-center text-gray-500" colSpan={4}>No cohort records found for current filters.</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </PageShell>
    </AppLayout>
  );
}
