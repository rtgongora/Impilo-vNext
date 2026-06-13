"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

type Props = {
  title: string;
  subtitle: string;
  children?: ReactNode;
};

const STUDIO_LINKS = [
  ["/learning/studio", "Studio home"],
  ["/learning/studio/courses", "Courses"],
  ["/learning/studio/library", "Library"],
  ["/learning/studio/media", "Media"],
  ["/learning/studio/assessments", "Assessments"],
  ["/learning/studio/surveys", "Surveys"],
  ["/learning/studio/ai", "AI"],
  ["/learning/studio/publish", "Publish"],
  ["/learning/studio/analytics", "Analytics"],
];

const LEARNER_LINKS = [
  ["/learning/library", "Learning Library"],
  ["/learning/library/resources", "Library resources"],
  ["/learning/library/uploads", "Library uploads"],
  ["/learning/notifications", "Learning notifications"],
];

export function FundoStudioWorkspace({ title, subtitle, children }: Props) {
  return (
    <AppLayout>
      <PageShell title={title} subtitle={subtitle} serviceSlug="fundo">
        <div className="mb-4 rounded-lg border border-teal-200 bg-teal-50 px-4 py-3 text-sm text-teal-900">
          <p className="font-semibold">Role-aware Fundo experience</p>
          <p className="mt-1 text-teal-800">
            Learner, Trainer, Author, Admin and Supervisor surfaces are all native Fundo routes. AI/Nompilo outputs remain draft until reviewed.
          </p>
        </div>
        <div className="mb-4 grid gap-2 md:grid-cols-3">
          {STUDIO_LINKS.map(([href, label]) => (
            <Link key={href} href={href} className="rounded border border-border bg-card px-3 py-2 text-sm text-foreground hover:border-teal-300">
              {label}
            </Link>
          ))}
        </div>
        <div className="mb-6 flex flex-wrap gap-2">
          {LEARNER_LINKS.map(([href, label]) => (
            <Link key={href} href={href} className="rounded border border-border px-2.5 py-1 text-xs text-foreground">
              {label}
            </Link>
          ))}
        </div>
        {children}
      </PageShell>
    </AppLayout>
  );
}
