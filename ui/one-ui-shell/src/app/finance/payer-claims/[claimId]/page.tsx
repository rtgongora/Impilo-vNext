"use client";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { ArrowLeft, FileText, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FinancePayerOpsReconciliationNotice } from "@/components/FinanceAccessNotice";
import { useDisputePayerClaim, usePayerClaim, useSubmitPayerClaim } from "@/hooks/queries/usePayerClaims";

const DEFAULT_DISPUTE_JSON = "{\n  \"reason\": \"\",\n  \"notes\": \"\"\n}\n";

export default function FinancePayerClaimDetailPage() {
  const params = useParams<{ claimId: string }>();
  const claimId = params.claimId;
  const claimQ = usePayerClaim(claimId);
  const submitM = useSubmitPayerClaim();
  const disputeM = useDisputePayerClaim();
  const [disputeJson, setDisputeJson] = useState(DEFAULT_DISPUTE_JSON);
  const [disputeError, setDisputeError] = useState<string | null>(null);
  const [submitConfirmed, setSubmitConfirmed] = useState(false);
  const [disputeConfirmed, setDisputeConfirmed] = useState(false);

  return (
    <AppLayout>
      <PageShell
        title="Payer claim"
        subtitle="MusheX claim detail, submit, and dispute workflow through Experience."
        icon={<FileText className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/finance/payer-claims" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Back to payer claims
          </Link>
        </div>

        <div className="max-w-4xl space-y-6">
          <FinancePayerOpsReconciliationNotice />

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Claim detail</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET <code className="text-[11px]">/internal/v1/finance/payer-claims/{claimId}</code>
            </p>
            {claimQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading claim...
              </p>
            ) : claimQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Could not load payer claim.</p>
            ) : (
              <QueryResultPanel title="Claim Q" isPending={claimQ.isPending} isLoading={claimQ.isPending} isError={claimQ.isError} error={claimQ.error} data={claimQ.data} />
            )}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Claim actions</h2>
            <p className="mt-1 text-xs text-slate-500">
              Submit/dispute writes are intentional operations. Confirm each action before sending it to the payer-ops API.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <label className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-700">
                <input
                  type="checkbox"
                  checked={submitConfirmed}
                  onChange={(event) => setSubmitConfirmed(event.target.checked)}
                />
                I confirm this submit action is intentional
              </label>
              <button
                type="button"
                disabled={submitM.isPending || !submitConfirmed}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
                onClick={() => submitM.mutate(claimId)}
              >
                Submit claim
              </button>
            </div>

            <label className="mt-4 block text-xs text-slate-600">
              Dispute JSON
              <textarea
                value={disputeJson}
                onChange={(event) => setDisputeJson(event.target.value)}
                rows={6}
                className="mt-1 block w-full rounded-lg border border-slate-200 p-3 font-mono text-xs"
                aria-label="Payer claim dispute JSON"
              />
            </label>
            <label className="mt-3 inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-700">
              <input
                type="checkbox"
                checked={disputeConfirmed}
                onChange={(event) => setDisputeConfirmed(event.target.checked)}
              />
              I confirm this dispute action is intentional
            </label>
            {disputeError ? <p className="mt-2 text-xs text-red-700">{disputeError}</p> : null}
            <button
              type="button"
              disabled={disputeM.isPending || !disputeConfirmed}
              className="mt-3 rounded-lg bg-red-700 px-4 py-2 text-sm font-medium text-white hover:bg-red-800 disabled:opacity-50"
              onClick={() => {
                try {
                  const body = JSON.parse(disputeJson) as unknown;
                  setDisputeError(null);
                  disputeM.mutate({ claimId, body });
                } catch {
                  setDisputeError("Invalid JSON.");
                }
              }}
            >
              Dispute claim
            </button>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
