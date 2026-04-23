"use client";

/**
 * Provider council self-service — obligations, Fundo CPD candidates, renewal context.
 * Route: /registry/provider-council/self-service
 */

import Link from "next/link";
import { ArrowLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useFundoCpdCandidates, useProviderCouncilObligations } from "@/hooks/queries/useProviderCouncil";
import { useSearchParams } from "next/navigation";

export default function ProviderCouncilSelfServicePage() {
  const searchParams = useSearchParams();
  const providerId = searchParams.get("providerId") ?? undefined;
  const { data: obligations, isLoading: loadOb } = useProviderCouncilObligations(providerId);
  const { data: fundo, isLoading: loadFd } = useFundoCpdCandidates(providerId);

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
          Pass <code className="bg-gray-100 px-1 rounded">?providerId=&lt;numeric internal id&gt;</code> to load data
          from Varapi via the Experience BFF.
        </p>

        {!providerId ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            Add <strong>providerId</strong> query parameter to view obligations and Fundo-linked CPD candidates.
          </div>
        ) : null}

        <div className="grid gap-6 md:grid-cols-2">
          <section className="rounded-lg border border-gray-200 bg-white p-4">
            <h2 className="text-sm font-semibold text-gray-900 mb-2">Payment obligations</h2>
            {loadOb ? (
              <div className="flex items-center gap-2 text-gray-500 text-sm">
                <Loader2 className="w-4 h-4 animate-spin" /> Loading…
              </div>
            ) : (
              <pre className="text-xs bg-gray-50 p-3 rounded overflow-auto max-h-80">
                {JSON.stringify(obligations ?? [], null, 2)}
              </pre>
            )}
          </section>
          <section className="rounded-lg border border-gray-200 bg-white p-4">
            <h2 className="text-sm font-semibold text-gray-900 mb-2">Fundo CPD candidates</h2>
            {loadFd ? (
              <div className="flex items-center gap-2 text-gray-500 text-sm">
                <Loader2 className="w-4 h-4 animate-spin" /> Loading…
              </div>
            ) : (
              <pre className="text-xs bg-gray-50 p-3 rounded overflow-auto max-h-80">
                {JSON.stringify(fundo ?? [], null, 2)}
              </pre>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
