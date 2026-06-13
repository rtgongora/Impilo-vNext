"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoReportsOverview } from "@/hooks/queries/useFundoLms";

export default function LearningReportsHomePage() {
  const { data } = useFundoReportsOverview();
  const overview = (data?.data ?? {}) as Record<string, unknown>;

  return (
    <AppLayout>
      <PageShell title="Learning reports" subtitle="Trainer and supervisor reporting workspace.">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {[
            ["Published courses", overview.publishedCourses],
            ["Total enrolments", overview.totalEnrolments],
            ["In progress", overview.inProgress],
            ["Completed", overview.completed],
          ].map(([label, value]) => (
            <div key={String(label)} className="rounded border border-border bg-card p-3">
              <p className="text-xs text-muted-foreground">{String(label)}</p>
              <p className="text-xl font-semibold text-foreground">{String(value ?? 0)}</p>
            </div>
          ))}
        </div>
        <div className="mt-4 flex flex-wrap gap-2 text-sm">
          {[
            ["/learning/reports/cohorts", "Cohort completions"],
            ["/learning/reports/courses", "Course completions"],
            ["/learning/reports/overdue", "Overdue learning"],
            ["/learning/reports/assessments", "Assessment performance"],
          ].map(([href, label]) => (
            <Link key={String(href)} href={String(href)} className="rounded border border-border px-3 py-1.5 text-foreground">
              {String(label)}
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
