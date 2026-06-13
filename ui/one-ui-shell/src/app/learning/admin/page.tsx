"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function LearningAdminHomePage() {
  const links = [
    ["/learning/admin/courses", "Courses"],
    ["/learning/admin/pathways", "Pathways"],
    ["/learning/admin/assessments", "Assessments"],
    ["/learning/admin/moderation", "Moderation queue"],
    ["/learning/library/uploads", "Content uploads"],
  ];
  return (
    <AppLayout>
      <PageShell title="Fundo authoring admin" subtitle="Native authoring shell for courses, pathways and assessments.">
        <div className="flex flex-wrap gap-2">
          {links.map(([href, label]) => (
            <Link key={String(href)} href={String(href)} className="rounded border border-border px-3 py-1.5 text-sm text-foreground">
              {String(label)}
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
