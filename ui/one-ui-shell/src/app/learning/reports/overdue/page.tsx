"use client";

import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoReportFiltered } from "@/hooks/queries/useFundoLms";

export default function OverdueLearningReportPage() {
  const [courseId, setCourseId] = useState("");
  const [subjectType, setSubjectType] = useState("");
  const [status, setStatus] = useState("");
  const { data } = useFundoReportFiltered("overdue-learning", { courseId, subjectType, status, limit: 100 });
  const items = (((data?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  return (
    <AppLayout>
      <PageShell title="Overdue learning" subtitle="Overdue enrolment list for trainer/supervisor follow-up.">
        <div className="mb-3 grid gap-2 rounded border border-gray-200 bg-white p-3 sm:grid-cols-3">
          <input value={courseId} onChange={(e) => setCourseId(e.target.value)} placeholder="Course ID" className="rounded border border-gray-300 px-2 py-1 text-sm" />
          <input value={subjectType} onChange={(e) => setSubjectType(e.target.value)} placeholder="Subject type" className="rounded border border-gray-300 px-2 py-1 text-sm" />
          <input value={status} onChange={(e) => setStatus(e.target.value)} placeholder="Status filter" className="rounded border border-gray-300 px-2 py-1 text-sm" />
        </div>
        <div className="overflow-auto rounded border border-gray-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
              <tr>
                <th className="px-3 py-2">Course</th>
                <th className="px-3 py-2">Subject</th>
                <th className="px-3 py-2">Due date</th>
                <th className="px-3 py-2">Status</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={String(item.id)} className="border-t border-gray-100">
                  <td className="px-3 py-2">{String(item.courseTitle ?? item.courseCode ?? "Course")}</td>
                  <td className="px-3 py-2">{String(item.subjectType ?? "-")} / {String(item.subjectId ?? "-")}</td>
                  <td className="px-3 py-2">{String(item.dueAt ?? "-")}</td>
                  <td className="px-3 py-2">{String(item.status ?? "-")}</td>
                </tr>
              ))}
              {items.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-3 py-6 text-center text-gray-500">No overdue enrolments found.</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </PageShell>
    </AppLayout>
  );
}
