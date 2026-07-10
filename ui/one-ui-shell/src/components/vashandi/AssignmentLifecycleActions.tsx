"use client";

/**
 * Status-appropriate lifecycle actions for one workforce assignment.
 *
 * Precheck and Activate first obtain an OPA policy decision (the eligibility
 * gate requires an opaDecisionId — without it Vashandi answers "pending"),
 * then surface the eligibility verdict honestly: allowed / conditional
 * training / denied / pending upstream, with the per-dependency check list.
 */

import { useState } from "react";
import { CheckCircle2, Loader2, OctagonPause, PlayCircle, ShieldCheck, Square } from "lucide-react";
import type { VashandiActionResponse, WorkforceAssignment } from "@/lib/vashandi/types";
import {
  useActivateAssignment,
  useApproveAssignment,
  useEndAssignment,
  usePrecheckAssignment,
  useSuspendAssignment,
  useVashandiPrecheck,
} from "@/hooks/useVashandi";

interface EligibilityCheck {
  dependency?: string;
  name?: string;
  status?: string;
  detail?: string;
  message?: string;
}

interface EligibilityView {
  overallStatus?: string;
  message?: string;
  checks?: EligibilityCheck[];
}

function eligibilityFromResponse(response: VashandiActionResponse | undefined): EligibilityView | null {
  const data = response?.data;
  if (!data || typeof data !== "object") return null;
  const record = data as Record<string, unknown>;
  const eligibility = (record.eligibility ?? record) as Record<string, unknown>;
  if (!eligibility.overallStatus && !record.actionStatus) return null;
  return {
    overallStatus: (eligibility.overallStatus ?? record.actionStatus) as string | undefined,
    message: eligibility.message as string | undefined,
    checks: Array.isArray(eligibility.checks) ? (eligibility.checks as EligibilityCheck[]) : undefined,
  };
}

const VERDICT_TONE: Record<string, string> = {
  allowed: "border-emerald-200 bg-emerald-50 text-emerald-900",
  conditional_training: "border-amber-200 bg-amber-50 text-amber-900",
  denied: "border-rose-200 bg-rose-50 text-rose-900",
  pending: "border-sky-200 bg-sky-50 text-sky-900",
  pending_backend: "border-slate-200 bg-slate-50 text-slate-700",
};

const VERDICT_LABEL: Record<string, string> = {
  allowed: "Eligible — all dependency checks passed",
  conditional_training: "Conditional — a SOFT training requirement is unmet (governance override needed)",
  denied: "Not eligible — see failed checks below",
  pending: "Pending — awaiting policy decision",
  pending_backend: "Pending — an upstream registry is unavailable; retry shortly",
};

