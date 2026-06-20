"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { useFundoLibraryResources } from "@/hooks/queries/useFundoStudio";

export default function LearningLibraryPage() {
  const { data, isLoading } = useFundoLibraryResources(20);
  const resources = (data?.data as { items?: Array<{ id?: string; title?: string }> } | undefined)?.items ?? [];

  return (
    <PageShell title="Fundo Library" subtitle="Learning resources from the Fundo library BFF surface.">
      <div className="grid gap-3 md:grid-cols-2">
        <Link href="/learning/library/resources" className="rounded border border-border bg-card p-4 text-sm text-foreground hover:border-teal-300">
          Browse resources
        </Link>
        <Link href="/learning/library/uploads" className="rounded border border-border bg-card p-4 text-sm text-foreground hover:border-teal-300">
          Uploads and metadata
        </Link>
      </div>
      {isLoading ? <p className="mt-4 text-sm text-muted-foreground">Loading library resources…</p> : null}
      {resources.length > 0 ? (
        <ul className="mt-4 space-y-2">
          {resources.slice(0, 8).map((resource) => (
            <li key={resource.id ?? resource.title}>
              <Link href={`/learning/library/${encodeURIComponent(resource.id ?? "")}`} className="text-sm text-teal-700 hover:underline">
                {resource.title ?? resource.id}
              </Link>
            </li>
          ))}
        </ul>
      ) : null}
    </PageShell>
  );
}
