"use client";

import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoReportFiltered } from "@/hooks/queries/useFundoLms";

export default function CohortReportPage() {
  const [pathwayId, setPathwayId] = useState("");
  const [courseId, setCourseId] = useState("");
  const { data } = useFundoReportFiltered("cohort-completions", { pathwayId, courseId, limit: 100 });
  const items = (((data?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  return (
    <AppLayout>
      <PageShell title="Cohort completions" subtitle="Completion and certificate metrics by cohort/course.">
        <div className="mb-3 grid gap-2 rounded border border-border bg-card p-3 sm:grid-cols-2">
          <input value={pathwayId} onChange={(e) => setPathwayId(e.target.value)} placeholder="Filter pathway ID" className="rounded border border-border px-2 py-1 text-sm" />
          <input value={courseId} onChange={(e) => setCourseId(e.target.value)} placeholder="Filter course ID" className="rounded border border-border px-2 py-1 text-sm" />
        </div>
        <div className="overflow-auto rounded border border-border bg-card">
          <table className="min-w-full text-sm">
            <thead className="bg-background text-left text-xs uppercase text-muted-foreground">
              <tr>
                <th className="px-3 py-2">Cohort</th>
                <th className="px-3 py-2">Course</th>
                <th className="px-3 py-2">Completed</th>
                <th className="px-3 py-2">Completion %</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item, idx) => (
                <tr key={String(item.cohortId ?? idx)} className="border-t border-border">
                  <td className="px-3 py-2">{String(item.cohortCode ?? item.cohortId ?? "-")}</td>
                  <td className="px-3 py-2">{String(item.courseTitle ?? item.courseCode ?? "-")}</td>
                  <td className="px-3 py-2">{String(item.completedCount ?? item.completed ?? 0)}</td>
                  <td className="px-3 py-2">{String(item.completionRatePercent ?? 0)}</td>
                </tr>
              ))}
              {items.length === 0 ? (
                <tr>
                  <td className="px-3 py-6 text-center text-muted-foreground" colSpan={4}>No cohort records found for current filters.</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </PageShell>
    </AppLayout>
  );
}