function EligibilityVerdict({ verdict }: { verdict: EligibilityView }) {
  const status = verdict.overallStatus ?? "pending";
  return (
    <div
      data-testid="eligibility-verdict"
      className={`mt-2 rounded-xl border px-3 py-2 text-xs ${VERDICT_TONE[status] ?? VERDICT_TONE.pending}`}
    >
      <p className="flex items-center gap-1.5 font-semibold">
        <ShieldCheck className="h-3.5 w-3.5" />
        {VERDICT_LABEL[status] ?? `Eligibility: ${status}`}
      </p>
      {verdict.message ? <p className="mt-1">{verdict.message}</p> : null}
      {verdict.checks && verdict.checks.length > 0 ? (
        <ul className="mt-1.5 space-y-0.5">
          {verdict.checks.map((check, index) => (
            <li key={`${check.dependency ?? check.name ?? index}`} className="flex items-start gap-1.5">
              <span className="font-medium">{check.dependency ?? check.name ?? "check"}:</span>
              <span>
                {check.status ?? "—"}
                {check.detail || check.message ? ` — ${check.detail ?? check.message}` : ""}
              </span>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

export function AssignmentLifecycleActions({ assignment }: { assignment: WorkforceAssignment }) {
  const status = (assignment.status ?? "").toLowerCase();
  const policyPrecheck = useVashandiPrecheck();
  const precheck = usePrecheckAssignment();
  const approve = useApproveAssignment();
  const activate = useActivateAssignment();
  const suspend = useSuspendAssignment();
  const end = useEndAssignment();

  const [verdict, setVerdict] = useState<EligibilityView | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const busy =
    policyPrecheck.isPending || precheck.isPending || approve.isPending ||
    activate.isPending || suspend.isPending || end.isPending;

  /** Obtain an OPA decision, then run the gated action with its decision id. */
  const runGated = async (
    requestedAction: string,
    gated: typeof precheck | typeof activate,
  ) => {
    setActionError(null);
    setVerdict(null);
    try {
      const policy = await policyPrecheck.mutateAsync({ requestedAction });
      const opaDecisionId = policy.policyDecision?.decisionId;
      const response = await gated.mutateAsync({
        assignmentId: assignment.id,
        body: opaDecisionId ? { opaDecisionId } : {},
      });
      const view = eligibilityFromResponse(response);
      if (view) setVerdict(view);
      if (response && response.success === false) {
        setActionError(response.friendlyMessage ?? "The action was not accepted.");
      }
    } catch (e) {
      setActionError(e instanceof Error ? e.message : "The action could not be submitted.");
    }
  };

  const runPlain = async (mutation: typeof approve, label: string) => {
    setActionError(null);
    setVerdict(null);
    try {
      const response = await mutation.mutateAsync({ assignmentId: assignment.id });
      if (response && response.success === false) {
        setActionError(response.friendlyMessage ?? `${label} was not accepted.`);
      }
    } catch (e) {
      setActionError(e instanceof Error ? e.message : `${label} could not be submitted.`);
    }
  };

  const button =
    "inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-medium disabled:opacity-50";

  return (
    <div className="w-full" data-testid={`assignment-actions-${assignment.id}`}>
      <div className="flex flex-wrap items-center gap-2">
        {["draft", "requested", "prechecked", "pending_approval"].includes(status) ? (
          <>
            <button
              type="button"
              disabled={busy}
              onClick={() => void runGated("ASSIGNMENT_PRECHECK", precheck)}
              data-testid="assignment-precheck"
              className={`${button} border-sky-300 text-sky-700 hover:bg-sky-50`}
            >
              {busy && precheck.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <ShieldCheck className="h-3.5 w-3.5" />}
              Run eligibility precheck
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => void runPlain(approve, "Approve")}
              data-testid="assignment-approve"
              className={`${button} border-emerald-300 text-emerald-700 hover:bg-emerald-50`}
            >
              <CheckCircle2 className="h-3.5 w-3.5" />
              Approve
            </button>
          </>
        ) : null}

        {["approved", "suspended"].includes(status) ? (
          <button
            type="button"
            disabled={busy}
            onClick={() => void runGated("ASSIGNMENT_ACTIVATE", activate)}
            data-testid="assignment-activate"
            className={`${button} border-emerald-300 text-emerald-700 hover:bg-emerald-50`}
          >
            {busy && activate.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <PlayCircle className="h-3.5 w-3.5" />}
            Activate
          </button>
        ) : null}

        {status === "active" ? (
          <button
            type="button"
            disabled={busy}
            onClick={() => void runPlain(suspend, "Suspend")}
            data-testid="assignment-suspend"
            className={`${button} border-amber-300 text-warning-foreground hover:bg-amber-50`}
          >
            <OctagonPause className="h-3.5 w-3.5" />
            Suspend
          </button>
        ) : null}

        {["active", "suspended"].includes(status) ? (
          <button
            type="button"
            disabled={busy}
            onClick={() => void runPlain(end, "End")}
            data-testid="assignment-end"
            className={`${button} border-rose-300 text-rose-700 hover:bg-rose-50`}
          >
            <Square className="h-3.5 w-3.5" />
            End assignment
          </button>
        ) : null}
      </div>

      {assignment.eligibilityStatus && !verdict ? (
        <p className="mt-1.5 text-[11px] text-muted-foreground">
          Last eligibility: <span className="font-medium">{assignment.eligibilityStatus}</span>
        </p>
      ) : null}
      {actionError ? (
        <p className="mt-2 rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger" data-testid="assignment-action-error">
          {actionError}
        </p>
      ) : null}
      {verdict ? <EligibilityVerdict verdict={verdict} /> : null}
    </div>
  );
}
