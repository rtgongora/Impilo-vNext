"use client";

/**
 * Displays the triage of record as the backend decided it. This component derives NOTHING —
 * it renders the acuity, the tool, the IITT priority and the clinician-review flag exactly as
 * pct returned them (WhoIittEngine is the authority; the demotion of ESI/MTS to advisory and the
 * acuity mapping all happen server-side). That is deliberate: the estate's rule is that TypeScript
 * presents and Java decides, so there is no threshold, no acuity derivation and no priority logic
 * here — only presentation of a server-computed decision.
 */

export type TriageAssessment = {
  acuity?: number;
  triage_system?: string;
  triage_tool?: string | null;
  iitt_priority?: string | null;
  requires_clinician_review?: boolean;
  review_reason?: string | null;
  advisory_scores?: unknown;
  created_at?: string;
};

const PRIORITY_STYLE: Record<string, string> = {
  RED: "bg-red-600 text-white",
  YELLOW: "bg-amber-400 text-black",
  GREEN: "bg-emerald-500 text-white",
};

export function TriageOfRecordPanel({ triage }: { triage?: TriageAssessment | null }) {
  if (!triage) {
    return (
      <section className="rounded-xl border p-4 text-sm text-muted-foreground" data-testid="triage-of-record-empty">
        No triage recorded yet.
      </section>
    );
  }

  const priority = triage.iitt_priority ?? null;
  const tool = triage.triage_tool ?? triage.triage_system ?? "—";
  const isIitt = tool === "WHO_IITT";

  return (
    <section className="rounded-xl border p-4 space-y-3" data-testid="triage-of-record">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold">Triage of record</h2>
        <span className="text-xs text-muted-foreground" data-testid="triage-tool">
          {isIitt ? "WHO IITT (authoritative)" : `${tool} (manual entry)`}
        </span>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        {priority ? (
          <span
            className={`rounded-full px-3 py-1 text-sm font-semibold ${PRIORITY_STYLE[priority] ?? "bg-neutral-200 text-black"}`}
            data-testid="iitt-priority"
          >
            IITT {priority}
          </span>
        ) : null}
        <span className="text-sm" data-testid="acuity-of-record">
          Acuity <strong>{triage.acuity ?? "—"}</strong>
        </span>
      </div>

      {triage.requires_clinician_review ? (
        <div
          className="rounded-lg border border-amber-500 bg-amber-50 p-3 text-sm text-amber-900"
          role="alert"
          data-testid="clinician-review-banner"
        >
          <strong>Clinician review required.</strong>{" "}
          {triage.review_reason ?? "A cross-system discrepancy or high-risk vital sign needs a supervising clinician."}
        </div>
      ) : null}

      {triage.advisory_scores ? (
        <p className="text-xs text-muted-foreground" data-testid="advisory-note">
          ESI/MTS advisory scores recorded — advisory only, they do not set the acuity of record.
        </p>
      ) : null}
    </section>
  );
}
