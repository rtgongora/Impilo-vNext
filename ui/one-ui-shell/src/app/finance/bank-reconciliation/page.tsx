"use client";

/**
 * Bank → wallet cash-in reconciliation.
 * Route: /finance/bank-reconciliation
 *
 * A finance user pastes/enters collection-account statement lines; each line is
 * matched to a pending deposit intent by its Impilo reference code (quoted in
 * the narrative) and, when the amount agrees, the deposit is confirmed and the
 * wallet credited. Unmatched lines are surfaced with a reason — money is never
 * force-credited.
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, Landmark, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useStatementMatch, type StatementLine } from "@/hooks/queries/useStatementMatch";

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}
function unwrapList(v: unknown): unknown[] {
  if (!v) return [];
  if (Array.isArray(v)) return v;
  const r = v as Record<string, unknown>;
  if (Array.isArray(r.data)) return unwrapList(r.data);
  if (Array.isArray(r.items)) return r.items;
  return [];
}

/**
 * Parse pasted statement text. One line per transaction:
 *   narrative | amount | bankRef
 * Pipe-, tab-, or comma-separated; amount and bankRef optional per line.
 */
function parseLines(text: string): StatementLine[] {
  return text
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean)
    .map((l) => {
      const parts = l.split(/\s*[|\t,]\s*/);
      const narrative = parts[0] ?? "";
      const amount = Number(parts[1] ?? "");
      const bankRef = parts[2] ?? "";
      return { narrative, amount: Number.isFinite(amount) ? amount : 0, bankRef };
    });
}

export default function BankReconciliationPage() {
  const [raw, setRaw] = useState("");
  const match = useStatementMatch();

  const parsed = useMemo(() => parseLines(raw), [raw]);
  const outcomes = useMemo(() => unwrapList(match.data).map(asRecord), [match.data]);
  const matchedCount = outcomes.filter((o) => o.result === "MATCHED").length;

  const submit = () => {
    if (parsed.length === 0) return;
    match.mutate({ lines: parsed });
  };

  return (
    <AppLayout>
      <PageShell
        title="Bank Reconciliation"
        subtitle="Match collection-account statement lines to pending wallet deposits"
        icon={<Landmark className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/finance"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to finance
          </Link>
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          {/* Input */}
          <section className="rounded-xl border border-border bg-card shadow-sm p-5 space-y-3">
            <h3 className="text-sm font-semibold text-foreground">Statement lines</h3>
            <p className="text-xs text-muted-foreground">
              One line per transaction: <code>narrative | amount | bankRef</code>. The depositor&apos;s
              Impilo reference (e.g. <code>IMP-ABCD2345</code>) must appear in the narrative.
            </p>
            <textarea
              className="w-full h-48 rounded-lg border border-border px-3 py-2 text-sm font-mono"
              placeholder={"ZIPIT FRM J MOYO REF IMP-ABCD2345 | 50.00 | BANK-TX-1\nZIPIT FRM A NCUBE REF IMP-EFGH6789 | 20.00 | BANK-TX-2"}
              value={raw}
              onChange={(e) => setRaw(e.target.value)}
            />
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={submit}
                disabled={match.isPending || parsed.length === 0}
                className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
              >
                {match.isPending && <Loader2 className="h-4 w-4 animate-spin inline mr-1" />}
                Match {parsed.length > 0 ? `${parsed.length} line${parsed.length === 1 ? "" : "s"}` : ""}
              </button>
              {match.isError && (
                <span className="text-xs text-danger">Reconciliation failed. Please try again.</span>
              )}
            </div>
          </section>

          {/* Results */}
          <section className="rounded-xl border border-border bg-card shadow-sm">
            <div className="flex items-center justify-between border-b border-border px-4 py-3 bg-background/80">
              <h3 className="text-sm font-semibold text-foreground">Results</h3>
              {outcomes.length > 0 && (
                <span className="text-xs text-muted-foreground">
                  {matchedCount}/{outcomes.length} matched
                </span>
              )}
            </div>
            <div className="p-4">
              {outcomes.length === 0 && !match.isPending && (
                <p className="text-sm text-muted-foreground">
                  Paste statement lines and run the match to confirm pending deposits.
                </p>
              )}
              {outcomes.length > 0 && (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-left text-xs text-muted-foreground">
                        <th className="pb-2 pr-3 font-medium">Bank ref</th>
                        <th className="pb-2 pr-3 font-medium">Reference</th>
                        <th className="pb-2 pr-3 font-medium">Result</th>
                        <th className="pb-2 font-medium">Detail</th>
                      </tr>
                    </thead>
                    <tbody>
                      {outcomes.map((o, i) => {
                        const result = String(o.result ?? "");
                        return (
                          <tr key={i} className="border-t border-border align-top">
                            <td className="py-2 pr-3 font-mono text-xs">{String(o.bankRef ?? "—")}</td>
                            <td className="py-2 pr-3 font-mono text-xs">{String(o.referenceCode ?? "—")}</td>
                            <td className="py-2 pr-3">
                              <span
                                className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                                  result === "MATCHED"
                                    ? "bg-success-soft text-primary-hover"
                                    : "bg-warning-soft text-warning-foreground"
                                }`}
                              >
                                {result || "—"}
                              </span>
                            </td>
                            <td className="py-2 text-xs text-muted-foreground">{String(o.detail ?? "")}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
