"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoPathways } from "@/hooks/queries/useFundoLms";

export default function PathwaysPage() {
  const { data, isLoading } = useFundoPathways();
  const items = (((data?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);

  return (
    <AppLayout>
      <PageShell title="Pathways" subtitle="Available and assigned pathways with ordered learning sequences.">
        {isLoading ? <p className="text-sm text-gray-600">Loading…</p> : null}
        <ul className="space-y-2">
          {items.map((p) => (
            <li key={String(p.id)} className="rounded border border-gray-200 bg-white p-3">
              <p className="font-medium text-gray-900">{String(p.title ?? p.code ?? "Pathway")}</p>
              <Link href={`/learning/pathways/${String(p.id)}`} className="text-sm text-teal-700 hover:underline">
                Open pathway
              </Link>
            </li>
          ))}
        </ul>
      </PageShell>
    </AppLayout>
  );
}
