"use client";

/**
 * Complaints involving me (ROM respondent self-service). The signed-in professional sees every
 * complaint or disciplinary matter where they are the subject of a regulatory process. Reads
 * /internal/v1/me/regulatory/complaints, which resolves the caller's OWN record from the trust
 * context (never a providerId param), so this is genuinely self-service.
 *
 * This is procedural-fairness UI: the tone is respectful and factual. A complaint is not a finding —
 * matters are decided by the council following due process — so nothing here is styled to alarm.
 *
 * Route: /professional/regulatory/complaints
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2, Scale, ScrollText, ShieldCheck } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface Complaint {
  id: number;
  caseStage: string;
  status: string;
  summary?: string | null;
  respondentNotified: boolean;
  openedDate?: string | null;
}

/** Tolerate raw-or-{data} and array-or-{data:array} envelopes from the BFF. */
function unwrapComplaints(payload: unknown): Complaint[] {
  const p = (payload as { data?: unknown })?.data ?? payload;
  if (!Array.isArray(p)) return [];
  return p
    .filter((x): x is Record<string, unknown> => typeof x === "object" && x !== null && "id" in x)
    .map((x) => ({
      id: Number(x.id),
      caseStage: typeof x.caseStage === "string" ? x.caseStage : "",
      status: typeof x.status === "string" ? x.status : "",
      summary: typeof x.summary === "string" ? x.summary : null,
      respondentNotified: x.respondentNotified === true,
      openedDate: typeof x.openedDate === "string" ? x.openedDate : null,
    }));
}

/** Human-readable stage labels for the disciplinary lifecycle. */
const STAGE_LABELS: Record<string, string> = {
  RECEIVED: "Received",
  PRELIMINARY_ASSESSMENT: "Preliminary assessment",
  UNDER_INVESTIGATION: "Under investigation",
  CHARGES_FORMULATED: "Charges formulated",
  REFERRED_TO_COMMITTEE: "Referred to committee",
  HEARING: "Hearing",
  DETERMINED: "Determined",
  APPEALED: "Appealed",
  CLOSED: "Closed",
};

function stageLabel(stage: string): string {
  return STAGE_LABELS[stage] ?? stage.replace(/_/g, " ").toLowerCase();
}

export default function ComplaintsInvolvingMePage() {
  const [complaints, setComplaints] = useState<Complaint[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/me/regulatory/complaints");
      setComplaints(unwrapComplaints(res));
    } catch {
      setError("Could not load complaints involving you. Please try again.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell
        title="Complaints involving me"
        subtitle="Matters where you are the subject of a regulatory process"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          <Link
            href="/professional/regulatory"
            className="inline-flex items-center gap-1.5 text-xs font-medium text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Back to My Regulatory Affairs
          </Link>

          {loading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading…
            </div>
          ) : error ? (
            <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>
          ) : !complaints || complaints.length === 0 ? (
            <div className="rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground">
              There are no complaints or disciplinary matters involving you.
            </div>
          ) : (
            <div className="space-y-3">
              {complaints.map((c) => (
                <ComplaintCard key={c.id} complaint={c} />
              ))}
            </div>
          )}

          <p className="flex items-start gap-1.5 text-[11px] text-muted-foreground">
            <ScrollText className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            A complaint is not a finding. Matters are decided by the council following due process; a
            rating or complaint never changes your registration on its own.
          </p>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

function ComplaintCard({ complaint }: { complaint: Complaint }) {
  return (
    <section className="rounded-2xl border border-amber-200 bg-amber-50/60 p-4">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Scale className="h-4 w-4 text-amber-700" />
          <span className="text-sm font-semibold text-foreground">{stageLabel(complaint.caseStage)}</span>
        </div>
        <span className="rounded-full border border-amber-200 bg-amber-100 px-2 py-0.5 text-[11px] font-medium text-amber-800">
          {(complaint.status || "—").replace(/_/g, " ").toLowerCase()}
        </span>
      </div>
      {complaint.summary && <p className="text-sm text-foreground">{complaint.summary}</p>}
      <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
        <div>
          <dt className="text-muted-foreground">Stage</dt>
          <dd className="text-foreground">{stageLabel(complaint.caseStage)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Opened</dt>
          <dd className="text-foreground">{complaint.openedDate || "—"}</dd>
        </div>
      </dl>
      {complaint.respondentNotified && (
        <div className="mt-3 flex items-start gap-2 rounded-lg border border-teal-200 bg-teal-50 p-2.5 text-xs text-teal-800">
          <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          <span>You have been formally notified and have the right to respond.</span>
        </div>
      )}
    </section>
  );
}
