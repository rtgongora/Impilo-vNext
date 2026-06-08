"use client";
/**
 * Provider council self-service — obligations, Fundo CPD candidates, renewal context.
 * Route: /registry/provider-council/self-service
 */

import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Brain, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { IntelligenceResultPanel } from "@/components/intelligence/IntelligenceResultPanel";
import { PageShell } from "@/components/PageShell";
import { useIntelligenceQuery } from "@/hooks/useIntelligence";
import { CouncilObligationPaymentPanel } from "@/components/registry/CouncilObligationPaymentPanel";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
import { useFundoCpdCandidates, useProviderCouncilObligations } from "@/hooks/queries/useProviderCouncil";
import { useSearchParams } from "next/navigation";

export default function ProviderCouncilSelfServicePage() {
  const searchParams = useSearchParams();
  const providerId = searchParams.get("providerId") ?? undefined;
  const { data: obligations, isLoading: loadOb } = useProviderCouncilObligations(providerId);
  const { data: fundo, isLoading: loadFd } = useFundoCpdCandidates(providerId);
  const { run: runIntel, loading: intelLoading } = useIntelligenceQuery();
  const [intelBrief, setIntelBrief] = useState<Record<string, unknown> | null>(null);

  return (
    <AppLayout>
      <PageShell
        title="Council self-service"
        subtitle="Track fees, CPD candidates from Impilo Fundo, and council-linked obligations"
      >
        <RegistryPlaneContextBar />
        <div className="mb-4">
          <Link
            href="/registry/providers"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to provider registry
          </Link>
        </div>

        <p className="text-sm text-gray-600 mb-4">
          Experience Doctrine: the person&apos;s <strong>Impilo / Health ID</strong> is the canonical identity; the
          numeric <code className="bg-gray-100 px-1 rounded">providerId</code> here is the registry technical key.
          Pass <code className="bg-gray-100 px-1 rounded">?providerId=&hellip;</code> to load obligations and Fundo
          candidates from Varapi via the BFF. Prefer Health-ID-first flows where the BFF exposes{" "}
          <code className="bg-gray-100 px-1 rounded">/by-health-id/...</code> for lookups.
        </p>

        {!providerId ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            Add <strong>providerId</strong> query parameter to view obligations and Fundo-linked CPD candidates.
          </div>
        ) : null}

        {providerId ? (
          <section className="mb-6 rounded-lg border border-violet-100 bg-violet-50/50 p-4">
            <div className="flex items-center gap-2 mb-2">
              <Brain className="h-5 w-5 text-violet-600" />
              <h2 className="text-sm font-semibold text-gray-900">Registry intelligence briefing</h2>
            </div>
            <p className="text-xs text-gray-600 mb-2">
              Varapi-backed provider summary plus data-quality hints (council alignment). Read-only — does not change
              registry records.
            </p>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                disabled={intelLoading}
                onClick={async () => {
                  const summary = await runIntel({
                    queryType: "SUMMARY_PROVIDER",
                    context: { subjectId: providerId, subjectType: "PROVIDER" },
                    input: {},
                  });
                  const dq = await runIntel({
                    queryType: "DATA_QUALITY_HINTS",
                    context: { subjectType: "PROVIDER", subjectId: providerId },
                    input: { text: "council obligations and CPD linkage" },
                  });
                  setIntelBrief({ summary, dataQuality: dq });
                }}
                className="rounded-lg bg-violet-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-700 disabled:opacity-50"
              >
                {intelLoading ? "Loading…" : "Run intelligence briefing"}
              </button>
              <Link href="/intelligence" className="text-xs text-violet-800 underline self-center">
                Intelligence hub
              </Link>
            </div>
            {intelBrief ? (
              <div className="mt-3 grid gap-3 md:grid-cols-2">
                {intelBrief.summary && typeof intelBrief.summary === "object" ? (
                  <div className="rounded border border-violet-100 bg-white p-2">
                    <p className="text-[11px] font-semibold text-violet-800 mb-1">Provider summary</p>
                    <IntelligenceResultPanel data={intelBrief.summary as Record<string, unknown>} />
                  </div>
                ) : null}
                {intelBrief.dataQuality && typeof intelBrief.dataQuality === "object" ? (
                  <div className="rounded border border-violet-100 bg-white p-2">
                    <p className="text-[11px] font-semibold text-violet-800 mb-1">Data quality</p>
                    <IntelligenceResultPanel data={intelBrief.dataQuality as Record<string, unknown>} />
                  </div>
                ) : null}
              </div>
            ) : null}
          </section>
        ) : null}

        <div className="grid gap-6 md:grid-cols-2">
          <section className="rounded-lg border border-gray-200 bg-white p-4">
            <h2 className="text-sm font-semibold text-gray-900 mb-2">Payment obligations</h2>
            <p className="mb-3 text-xs text-gray-600">
              Create a MusheX payment intent, sync settlement from MusheX, then complete council fee payment via the
              governed wallet rail.
            </p>
            <CouncilObligationPaymentPanel
              providerId={providerId}
              obligations={obligations ?? []}
              isLoading={loadOb}
            />
          </section>
          <section className="rounded-lg border border-gray-200 bg-white p-4">
            <h2 className="text-sm font-semibold text-gray-900 mb-2">Fundo CPD candidates</h2>
            {loadFd ? (
              <div className="flex items-center gap-2 text-gray-500 text-sm">
                <Loader2 className="w-4 h-4 animate-spin" /> Loading…
              </div>
            ) : (
              <QueryResultPanel title="Fundo CPD candidates" data={fundo ?? []} />
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
