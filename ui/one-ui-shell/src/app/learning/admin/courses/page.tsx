"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { useFundoCatalog } from "@/hooks/queries/useFundoCatalog";

export default function AdminCoursesPage() {
  const { data } = useFundoCatalog({ limit: 100 });
  const items = data?.data?.items ?? [];
  return (
      <PageShell title="Admin courses" subtitle="Create/edit/publish native Fundo courses.">
        <Link href="/learning/admin/courses/new" className="text-sm text-teal-700 hover:underline">
          New course
        </Link>
        <ul className="mt-3 space-y-2">
          {items.map((c) => (
            <li key={c.id} className="rounded border border-border bg-card p-3 text-sm">
              <p className="font-medium text-foreground">{c.title}</p>
              <Link href={`/learning/admin/courses/${c.id}/edit`} className="text-teal-700 hover:underline">
                Edit
              </Link>
            </li>
          ))}
        </ul>
      </PageShell>
  );
}
