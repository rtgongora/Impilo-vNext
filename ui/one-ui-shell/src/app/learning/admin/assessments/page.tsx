"use client";

import { useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoCourseAssessments } from "@/hooks/queries/useFundoLms";

export default function AdminAssessmentsPage() {
  const [courseId, setCourseId] = useState("");
  const { data, isLoading } = useFundoCourseAssessments(courseId || undefined);
  const items = (((data?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);

  return (
    <AppLayout>
      <PageShell title="Admin assessments" subtitle="Create/edit assessments and objective/manual questions.">
        <div className="flex flex-wrap gap-2">
          <Link href="/learning/admin/assessments/new" className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700">
            New assessment
          </Link>
          <input
            value={courseId}
            onChange={(e) => setCourseId(e.target.value)}
            placeholder="Course ID to list assessments"
            className="min-w-[18rem] rounded border border-gray-300 px-2 py-1.5 text-sm"
          />
        </div>
        {isLoading ? <p className="mt-3 text-sm text-gray-500">Loading assessments...</p> : null}
        <ul className="mt-3 space-y-2">
          {items.map((assessment) => (
            <li key={String(assessment.id)} className="rounded border border-gray-200 bg-white p-3 text-sm">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="font-medium text-gray-900">{String(assessment.title ?? "Assessment")}</p>
                  <p className="text-xs text-gray-500">{String(assessment.assessmentType ?? "QUIZ")} • {String(assessment.status ?? "-")}</p>
                </div>
                <Link href={`/learning/admin/assessments/${String(assessment.id)}/edit`} className="text-impilo-700 hover:underline">
                  Edit
                </Link>
              </div>
            </li>
          ))}
          {courseId && !isLoading && items.length === 0 ? (
            <li className="rounded border border-dashed border-gray-200 p-3 text-sm text-gray-500">
              No assessments found for this course.
            </li>
          ) : null}
        </ul>
      </PageShell>
    </AppLayout>
  );
}
