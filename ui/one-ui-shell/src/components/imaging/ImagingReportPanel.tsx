"use client";

/**
 * Viewer-linked radiology report authoring (R7/G22). The full report lifecycle (draft→final→amend/
 * addendum→release) already exists in OROS + the BFF + the standalone /diagnostics/reporting page,
 * but the DICOM viewer had no way to author a report against the study it is showing. This panel
 * reuses the existing report hooks, seeded from the viewer's orderId — no new BFF/engine.
 */

import { useState } from "react";
import { FileSignature, Loader2 } from "lucide-react";
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

export function ImagingReportPanel({ orderId }: { orderId: string }) {
  const [action, setAction] = useState<ReportAction>("preliminary");
  const [impression, setImpression] = useState("");
  const [recommendations, setRecommendations] = useState("");
  const [summary, setSummary] = useState("");
  const [reason, setReason] = useState("");

  const versionsQ = useOrderReportVersions(orderId);
  const authorMut = useAuthorReport();
  const releaseMut = useReleaseReport();
  const versions = versionsQ.data?.data ?? [];
  const needsReason = action === "amend" || action === "addendum";
  const canSubmit = (!needsReason || reason.trim().length > 0) && !authorMut.isPending;

  function submit(e: React.FormEvent) {
    e.preventDefault();
    authorMut.mutate(
      {
        orderId,
        action,
        summary: summary.trim() || undefined,
        impression: impression.trim() || undefined,
        recommendations: recommendations.trim() || undefined,
        reason: needsReason ? reason.trim() : undefined,
      },
      { onSuccess: () => { if (!needsReason) { setImpression(""); setRecommendations(""); setSummary(""); } setReason(""); } },
    );
  }

  return (
    <section className="mt-4 rounded-xl border border-border bg-card p-4" data-testid="imaging-report-panel">
      <div className="mb-3 flex items-center gap-2">
        <FileSignature className="h-4 w-4 text-primary" />
        <h3 className="text-sm font-semibold text-foreground">Report</h3>
        <span className="font-mono text-[11px] text-muted-foreground">{orderId}</span>
      </div>

      <form onSubmit={submit} className="space-y-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-xs text-muted-foreground">
            Action
            <select value={action} onChange={(e) => setAction(e.target.value as ReportAction)}
              className="mt-0.5 block w-full rounded border border-border bg-card px-2 py-1.5 text-sm" aria-label="Report action">
              {ACTIONS.map((a) => <option key={a.value} value={a.value}>{a.label}</option>)}
            </select>
          </label>
          {needsReason && (
            <label className="block text-xs text-muted-foreground">
              Reason *
              <input value={reason} onChange={(e) => setReason(e.target.value)}
                className="mt-0.5 block w-full rounded border border-border px-2 py-1.5 text-sm" aria-label="Reason" />
            </label>
          )}
        </div>
        <textarea value={impression} onChange={(e) => setImpression(e.target.value)} rows={2}
          placeholder="Impression" className="w-full rounded border border-border px-2 py-1.5 text-sm" aria-label="Impression" />
        <textarea value={recommendations} onChange={(e) => setRecommendations(e.target.value)} rows={2}
          placeholder="Recommendations" className="w-full rounded border border-border px-2 py-1.5 text-sm" aria-label="Recommendations" />
        <textarea value={summary} onChange={(e) => setSummary(e.target.value)} rows={2}
          placeholder="Findings" className="w-full rounded border border-border px-2 py-1.5 text-sm" aria-label="Findings" />
        <div className="flex items-center gap-3">
          <button type="submit" disabled={!canSubmit}
            className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50">
            {authorMut.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileSignature className="h-4 w-4" />}
            Save report
          </button>
          {authorMut.isError && <span className="text-xs text-red-600" role="alert">Failed to save.</span>}
          {authorMut.isSuccess && <span className="text-xs text-green-600" role="status">Saved.</span>}
        </div>
      </form>

      <div className="mt-4">
        <p className="mb-2 text-xs font-semibold uppercase text-muted-foreground">Report versions</p>
        {versionsQ.isLoading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
        ) : versions.length === 0 ? (
          <p className="text-sm text-muted-foreground">No report versions yet.</p>
        ) : (
          <ul className="space-y-1.5 text-sm">
            {versions.map((r) => (
              <li key={r.resultId} data-testid="report-version-row" className="flex items-center justify-between gap-2 rounded-lg bg-background px-3 py-2">
                <span>v{r.version} · {r.reportStatus ?? "—"}{r.critical ? " · CRITICAL" : ""}</span>
                {!r.releasedAt && (
                  <button type="button"
                    onClick={() => releaseMut.mutate({ resultId: r.resultId, orderId })}
                    disabled={releaseMut.isPending && releaseMut.variables?.resultId === r.resultId}
                    className="rounded border border-border px-2 py-1 text-xs hover:bg-card disabled:opacity-50">
                    Release
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
