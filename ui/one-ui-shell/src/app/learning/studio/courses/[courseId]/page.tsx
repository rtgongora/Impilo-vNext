"use client";

import Link from "next/link";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useFundoCourseStructure } from "@/hooks/queries/useFundoCatalog";
import { useFundoStudioReadiness } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioCourseDetailPage({ params }: { params: { courseId: string } }) {
  const { courseId } = params;
  const { data } = useFundoCourseStructure(courseId);
  const { data: readiness } = useFundoStudioReadiness(courseId);
  const structure = data?.data?.structure;
  const checklist = (readiness?.data?.readinessChecklist as Array<{ item: string; ok: boolean }> | undefined) ?? [];

  return (
    <FundoStudioWorkspace title="Studio Course Detail" subtitle="Review course structure, readiness, and open builder/preview flows.">
      <div className="rounded-lg border border-border bg-card p-4">
        <p className="text-lg font-semibold text-foreground">{structure?.title ?? "Course"}</p>
        <p className="text-sm text-muted-foreground">{structure?.description ?? "No description yet."}</p>
        <div className="mt-2 flex gap-2 text-xs">
          <Link href={`/learning/studio/courses/${courseId}/builder`} className="rounded border border-border px-2 py-1 text-foreground">Open builder</Link>
          <Link href={`/learning/courses/${courseId}`} className="rounded border border-border px-2 py-1 text-foreground">Learner preview</Link>
        </div>
      </div>
      <div className="mt-4 rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Readiness checklist</p>
        <ul className="mt-2 space-y-1 text-sm text-foreground">
          {checklist.map((c) => (
            <li key={c.item}>
              {c.ok ? "✓" : "•"} {c.item}
            </li>
          ))}
        </ul>
      </div>
    </FundoStudioWorkspace>
  );
}
