"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function AdminAssessmentsPage() {
  return (
    <AppLayout>
      <PageShell title="Admin assessments" subtitle="Create/edit assessments and objective/manual questions.">
        <div className="flex gap-2">
          <Link href="/learning/admin/assessments/new" className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700">
            New assessment
          </Link>
        </div>
      </PageShell>
    </AppLayout>
  );
}
