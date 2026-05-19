"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { AlertCircle, ArrowLeft, Loader2, RotateCcw, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  extractRecord,
  useMushexPlatformReversalById,
} from "@/hooks/queries/useMushexPlatformAdmin";

function asString(value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return "—";
  }
}

export default function ReversalDetailPage() {
  const params = useParams();
  const reversalId = typeof params.reversalId === "string" ? params.reversalId : "";
  const reversalQ = useMushexPlatformReversalById(reversalId);
  const reversal = extractRecord(reversalQ.data);

  return (
    <AppLayout>
      <PageShell
        title="Reversal record"
        subtitle={
          reversalId
            ? `Read-only detail for reversal ${reversalId}`
            : "Read-only detail for a reversal record"
        }
      >
        <div className="mb-4">
          <Link
            href="/finance/mushex-platform"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to MusheX platform
          </Link>
        </div>

        <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-purple-100 text-purple-700">
              <RotateCcw className="h-5 w-5" />
            </div>
            <div className="flex-1">
              <h2 className="text-sm font-semibold text-slate-900">Reversal detail</h2>
              <p className="mt-1 text-xs text-slate-500">
                Read-only reversal detail from MusheX platform admin.
              </p>

              {reversalQ.isLoading && (
                <p className="mt-3 flex items-center gap-2 text-xs text-slate-500">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading reversal…
                </p>
              )}
              {reversalQ.isError && (
                <p className="mt-3 flex items-center gap-2 text-xs text-red-700">
                  <AlertCircle className="h-3.5 w-3.5" /> Could not load reversal record from MusheX.
                </p>
              )}
              {!reversalQ.isLoading && !reversalQ.isError && !reversal && (
                <p className="mt-3 text-xs text-slate-500">
                  No reversal record returned for this ID.
                </p>
              )}
              {!reversalQ.isLoading && !reversalQ.isError && reversal && (
                <dl className="mt-3 grid gap-2 text-xs sm:grid-cols-2">
                  <div>
                    <dt className="text-slate-500">Reversal ID</dt>
                    <dd className="font-mono text-[11px] text-slate-900">
                      {asString(reversal.reversalId ?? reversal.id)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Reversal code</dt>
                    <dd className="text-slate-700">{asString(reversal.reversalCode)}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Original transaction ref</dt>
                    <dd className="text-slate-700">{asString(reversal.originalTxnRef)}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Status</dt>
                    <dd className="text-slate-700">
                      {asString(reversal.reversalStatus ?? reversal.status)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Amount</dt>
                    <dd className="text-slate-700">
                      {asString(reversal.amount)} {asString(reversal.currency)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Created</dt>
                    <dd className="text-slate-700">{asString(reversal.createdAt)}</dd>
                  </div>
                </dl>
              )}

              <p className="mt-3 text-[11px] text-slate-400">
                BFF route:{" "}
                <code className="text-[10px]">
                  /internal/v1/finance/mushex-platform/reversals/{reversalId || "{reversalId}"}
                </code>
              </p>
              <p className="mt-2 inline-flex items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-[11px] text-slate-600">
                <Shield className="h-3.5 w-3.5" />
                Read-only view — no execute / cancel / write actions are surfaced on this page.
              </p>
            </div>
          </div>
        </section>
      </PageShell>
    </AppLayout>
  );
}

