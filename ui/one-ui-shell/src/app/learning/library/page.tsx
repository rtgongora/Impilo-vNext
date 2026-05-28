"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function LearningLibraryPage() {
  return (
    <AppLayout>
      <PageShell title="Fundo Library" subtitle="Search and explore learning resources linked to your courses, modules and assessments.">
        <div className="grid gap-3 md:grid-cols-2">
          <Link href="/learning/library/resources" className="rounded border border-gray-200 bg-white p-4 text-sm text-gray-800 hover:border-teal-300">
            Browse resources
          </Link>
          <Link href="/learning/library/uploads" className="rounded border border-gray-200 bg-white p-4 text-sm text-gray-800 hover:border-teal-300">
            Uploads and metadata
          </Link>
        </div>
      </PageShell>
    </AppLayout>
  );
}
