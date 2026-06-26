"use client";

import { useParams } from "next/navigation";
import { PageShell } from "@/components/PageShell";
import { useFundoSpaceCourses, useFundoSpaceSummary } from "@/hooks/queries/useFundoProviders";

type Course = { id: string; code?: string; title?: string; status?: string };

export default function SpaceDashboardPage() {
  const params = useParams();
  const spaceId = String(params?.spaceId ?? "");
  const { data: summaryData } = useFundoSpaceSummary(spaceId);
  const { data: courseData } = useFundoSpaceCourses(spaceId);

  const summary = (summaryData?.data as Record<string, unknown> | undefined) ?? {};
  const courses = (((courseData?.data as { items?: Course[] } | undefined)?.items) ?? []);

  return (
    <PageShell
      title={`Academy: ${summary.name ?? spaceId}`}
      subtitle={`${summary.spaceKind ?? "ACADEMY"} · ${summary.status ?? "—"} · ${summary.courseCount ?? 0} course(s)`}
    >
      <section className="rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Academy courses ({courses.length})</p>
        <p className="mt-1 text-xs text-muted-foreground">A space admin manages only this academy&apos;s content (Tshepo-gated; see space-admin policy spec).</p>
        <ul className="mt-2 space-y-1 text-sm" data-testid="space-course-list">
          {courses.map((c) => (
            <li key={c.id} className="rounded border border-border px-3 py-1.5">{c.code ? `${c.code} · ` : ""}{c.title ?? c.id} · {c.status ?? "—"}</li>
          ))}
          {courses.length === 0 && <li className="text-muted-foreground">No courses bound to this academy yet.</li>}
        </ul>
      </section>
    </PageShell>
  );
}
