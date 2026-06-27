"use client";

/**
 * Report Authoring — author preliminary/final reports, amend/addendum, and release (W5, criterion G).
 * Route: /diagnostics/reporting?orderId=… | Zone: lab | Guard: facility
 *
 * Real path: UI → BFF (/internal/v1/diagnostics/results/*) → OROS. A finalized report is never
 * overwritten — amend/addendum create new versions (enforced server-side); history is shown below.
 */

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useOrderReportVersions,
  useAuthorReport,
  useReleaseReport,
  type ReportAction,
} from "@/hooks/queries/useDiagnosticsOrders";

const ACTIONS: { value: ReportAction; label: string }[] = [
  { value: "preliminary", label: "Preliminary" },
  { value: "final", label: "Final" },
  { value: "amend", label: "Amend" },
  { value: "addendum", label: "Addendum" },
];

function ReportingInner() {
  const params = useSearchParams();
  const [orderId, setOrderId] = useState(params.get("orderId") ?? "");
  const [action, setAction] = useState<ReportAction>("preliminary");
  const [impression, setImpression] = useState("");
  const [recommendations, setRecommendations] = useState("");
  const [summary, setSummary] = useState("");
  const [reason, setReason] = useState("");

  const versionsQ = useOrderReportVersions(orderId.trim() || undefined);
  const authorMut = useAuthorReport();
  const releaseMut = useReleaseReport();
  const versions = versionsQ.data?.data ?? [];
  const needsReason = action === "amend" || action === "addendum";
  const canSubmit = orderId.trim().length > 0 && (!needsReason || reason.trim().length > 0) && !authorMut.isPending;

  function submit(e: React.FormEvent) {
    e.preventDefault();
    authorMut.mutate({
      orderId: orderId.trim(),
      action,
      summary: summary.trim() || undefined,
      impression: impression.trim() || undefined,
      recommendations: recommendations.trim() || undefined,
      reason: needsReason ? reason.trim() : undefined,
    });
  }

  return (
    <>
      <form onSubmit={submit} className="max-w-2xl space-y-4">
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Order ID<span className="text-danger"> *</span></span>
          <input value={orderId} onChange={(e) => setOrderId(e.target.value)}
            className="impilo-pill-input w-full" aria-label="Order ID" placeholder="ORD-…" />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Report action</span>
          <select value={action} onChange={(e) => setAction(e.target.value as ReportAction)}
            className="impilo-pill-input w-full" aria-label="Report action">
            {ACTIONS.map((a) => <option key={a.value} value={a.value}>{a.label}</option>)}
          </select>
        </label>
        {needsReason && (
          <label className="block">
            <span className="mb-1 block text-sm font-medium">Reason<span className="text-danger"> *</span></span>
            <input value={reason} onChange={(e) => setReason(e.target.value)}
              className="impilo-pill-input w-full" aria-label="Reason" />
          </label>
        )}
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Impression</span>
          <textarea value={impression} onChange={(e) => setImpression(e.target.value)}
            className="impilo-pill-input w-full min-h-[70px] rounded-2xl py-2" aria-label="Impression" />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Recommendations</span>
          <textarea value={recommendations} onChange={(e) => setRecommendations(e.target.value)}
            className="impilo-pill-input w-full min-h-[70px] rounded-2xl py-2" aria-label="Recommendations" />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Findings (JSON or text)</span>
          <textarea value={summary} onChange={(e) => setSummary(e.target.value)}
            className="impilo-pill-input w-full min-h-[70px] rounded-2xl py-2" aria-label="Findings" />
        </label>
        <div className="flex items-center gap-3">
          <button type="submit" disabled={!canSubmit}
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50">
            {authorMut.isPending ? (
              <span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Saving…</span>
            ) : "Save report"}
          </button>
          {authorMut.isError && <span className="text-sm text-danger" role="alert">Failed to save report.</span>}
          {authorMut.isSuccess && <span className="text-sm text-success-foreground" role="status">Saved.</span>}
        </div>
      </form>

      <section className="mt-8">
        <h2 className="mb-3 text-sm font-semibold uppercase text-muted-foreground">Report history</h2>
        {!orderId.trim() ? (
          <p className="text-sm text-muted-foreground">Enter an order ID to view its report versions.</p>
        ) : versionsQ.isLoading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
        ) : versions.length === 0 ? (
          <p className="text-sm text-muted-foreground">No report versions yet.</p>
        ) : (
          <div className="overflow-hidden rounded-lg border border-gray-100">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="px-4 py-2">v</th><th className="px-4 py-2">Status</th>
                  <th className="px-4 py-2">Author</th><th className="px-4 py-2">Released</th>
                  <th className="px-4 py-2">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {versions.map((r) => (
                  <tr key={r.resultId} data-testid="report-version-row">
                    <td className="px-4 py-3">{r.version}</td>
                    <td className="px-4 py-3">{r.reportStatus ?? "—"}{r.critical ? " · CRITICAL" : ""}</td>
                    <td className="px-4 py-3">{r.validatedBy ?? r.reportedBy ?? "—"}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{r.releasedAt ? new Date(r.releasedAt).toLocaleString() : "—"}</td>
                    <td className="px-4 py-3">
                      {!r.releasedAt && (
                        <button type="button"
                          onClick={() => releaseMut.mutate({ resultId: r.resultId, orderId: orderId.trim() })}
                          disabled={releaseMut.isPending && releaseMut.variables?.resultId === r.resultId}
                          className="rounded-md bg-primary px-3 py-1 text-xs font-medium text-primary-foreground disabled:opacity-50">
                          Release
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}

export default function ReportingPage() {
  return (
    <AppLayout>
      <PageShell title="Report Authoring" subtitle="Author, amend, and release diagnostic reports">
        <Suspense fallback={<div className="p-8 text-sm text-muted-foreground">Loading…</div>}>
          <ReportingInner />
        </Suspense>
      </PageShell>
    </AppLayout>
  );
}
