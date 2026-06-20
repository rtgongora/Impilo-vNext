"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { useFundoStudioDashboard } from "@/hooks/queries/useFundoStudio";

export default function LearningAdminHomePage() {
  const { data, isLoading } = useFundoStudioDashboard();
  const links = [
    ["/learning/admin/courses", "Courses"],
    ["/learning/admin/pathways", "Pathways"],
    ["/learning/admin/assessments", "Assessments"],
    ["/learning/admin/moderation", "Moderation queue"],
    ["/learning/library/uploads", "Content uploads"],
  ] as const;

  return (
    <PageShell title="Fundo authoring admin" subtitle="Native authoring shell for courses, pathways and assessments.">
      {isLoading ? <p className="text-sm text-muted-foreground">Loading studio dashboard…</p> : null}
      {data?.data ? (
        <p className="mb-3 text-xs text-muted-foreground">Studio dashboard loaded from BFF.</p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        {links.map(([href, label]) => (
          <Link key={href} href={href} className="rounded border border-border px-3 py-1.5 text-sm text-foreground">
            {label}
          </Link>
        ))}
      </div>
    </PageShell>
  );
}
