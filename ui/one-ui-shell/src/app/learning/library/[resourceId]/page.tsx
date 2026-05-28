import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function LearningLibraryResourceDetailPage({ params }: { params: { resourceId: string } }) {
  return (
    <AppLayout>
      <PageShell title="Resource Detail" subtitle="Resource ownership, review status, versions and usage links.">
        <div className="rounded border border-gray-200 bg-white p-4 text-sm text-gray-700">
          Resource reference: <span className="font-mono">{params.resourceId}</span>
        </div>
      </PageShell>
    </AppLayout>
  );
}
