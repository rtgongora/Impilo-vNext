"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { useFundoCourseAssessments } from "@/hooks/queries/useFundoLms";

export default function AdminAssessmentsPage() {
  const { data, isLoading } = useFundoCourseAssessments();
  const assessments = (data?.data as unknown[] | undefined) ?? [];

  return (
    <PageShell title="Admin assessments" subtitle="Create/edit assessments and objective/manual questions.">
      {isLoading ? <p className="text-sm text-muted-foreground">Loading assessments…</p> : null}
      <div className="flex gap-2">
        <Link href="/learning/admin/assessments/new" className="rounded border border-border px-3 py-1.5 text-sm text-foreground">
          New assessment
        </Link>
      </div>
      {assessments.length > 0 ? (
        <ul className="mt-4 space-y-2 text-sm">
          {assessments.slice(0, 10).map((item, index) => (
            <li key={index} className="rounded border border-border bg-card p-3">
              {typeof item === 'object' && item && 'title' in item ? String((item as { title?: string }).title) : `Assessment ${index + 1}`}
            </li>
          ))}
        </ul>
      ) : !isLoading ? (
        <p className="mt-4 text-sm text-muted-foreground">No assessments returned yet.</p>
      ) : null}
    </PageShell>
  );
}
