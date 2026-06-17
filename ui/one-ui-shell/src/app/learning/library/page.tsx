"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";

export default function LearningLibraryPage() {
  return (
      <PageShell title="Fundo Library" subtitle="Search and explore learning resources linked to your courses, modules and assessments.">
        <div className="grid gap-3 md:grid-cols-2">
          <Link href="/learning/library/resources" className="rounded border border-border bg-card p-4 text-sm text-foreground hover:border-teal-300">
            Browse resources
          </Link>
          <Link href="/learning/library/uploads" className="rounded border border-border bg-card p-4 text-sm text-foreground hover:border-teal-300">
            Uploads and metadata
          </Link>
        </div>
      </PageShell>
  );
}
