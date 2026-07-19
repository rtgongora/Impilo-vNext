"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { useFundoLibraryResources } from "@/hooks/queries/useFundoStudio";

export default function LearningLibraryResourceDetailPage({ params }: { params: { resourceId: string } }) {
  const { data, isLoading, isError } = useFundoLibraryResources(100);
  const resources = (data?.data as { items?: Array<{ id?: string; title?: string; reviewStatus?: string; resourceType?: string }> } | undefined)?.items ?? [];
  const resource = resources.find((item) => item.id === params.resourceId);

  return (
    <PageShell title="Resource Detail" subtitle="Resource ownership, review status, versions and usage links.">
      <Link href="/learning/library" className="text-sm text-muted-foreground hover:text-foreground">
        ← Library
      </Link>
      {isLoading ? <p className="mt-4 text-sm text-muted-foreground">Loading resource…</p> : null}
      {isError ? <p className="mt-4 text-sm text-destructive">Library resource unavailable.</p> : null}
      <div className="mt-4 rounded border border-border bg-card p-4 text-sm text-foreground">
        <div>
          Resource reference: <span className="font-mono">{params.resourceId}</span>
        </div>
        {resource ? (
          <div className="mt-2 space-y-1">
            <div className="font-medium">{resource.title ?? 'Untitled resource'}</div>
            <div className="text-muted-foreground">Type: {resource.resourceType ?? 'unknown'}</div>
            <div className="text-muted-foreground">Review status: {resource.reviewStatus ?? 'unknown'}</div>
          </div>
        ) : !isLoading && !isError ? (
          <p className="mt-2 text-muted-foreground">Resource not found in the current library page.</p>
        ) : null}
      </div>
    </PageShell>
  );
}
