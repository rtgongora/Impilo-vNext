"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoPathways } from "@/hooks/queries/useFundoLms";

export default function AdminPathwaysPage() {
  const { data } = useFundoPathways();
  const items = (((data?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  return (
    <AppLayout>
      <PageShell title="Admin pathways" subtitle="Create and maintain ordered pathway structures.">
        <Link href="/learning/admin/pathways/new" className="text-sm text-teal-700 hover:underline">New pathway</Link>
        <ul className="mt-3 space-y-2">
          {items.map((p) => (
            <li key={String(p.id)} className="rounded border border-gray-200 bg-white p-3 text-sm">
              <p className="font-medium text-gray-900">{String(p.title ?? p.code)}</p>
              <Link href={`/learning/admin/pathways/${String(p.id)}/edit`} className="text-teal-700 hover:underline">Edit</Link>
            </li>
          ))}
        </ul>
      </PageShell>
    </AppLayout>
  );
}
