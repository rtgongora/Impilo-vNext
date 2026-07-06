"use client";

/**
 * Two-person approval panel for a PLATFORM_ACTION access request.
 *
 * Presentational: renders the 1-of-2 → 2-of-2 approval progress, the who-approved
 * rows (A-b), an APPROVE control that enforces approver ≠ initiator in the UX, and
 * an Execute control that stays disabled until the request is APPROVED. All data
 * and callbacks are supplied by the detail page (usePlatformOrigin hooks).
 */

import { useState } from "react";
import { CheckCircle2, Circle, ShieldCheck } from "lucide-react";
import type { PlatformActionApprovalRow } from "@/hooks/queries/usePlatformOrigin";

export interface TwoPersonApprovalPanelProps {
  accessRequestId: string;
  status: string;
  /** Who initiated the action — the two-person rule forbids them approving. */
  initiatorId?: string;
  approvalsRequired: number;
  approvalsRecorded: number;
  approvalsSatisfied: boolean;
  approvals: PlatformActionApprovalRow[];
  approving?: boolean;
  executing?: boolean;
  approveError?: string | null;
  executeError?: string | null;
  onApprove: (vars: { approverUserId: string; note?: string }) => void;
  onExecute: () => void;
}

export function TwoPersonApprovalPanel(props: TwoPersonApprovalPanelProps) {
  const {
    status,
    initiatorId,
    approvalsRequired,
    approvalsRecorded,
    approvalsSatisfied,
    approvals,
    approving,
    executing,
    approveError,
    executeError,
    onApprove,
    onExecute,
  } = props;

  const [approverUserId, setApproverUserId] = useState("");
  const [note, setNote] = useState("");

  const isExecuted = status?.toUpperCase() === "EXECUTED";
  const isApproved = status?.toUpperCase() === "APPROVED";
  const canExecute = isApproved && !isExecuted;

  const approverIsInitiator =
    approverUserId.trim().length > 0 && approverUserId.trim() === (initiatorId ?? "").trim();
  const alreadyApproved = approvals.some((a) => a.approverUserId === approverUserId.trim());
  const canSubmitApprove =
    approverUserId.trim().length > 0 && !approverIsInitiator && !alreadyApproved && !isApproved && !isExecuted;

  const required = Math.max(approvalsRequired, 0);
  const pips = Array.from({ length: required }, (_, i) => i < approvalsRecorded);

  return (
    <section className="rounded-xl border border-border bg-card p-5" aria-label="Two-person approval">
      <div className="flex items-center gap-2">
        <ShieldCheck className="h-5 w-5 text-primary-hover" aria-hidden />
        <h3 className="text-sm font-semibold text-foreground">Two-person approval</h3>
      </div>

      <div className="mt-3 flex items-center gap-3" data-testid="approval-progress">
        <div className="flex items-center gap-1">
          {pips.map((filled, i) =>
            filled ? (
              <CheckCircle2 key={i} className="h-5 w-5 text-emerald-600" aria-hidden />
            ) : (
              <Circle key={i} className="h-5 w-5 text-muted-foreground" aria-hidden />
            ),
          )}
        </div>
        <span className="text-sm font-medium text-foreground">
          {approvalsRecorded}-of-{required || 2} approvals
        </span>
        <span
          className={`rounded-full px-2 py-0.5 text-xs font-medium ${
            approvalsSatisfied
              ? "bg-emerald-100 text-emerald-800"
              : "bg-amber-100 text-amber-800"
          }`}
        >
          {approvalsSatisfied ? "Satisfied" : "Awaiting approvals"}
        </span>
        <span className="ml-auto text-xs text-muted-foreground">Status: {status ?? "—"}</span>
      </div>

      {/* Who-approved rows (A-b) */}
      <ul className="mt-4 divide-y divide-slate-100 rounded-lg border border-border">
        {approvals.length === 0 ? (
          <li className="px-3 py-2 text-xs text-muted-foreground">No approvals recorded yet.</li>
        ) : (
          approvals.map((a, i) => (
            <li key={`${a.approverUserId}-${i}`} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
              <span className="font-medium text-foreground">{a.approverUserId ?? "—"}</span>
              <span className="text-xs text-muted-foreground">{a.approverRole ?? "—"}</span>
              <span
                className={`rounded px-1.5 py-0.5 text-xs font-medium ${
                  a.decision?.toUpperCase() === "APPROVE"
                    ? "bg-emerald-100 text-emerald-800"
                    : "bg-red-100 text-red-800"
                }`}
              >
                {a.decision ?? "—"}
              </span>
            </li>
          ))
        )}
      </ul>

      {/* Approve control */}
      {!isApproved && !isExecuted && (
        <div className="mt-4 space-y-2">
          <label className="block text-xs font-medium text-muted-foreground" htmlFor="approver-id">
            Approver user id (must differ from the initiator)
          </label>
          <input
            id="approver-id"
            value={approverUserId}
            onChange={(e) => setApproverUserId(e.target.value)}
            placeholder="approver user id"
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
          />
          <input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Decision note (optional)"
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
          />
          {approverIsInitiator && (
            <p className="text-xs text-red-600">
              The initiator of a platform action cannot approve it (two-person rule).
            </p>
          )}
          {alreadyApproved && !approverIsInitiator && (
            <p className="text-xs text-amber-700">This approver has already recorded a decision.</p>
          )}
          {approveError && <p className="text-xs text-red-600">{approveError}</p>}
          <button
            type="button"
            disabled={!canSubmitApprove || approving}
            onClick={() => onApprove({ approverUserId: approverUserId.trim(), note: note.trim() || undefined })}
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {approving ? "Recording…" : "Record approval"}
          </button>
        </div>
      )}

      {/* Execute control — disabled until APPROVED */}
      <div className="mt-4 border-t border-border pt-4">
        {executeError && <p className="mb-2 text-xs text-red-600">{executeError}</p>}
        <button
          type="button"
          disabled={!canExecute || executing}
          onClick={onExecute}
          className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isExecuted ? "Executed" : executing ? "Executing…" : "Execute platform action"}
        </button>
        {!canExecute && !isExecuted && (
          <p className="mt-1 text-xs text-muted-foreground">
            Execution unlocks once two distinct approvals move this action to APPROVED.
          </p>
        )}
      </div>
    </section>
  );
}
