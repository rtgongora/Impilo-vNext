"use client";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FinancePayerOpsReconciliationNotice } from "@/components/FinanceAccessNotice";
import {
  useReconciliationImportStatement,
  useReconciliationMatch,
  useReconciliationTripleMatch,
  useReconciliationUnmatched,
} from "@/hooks/queries/useFinanceReconciliation";

const DEFAULT_IMPORT_LINES = `[
  {
    "statementRef": "STMT-001",
    "statementDate": "2026-04-01",
    "amount": 100,
    "currency": "USD",
    "counterparty": "Sample Bank"
  }
]`;

export default function FinanceReconciliationPage() {
  const [importJson, setImportJson] = useState(DEFAULT_IMPORT_LINES);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [unmatchedArmed, setUnmatchedArmed] = useState(false);
  const unmatchedQ = useReconciliationUnmatched({ page, size }, unmatchedArmed);

  const importM = useReconciliationImportStatement();
  const matchM = useReconciliationMatch();
  const [reconId, setReconId] = useState("");
  const [matchIntentId, setMatchIntentId] = useState("");
  const [importError, setImportError] = useState<string | null>(null);
  const [encounterId, setEncounterId] = useState("");
  const [tripleArmed, setTripleArmed] = useState(false);
  const tripleQ = useReconciliationTripleMatch(encounterId, tripleArmed);

  const unmatchedSummary = useMemo(() => {
    const d = unmatchedQ.data;
    if (d == null || typeof d !== "object") return null;
    return d as Record<string, unknown>;
  }, [unmatchedQ.data]);

  return (
    <AppLayout>
      <PageShell
        title="Reconciliation"
        subtitle="Import bank statement lines, list unmatched entries, and post matches — MusheX upstream via Experience BFF."
      >
        <div className="mb-4">
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
        </div>

        <div className="max-w-4xl space-y-6">
          <FinancePayerOpsReconciliationNotice />

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Import statement</h2>
            <p className="mt-1 text-xs text-slate-500">
              POST <code className="text-[11px]">/internal/v1/finance/reconciliation/import-statement</code> with a JSON
              array of statement lines (see MusheX OpenAPI <code className="text-[11px]">ReconImportLine</code>).
            </p>
            <textarea
              className="mt-3 w-full min-h-[140px] rounded-lg border border-slate-200 p-2 font-mono text-xs"
              value={importJson}
              onChange={(e) => setImportJson(e.target.value)}
              aria-label="Import statement JSON"
            />
            {importError ? <p className="mt-2 text-xs text-red-700">{importError}</p> : null}
            <button
              type="button"
              disabled={importM.isPending}
              className="mt-3 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
              onClick={() => {
                let body: unknown;
                try {
                  body = JSON.parse(importJson) as unknown;
                } catch {
                  setImportError("Invalid JSON.");
                  return;
                }
                setImportError(null);
                importM.mutate(body);
              }}
            >
              {importM.isPending ? (
                <span className="inline-flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" /> Importing…
                </span>
              ) : (
                "Import"
              )}
            </button>
            {importM.data != null ? (
              <QueryResultPanel title="Import M" isPending={importM.isPending} isLoading={importM.isPending} isError={importM.isError} error={importM.error} data={importM.data} />
            ) : null}
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Unmatched</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET <code className="text-[11px]">/internal/v1/finance/reconciliation/unmatched</code> with optional{" "}
              <code className="text-[11px]">page</code> and <code className="text-[11px]">size</code>.
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <label className="text-xs text-slate-600">
                Page
                <input
                  type="number"
                  className="mt-1 block w-24 rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
                  value={page}
                  onChange={(e) => setPage(Number(e.target.value))}
                />
              </label>
              <label className="text-xs text-slate-600">
                Size
                <input
                  type="number"
                  className="mt-1 block w-24 rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
                  value={size}
                  onChange={(e) => setSize(Number(e.target.value))}
                />
              </label>
              <button
                type="button"
                className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium hover:bg-slate-50"
                onClick={() => setUnmatchedArmed(true)}
              >
                Fetch unmatched
              </button>
              <button
                type="button"
                disabled={!unmatchedArmed || unmatchedQ.isFetching}
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50 disabled:opacity-50"
                onClick={() => void unmatchedQ.refetch()}
              >
                Refresh
              </button>
            </div>
            {unmatchedQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : unmatchedQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Request failed (403 if your role is not allowed).</p>
            ) : unmatchedSummary ? (
              <div className="mt-3 space-y-2 text-xs text-slate-600">
                {typeof unmatchedSummary.totalElements === "number" ? (
                  <p>Total elements: {String(unmatchedSummary.totalElements)}</p>
                ) : null}
                <QueryResultPanel title="Unmatched Q" isPending={unmatchedQ.isPending} isLoading={unmatchedQ.isPending} isError={unmatchedQ.isError} error={unmatchedQ.error} data={unmatchedQ.data} />
              </div>
            ) : unmatchedArmed ? (
              <p className="mt-3 text-xs text-slate-500">No data.</p>
            ) : (
              <p className="mt-3 text-xs text-slate-500">Choose Fetch unmatched to call the BFF.</p>
            )}
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Match entry</h2>
            <p className="mt-1 text-xs text-slate-500">
              POST <code className="text-[11px]">/internal/v1/finance/reconciliation/match?reconId=…</code> with body{" "}
              <code className="text-[11px]">{"{ \"intentId\": \"…\" }"}</code> (MusheX contract).
            </p>
            <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <input
                type="text"
                placeholder="recon id"
                value={reconId}
                onChange={(e) => setReconId(e.target.value)}
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono min-w-[200px]"
                aria-label="Reconciliation entry id"
              />
              <input
                type="text"
                placeholder="intent id to match"
                value={matchIntentId}
                onChange={(e) => setMatchIntentId(e.target.value)}
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono min-w-[200px]"
                aria-label="Payment intent id for match"
              />
              <button
                type="button"
                disabled={matchM.isPending || !reconId.trim() || !matchIntentId.trim()}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
                onClick={() =>
                  matchM.mutate({ reconId: reconId.trim(), body: { intentId: matchIntentId.trim() } })
                }
              >
                {matchM.isPending ? "Matching…" : "Post match"}
              </button>
            </div>
            {matchM.data != null ? (
              <QueryResultPanel title="Match M" isPending={matchM.isPending} isLoading={matchM.isPending} isError={matchM.isError} error={matchM.error} data={matchM.data} />
            ) : null}
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Triple-source match (intent ⇄ settlement ⇄ invoice)</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET <code className="text-[11px]">/internal/v1/finance/reconciliation/triple-match?encounterId=…</code>.
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <label className="text-xs text-slate-600">
                Encounter id
                <input
                  type="text"
                  className="mt-1 block min-w-[220px] rounded-lg border border-slate-200 px-2 py-1.5 text-sm font-mono"
                  value={encounterId}
                  onChange={(e) => setEncounterId(e.target.value)}
                  aria-label="Triple match encounter id"
                />
              </label>
              <button
                type="button"
                disabled={!encounterId.trim()}
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50 disabled:opacity-50"
                onClick={() => setTripleArmed(true)}
              >
                Fetch triple match
              </button>
            </div>
            {tripleQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading triple-source rows…
              </p>
            ) : tripleQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Triple-source request failed.</p>
            ) : tripleQ.data && typeof tripleQ.data === "object" ? (
              <QueryResultPanel title="Triple Q" isPending={tripleQ.isPending} isLoading={tripleQ.isPending} isError={tripleQ.isError} error={tripleQ.error} data={tripleQ.data} />
            ) : tripleArmed ? (
              <p className="mt-3 text-xs text-slate-500">No triple-source rows returned.</p>
            ) : null}
          </div>

          <p className="text-xs text-slate-500">
            <Link href="/finance/commerce-integrations" className="text-indigo-700 hover:underline">
              Commerce and payer integration map
            </Link>
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
