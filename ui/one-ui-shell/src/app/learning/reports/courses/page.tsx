"use client";

import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoReportFiltered } from "@/hooks/queries/useFundoLms";

export default function CourseCompletionReportPage() {
  const [courseId, setCourseId] = useState("");
  const [subjectType, setSubjectType] = useState("");
  const [status, setStatus] = useState("");
  const { data } = useFundoReportFiltered("course-completions", { courseId, subjectType, status, limit: 100 });
  const items = (((data?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  return (
    <AppLayout>
      <PageShell title="Course completions" subtitle="Completion rates and active/completed enrolments by course.">
        <div className="mb-3 grid gap-2 rounded border border-gray-200 bg-white p-3 sm:grid-cols-3">
          <input value={courseId} onChange={(e) => setCourseId(e.target.value)} placeholder="Course ID" className="rounded border border-gray-300 px-2 py-1 text-sm" />
          <input value={subjectType} onChange={(e) => setSubjectType(e.target.value)} placeholder="Subject type (e.g. PROVIDER)" className="rounded border border-gray-300 px-2 py-1 text-sm" />
          <input value={status} onChange={(e) => setStatus(e.target.value)} placeholder="Status filter (ENROLLED/IN_PROGRESS/COMPLETED)" className="rounded border border-gray-300 px-2 py-1 text-sm" />
        </div>
        <div className="overflow-auto rounded border border-gray-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
              <tr>
                <th className="px-3 py-2">Course</th>
                <th className="px-3 py-2">Total enrolments</th>
                <th className="px-3 py-2">Active</th>
                <th className="px-3 py-2">Completed</th>
                <th className="px-3 py-2">Completion %</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={String(item.courseId)} className="border-t border-gray-100">
                  <td className="px-3 py-2">{String(item.courseTitle ?? item.courseCode ?? item.courseId)}</td>
                  <td className="px-3 py-2">{String(item.totalEnrolments ?? 0)}</td>
                  <td className="px-3 py-2">{String(item.activeEnrolments ?? 0)}</td>
                  <td className="px-3 py-2">{String(item.completed ?? 0)}</td>
                  <td className="px-3 py-2">{String(item.completionRatePercent ?? 0)}</td>
                </tr>
              ))}
              {items.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-3 py-6 text-center text-gray-500">No course completion data found.</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </PageShell>
    </AppLayout>
  );
}
