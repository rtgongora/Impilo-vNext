"use client";

/**
 * Council staff workspace — application queues by council and workflow state.
 * Route: /registry/provider-council/council-workspace
 */

import Link from "next/link";
import { ArrowLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useProviderCouncilQueue } from "@/hooks/queries/useProviderCouncil";
import { useSearchParams } from "next/navigation";
import { CouncilLearningEvidencePanel } from "@/components/learning/CouncilLearningEvidencePanel";

export default function CouncilWorkspacePage() {
  const searchParams = useSearchParams();
  const councilId = searchParams.get("councilId") ?? undefined;
  const providerPublicId = searchParams.get("providerPublicId") ?? undefined;
  const workflowStates = searchParams.get("workflowStates") ?? "SUBMITTED,UNDER_ADMIN_REVIEW,AWAITING_PAYMENT,READY_FOR_REVIEW";
  const { data, isLoading } = useProviderCouncilQueue(councilId, workflowStates);

  return (
    <AppLayout>
      <PageShell
        title="Council operations workspace"
        subtitle="Intake and workflow queues scoped to a professional council"
      >
        <RegistryPlaneContextBar />
        <div className="mb-4">
          <Link
            href="/registry-admin"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to registry administration
          </Link>
        </div>

        <p className="text-sm text-gray-600 mb-4">
          Use <code className="bg-gray-100 px-1 rounded">?councilId=&lt;id&gt;</code> and optional{" "}
          <code className="bg-gray-100 px-1 rounded">workflowStates=</code> (comma-separated). Add{" "}
          <code className="bg-gray-100 px-1 rounded">providerPublicId=</code> to surface learning-service completion
          evidence for a provider.
        </p>

        <div className="mb-6">
          <CouncilLearningEvidencePanel providerPublicId={providerPublicId} />
        </div>

        {!councilId ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            Add <strong>councilId</strong> to load the council queue from Varapi.
          </div>
        ) : null}

        <section className="rounded-lg border border-gray-200 bg-white p-4">
          <h2 className="text-sm font-semibold text-gray-900 mb-2">Open applications</h2>
          {isLoading ? (
            <div className="flex items-center gap-2 text-gray-500 text-sm">
              <Loader2 className="w-4 h-4 animate-spin" /> Loading…
            </div>
          ) : (
            <pre className="text-xs bg-gray-50 p-3 rounded overflow-auto max-h-[28rem]">
              {JSON.stringify(data ?? [], null, 2)}
            </pre>
          )}
        </section>
      </PageShell>
    </AppLayout>
  );
}
