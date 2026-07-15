"use client";

/**
 * Surgical-case bundle — the itemised charges COSTA posts when a theatre case
 * completes (source_event `theatre.case.completed`), grouped per procedure
 * episode. Renders only when the encounter actually carries bundle lines, so it
 * stays invisible on non-surgical encounters. Read-only.
 */

import { Scissors, Loader2 } from "lucide-react";
import { useSurgicalBundle } from "@/hooks/queries/useSurgicalBundle";

function money(value: number | null): string {
  if (value == null) return "—";
  return value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function SurgicalBundleCard({ encounterId }: { encounterId: string }) {
  const { data, isLoading, isError } = useSurgicalBundle(encounterId, Boolean(encounterId));

  // Stay out of the way entirely on non-surgical encounters.
  if (!isLoading && (isError || !data || data.lineCount === 0)) return null;

  return (
    <section
      className="rounded-xl border border-border bg-card p-5 shadow-sm"
      data-testid="surgical-bundle-card"
    >
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-rose-100 text-rose-700">
          <Scissors className="h-5 w-5" />
        </div>
        <div className="flex-1">
          <h2 className="text-sm font-semibold text-foreground">Surgical case bundle</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            Itemised theatre charges posted by COSTA on <code className="text-[10px]">theatre.case.completed</code>,
            grouped per procedure episode.
          </p>

          {isLoading && (
            <p className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading surgical bundle…
            </p>
          )}

          {!isLoading &&
            data?.cases.map((surgicalCase) => (
              <div
                key={surgicalCase.episodeRef}
                className="mt-3 overflow-x-auto rounded-lg border border-border"
                data-testid={`surgical-bundle-case-${surgicalCase.episodeRef}`}
              >
                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border bg-background px-3 py-2">
                  <span className="text-xs text-muted-foreground">
                    Episode <span className="font-mono text-[11px] text-foreground">{surgicalCase.episodeRef}</span>
                  </span>
                  <span className="text-xs font-medium text-foreground">
                    Bundle subtotal: {money(surgicalCase.subtotal)}
                  </span>
                </div>
                <table className="w-full text-xs">
                  <thead className="bg-background text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2 text-left font-medium">Code</th>
                      <th className="px-3 py-2 text-left font-medium">Description</th>
                      <th className="px-3 py-2 text-right font-medium">Qty</th>
                      <th className="px-3 py-2 text-right font-medium">Unit</th>
                      <th className="px-3 py-2 text-right font-medium">Amount</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {surgicalCase.lines.map((line) => (
                      <tr key={line.lineId} data-testid={`surgical-bundle-line-${line.msikaCode ?? line.lineId}`}>
                        <td className="px-3 py-2 font-mono text-[11px]">{line.msikaCode ?? "—"}</td>
                        <td className="px-3 py-2">{line.description ?? "—"}</td>
                        <td className="px-3 py-2 text-right">{line.qty ?? "—"}</td>
                        <td className="px-3 py-2 text-right">{money(line.unitPrice)}</td>
                        <td className="px-3 py-2 text-right font-medium">{money(line.amount)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
        </div>
      </div>
    </section>
  );
}
