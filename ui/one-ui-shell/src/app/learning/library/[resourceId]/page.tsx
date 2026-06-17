import { PageShell } from "@/components/PageShell";

export default function LearningLibraryResourceDetailPage({ params }: { params: { resourceId: string } }) {
  return (
      <PageShell title="Resource Detail" subtitle="Resource ownership, review status, versions and usage links.">
        <div className="rounded border border-border bg-card p-4 text-sm text-foreground">
          Resource reference: <span className="font-mono">{params.resourceId}</span>
        </div>
      </PageShell>
  );
}
