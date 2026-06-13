"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
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
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
        </div>

        <div className="max-w-4xl space-y-6">
          <FinancePayerOpsReconciliationNotice />

          <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Import statement</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              POST <code className="text-[11px]">/internal/v1/finance/reconciliation/import-statement</code> with a JSON
              array of statement lines (see MusheX OpenAPI <code className="text-[11px]">ReconImportLine</code>).
            </p>
            <textarea
              className="mt-3 w-full min-h-[140px] rounded-lg border border-border p-2 font-mono text-xs"
              value={importJson}
              onChange={(e) => setImportJson(e.target.value)}
              aria-label="Import statement JSON"
            />
            {importError ? <p className="mt-2 text-xs text-danger">{importError}</p> : null}
            <button
              type="button"
              disabled={importM.isPending}
              className="mt-3 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-50"
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
              <p className="mt-3 rounded-lg border border-success/25 bg-success-soft px-3 py-2 text-xs text-primary-hover">
                Statement import accepted. Fetch unmatched entries to reconcile lines.
              </p>
            ) : null}
          </div>

          <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Unmatched</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET <code className="text-[11px]">/internal/v1/finance/reconciliation/unmatched</code> with optional{" "}
              <code className="text-[11px]">page</code> and <code className="text-[11px]">size</code>.
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <label className="text-xs text-muted-foreground">
                Page
                <input
                  type="number"
                  className="mt-1 block w-24 rounded-lg border border-border px-2 py-1.5 text-sm"
                  value={page}
                  onChange={(e) => setPage(Number(e.target.value))}
                />
              </label>
              <label className="text-xs text-muted-foreground">
                Size
                <input
                  type="number"
                  className="mt-1 block w-24 rounded-lg border border-border px-2 py-1.5 text-sm"
                  value={size}
                  onChange={(e) => setSize(Number(e.target.value))}
                />
              </label>
              <button
                type="button"
                className="rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium hover:bg-background"
                onClick={() => setUnmatchedArmed(true)}
              >
                Fetch unmatched
              </button>
              <button
                type="button"
                disabled={!unmatchedArmed || unmatchedQ.isFetching}
                className="rounded-lg border border-border px-3 py-2 text-sm hover:bg-background disabled:opacity-50"
                onClick={() => void unmatchedQ.refetch()}
              >
                Refresh
              </button>
            </div>
            {unmatchedQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : unmatchedQ.isError ? (
              <p className="mt-3 text-sm text-danger">Request failed (403 if your role is not allowed).</p>
            ) : unmatchedSummary ? (
              <div className="mt-3 space-y-2">
                {typeof unmatchedSummary.totalElements === "number" ? (
                  <p className="text-xs text-muted-foreground">Total elements: {String(unmatchedSummary.totalElements)}</p>
                ) : null}
                <JsonApiDataTable
                  data={unmatchedQ.data}
                  columns={[
                    { key: "recon", header: "Entry", fields: ["reconId", "id", "entryId"] },
                    { key: "amount", header: "Amount", fields: ["amount", "statementAmount"] },
                    { key: "ref", header: "Reference", fields: ["statementRef", "reference"] },
                    { key: "status", header: "Status", fields: ["status", "matchStatus"] },
                  ]}
                  isLoading={unmatchedQ.isPending}
                  error={unmatchedQ.error as Error | null}
                  emptyHint="No unmatched reconciliation rows."
                />
              </div>
            ) : unmatchedArmed ? (
              <p className="mt-3 text-xs text-muted-foreground">No data.</p>
            ) : (
              <p className="mt-3 text-xs text-muted-foreground">Choose Fetch unmatched to call the BFF.</p>
            )}
          </div>

          <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Match entry</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              POST <code className="text-[11px]">/internal/v1/finance/reconciliation/match?reconId=…</code> with body{" "}
              <code className="text-[11px]">{"{ \"intentId\": \"…\" }"}</code> (MusheX contract).
            </p>
            <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <input
                type="text"
                placeholder="recon id"
                value={reconId}
                onChange={(e) => setReconId(e.target.value)}
                className="rounded-lg border border-border px-3 py-2 text-sm font-mono min-w-[200px]"
                aria-label="Reconciliation entry id"
              />
              <input
                type="text"
                placeholder="intent id to match"
                value={matchIntentId}
                onChange={(e) => setMatchIntentId(e.target.value)}
                className="rounded-lg border border-border px-3 py-2 text-sm font-mono min-w-[200px]"
                aria-label="Payment intent id for match"
              />
              <button
                type="button"
                disabled={matchM.isPending || !reconId.trim() || !matchIntentId.trim()}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-50"
                onClick={() =>
                  matchM.mutate({ reconId: reconId.trim(), body: { intentId: matchIntentId.trim() } })
                }
              >
                {matchM.isPending ? "Matching…" : "Post match"}
              </button>
            </div>
            {matchM.data != null ? (
              <p className="mt-3 rounded-lg border border-success/25 bg-success-soft px-3 py-2 text-xs text-primary-hover">
                Match posted for reconciliation entry {reconId}.
              </p>
            ) : null}
          </div>

          <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Triple-source match (intent ⇄ settlement ⇄ invoice)</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET <code className="text-[11px]">/internal/v1/finance/reconciliation/triple-match?encounterId=…</code>.
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <label className="text-xs text-muted-foreground">
                Encounter id
                <input
                  type="text"
                  className="mt-1 block min-w-[220px] rounded-lg border border-border px-2 py-1.5 text-sm font-mono"
                  value={encounterId}
                  onChange={(e) => setEncounterId(e.target.value)}
                  aria-label="Triple match encounter id"
                />
              </label>
              <button
                type="button"
                disabled={!encounterId.trim()}
                className="rounded-lg border border-border px-3 py-2 text-sm hover:bg-background disabled:opacity-50"
                onClick={() => setTripleArmed(true)}
              >
                Fetch triple match
              </button>
            </div>
            {tripleQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading triple-source rows…
              </p>
            ) : tripleQ.isError ? (
              <p className="mt-3 text-sm text-danger">Triple-source request failed.</p>
            ) : tripleQ.data && typeof tripleQ.data === "object" ? (
              <div className="mt-3">
                <JsonApiDataTable
                  data={tripleQ.data}
                  columns={[
                    { key: "intent", header: "Intent", fields: ["intentId", "paymentIntentId"] },
                    { key: "settlement", header: "Settlement", fields: ["settlementId"] },
                    { key: "invoice", header: "Invoice", fields: ["invoiceId"] },
                    { key: "status", header: "Status", fields: ["status", "matchStatus"] },
                  ]}
                  isLoading={tripleQ.isPending}
                  error={tripleQ.error as Error | null}
                />
              </div>
            ) : tripleArmed ? (
              <p className="mt-3 text-xs text-muted-foreground">No triple-source rows returned.</p>
            ) : null}
          </div>

          <p className="text-xs text-muted-foreground">
            <Link href="/finance/commerce-integrations" className="text-primary-hover hover:underline">
              Commerce and payer integration map
            </Link>
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
