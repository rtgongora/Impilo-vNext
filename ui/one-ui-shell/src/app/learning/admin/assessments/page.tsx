"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";

export default function AdminAssessmentsPage() {
  return (
      <PageShell title="Admin assessments" subtitle="Create/edit assessments and objective/manual questions.">
        <div className="flex gap-2">
          <Link href="/learning/admin/assessments/new" className="rounded border border-border px-3 py-1.5 text-sm text-foreground">
            New assessment
          </Link>
        </div>
      </PageShell>
  );
}
