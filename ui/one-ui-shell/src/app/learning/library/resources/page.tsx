"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { useFundoLibraryResources } from "@/hooks/queries/useFundoStudio";

export default function LearningLibraryResourcesPage() {
  const { data } = useFundoLibraryResources(100);
  const items = ((data?.data as { items?: Array<{ id: string; title: string; resourceType?: string; reviewStatus?: string }> } | undefined)?.items ?? []);
  return (
      <PageShell title="Library Resources" subtitle="Curated Fundo resources with review status and source attribution.">
        <ul className="space-y-2">
          {items.map((item) => (
            <li key={item.id} className="flex items-center justify-between rounded border border-border bg-card px-3 py-2 text-sm">
              <span>{item.title} · {item.resourceType ?? "DOCUMENT"} · {item.reviewStatus ?? "DRAFT"}</span>
              <Link href={`/learning/library/${item.id}`} className="text-teal-700 hover:underline">Open</Link>
            </li>
          ))}
        </ul>
      </PageShell>
  );
}
