"use client";

import { useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAdjudicationQueue, useAbisStats, type CaseStatus } from "@/hooks/queries/useAbisAdjudication";
import { Scale, Loader2 } from "lucide-react";

/**
 * ABIS adjudication console — the duplicate-investigation queue opened by enrol-time dedup
 * and 1:N identify. Reviewers work cases here; a merge is a governed, dual-control action
 * (never automatic) performed on the case detail page.
 */
const STATUS_TABS: Array<{ label: string; value: CaseStatus | "" }> = [
  { label: "Open", value: "OPEN" },
  { label: "In review", value: "IN_REVIEW" },
  { label: "Resolved", value: "RESOLVED" },
  { label: "All", value: "" },
];

export default function AdjudicationConsole() {
  const [status, setStatus] = useState<CaseStatus | "">("OPEN");
  const queue = useAdjudicationQueue(status);
  const stats = useAbisStats();

  return (
    <AppLayout>
      <PageShell
        title="Biometric adjudication console"
        subtitle="Work the duplicate-investigation queue. Merges are dual-control and never automatic."
        icon={<Scale className="h-5 w-5" />}
      >
        <div className="space-y-6">
          {/* Stats strip */}
          {stats.data && (
            <div data-testid="stats-strip" className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <StatTile label="Templates (active)" value={stats.data.templates?.active ?? 0} />
              <StatTile label="Open cases" value={stats.data.adjudication?.OPEN ?? 0} />
              <StatTile label="In review" value={stats.data.adjudication?.IN_REVIEW ?? 0} />
              <StatTile label="Resolved" value={stats.data.adjudication?.RESOLVED ?? 0} />
            </div>
          )}

          <div className="flex gap-2" role="tablist">
            {STATUS_TABS.map((t) => (
              <button
                key={t.label}
                data-testid={`tab-${t.value || "ALL"}`}
                onClick={() => setStatus(t.value)}
                className={`rounded-md border px-3 py-1.5 text-sm ${
                  status === t.value ? "bg-primary text-primary-foreground" : "bg-background"
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>

          {queue.isLoading && (
            <p className="text-sm flex items-center gap-1"><Loader2 className="h-4 w-4 animate-spin" /> Loading queue…</p>
          )}
          {queue.isError && <p data-testid="queue-error" className="text-sm text-red-600">Failed to load the queue.</p>}

          {queue.data && (
            <div className="overflow-x-auto rounded-lg border">
              <table className="w-full text-sm">
                <thead className="bg-muted/50 text-left">
                  <tr>
                    <th className="p-2">Case</th>
                    <th className="p-2">Subject</th>
                    <th className="p-2">Modality</th>
                    <th className="p-2">Reason</th>
                    <th className="p-2">Candidates</th>
                    <th className="p-2">Top score</th>
                    <th className="p-2">Status</th>
                    <th className="p-2">Decision</th>
                  </tr>
                </thead>
                <tbody data-testid="queue-body">
                  {queue.data.content.length === 0 && (
                    <tr>
                      <td colSpan={8} className="p-4 text-center text-muted-foreground" data-testid="queue-empty">
                        No cases in this view.
                      </td>
                    </tr>
                  )}
                  {queue.data.content.map((c) => (
                    <tr key={c.id} className="border-t hover:bg-muted/30" data-testid={`case-row-${c.id}`}>
                      <td className="p-2">
                        <Link href={`/operations/vito/adjudication/${c.id}`} className="text-primary underline">
                          #{c.id}
                        </Link>
                      </td>
                      <td className="p-2 font-mono text-xs">{c.subjectRef}</td>
                      <td className="p-2">{c.modality}</td>
                      <td className="p-2">{c.reason}</td>
                      <td className="p-2">{c.candidateCount}</td>
                      <td className="p-2">{c.topScore != null ? `${(c.topScore * 100).toFixed(1)}%` : "—"}</td>
                      <td className="p-2">{c.status}</td>
                      <td className="p-2">{c.decision ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function StatTile({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border p-3">
      <div className="text-2xl font-semibold">{value}</div>
      <div className="text-xs text-muted-foreground">{label}</div>
    </div>
  );
}
