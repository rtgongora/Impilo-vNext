"use client";

/**
 * Fundo → Varapi CPD candidate list with optional council decision actions.
 *
 * Read-only mode (provider self-service, learner surfaces) shows the governed
 * verification state honestly; decision mode (council staff workspace) wires the
 * existing accept/reject BFF endpoints. Varapi remains the CPD ledger authority —
 * accepting here records a governed CPD event in Varapi, nothing is awarded in Fundo.
 */

import { useState } from "react";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import {
  useAcceptFundoCpd,
  useFundoCpdCandidates,
  useFundoCpdCandidatesByPublicId,
  useRejectFundoCpd,
} from "@/hooks/queries/useProviderCouncil";

export interface FundoCpdCandidateRow {
  id?: number | string;
  courseId?: string;
  courseName?: string;
  completedAt?: string;
  creditsSuggested?: number;
  verificationState?: string;
  externalRef?: string;
  linkedCpdEventId?: number | string | null;
  [key: string]: unknown;
}

function stateBadge(state: string) {
  const s = state.toUpperCase();
  if (s === "ACCEPTED") return "bg-emerald-100 text-emerald-800 border-emerald-200";
  if (s === "REJECTED") return "bg-red-100 text-red-800 border-red-200";
  return "bg-amber-100 text-amber-800 border-amber-200";
}

export function FundoCpdCandidatePanel({
  providerId,
  providerPublicId,
  canDecide = false,
}: {
  /** Varapi numeric provider id — preferred when accepting/rejecting. */
  providerId?: string | undefined;
  /** Fundo/learning public identifier — used when the numeric id is not known. */
  providerPublicId?: string | undefined;
  canDecide?: boolean;
}) {
  const byNumeric = useFundoCpdCandidates(providerId);
  const byPublic = useFundoCpdCandidatesByPublicId(
    providerId ? undefined : providerPublicId,
  );
  const data = providerId ? byNumeric.data : byPublic.data;
  const isLoading = providerId ? byNumeric.isLoading : byPublic.isLoading;
  const accept = useAcceptFundoCpd(providerId);
  const reject = useRejectFundoCpd(providerId);
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  const rows = (Array.isArray(data) ? data : []) as FundoCpdCandidateRow[];
  const busy = accept.isPending || reject.isPending;

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading CPD candidates…
      </div>
    );
  }
  if (rows.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No Fundo CPD candidates for this provider. Candidates appear automatically when a
        CPD-eligible Fundo certificate is issued.
      </p>
    );
  }

  const onAccept = (candidateId: number) => {
    setActionError(null);
    accept.mutate(candidateId, {
      onError: (e) => setActionError(e instanceof Error ? e.message : "Accept failed"),
    });
  };

  const onConfirmReject = (candidateId: number) => {
    setActionError(null);
    reject.mutate(
      { candidateId, reason: rejectReason },
      {
        onSuccess: () => {
          setRejectingId(null);
          setRejectReason("");
        },
        onError: (e) => setActionError(e instanceof Error ? e.message : "Reject failed"),
      },
    );
  };

  return (
    <div data-testid="fundo-cpd-candidate-panel">
      {actionError ? (
        <p className="mb-2 rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger">
          {actionError}
        </p>
      ) : null}
      <ul className="divide-y divide-gray-100">
        {rows.map((row) => {
          const candidateId = Number(row.id);
          const state = String(row.verificationState ?? "PENDING");
          const pending = state.toUpperCase() === "PENDING";
          return (
            <li key={String(row.id)} className="py-3" data-testid={`fundo-cpd-candidate-${row.id}`}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-foreground">
                    {String(row.courseName ?? row.courseId ?? "Course completion")}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    Completed: {row.completedAt ? String(row.completedAt) : "—"} · Suggested credits:{" "}
                    {String(row.creditsSuggested ?? 0)}
                    {row.externalRef ? ` · Certificate: ${String(row.externalRef)}` : ""}
                    {row.linkedCpdEventId != null
                      ? ` · CPD ledger event #${String(row.linkedCpdEventId)}`
                      : ""}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span
                    className={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${stateBadge(state)}`}
                    data-testid={`fundo-cpd-state-${row.id}`}
                  >
                    {state}
                  </span>
                  {canDecide && pending && Number.isFinite(candidateId) ? (
                    <>
                      <button
                        type="button"
                        disabled={busy}
                        data-testid={`fundo-cpd-accept-${row.id}`}
                        onClick={() => onAccept(candidateId)}
                        className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                      >
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        Accept into CPD ledger
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        data-testid={`fundo-cpd-reject-${row.id}`}
                        onClick={() => {
                          setRejectingId(rejectingId === candidateId ? null : candidateId);
                          setRejectReason("");
                        }}
                        className="inline-flex items-center gap-1 rounded-lg border border-danger/28 px-2.5 py-1 text-xs font-medium text-danger hover:bg-danger-soft disabled:opacity-50"
                      >
                        <XCircle className="h-3.5 w-3.5" />
                        Reject
                      </button>
                    </>
                  ) : null}
                </div>
              </div>
              {canDecide && rejectingId === candidateId ? (
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <input
                    type="text"
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    placeholder="Rejection reason (recorded in the audit trail)"
                    data-testid={`fundo-cpd-reject-reason-${row.id}`}
                    className="w-72 rounded border border-border px-2 py-1 text-xs"
                  />
                  <button
                    type="button"
                    disabled={busy}
                    data-testid={`fundo-cpd-reject-confirm-${row.id}`}
                    onClick={() => onConfirmReject(candidateId)}
                    className="rounded-lg bg-red-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-red-700 disabled:opacity-50"
                  >
                    Confirm rejection
                  </button>
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
