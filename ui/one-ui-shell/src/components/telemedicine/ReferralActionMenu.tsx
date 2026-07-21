"use client";

/**
 * ReferralActionMenu — reason-bound lifecycle controls for a teleconsult referral.
 *
 * The guard (via GET allowed-actions) is the single source of truth for which transitions
 * are permitted from the referral's current state. Only lifecycle actions whose target state
 * is in `allowedTargets` render — no locally-guessed transitions, no dead buttons.
 *
 * Each action opens a small reason dialog; a reason is mandatory (submit stays disabled until
 * it is non-empty, and the BFF independently rejects a blank one). Loading, error and
 * "nothing permitted" states are surfaced honestly.
 */

import { useState } from "react";
import { AlertTriangle, Loader2 } from "lucide-react";
import {
  useReferralAllowedActions,
  useReferralLifecycleAction,
  type ReferralLifecycleActionName,
} from "@/hooks/queries/useTelemedicine";

interface LifecycleActionDef {
  action: ReferralLifecycleActionName;
  /** Target state that must be present in `allowedTargets` for this action to render. */
  target: string;
  label: string;
  /** Prompt shown in the reason dialog. */
  prompt: string;
  danger?: boolean;
}

const LIFECYCLE_ACTIONS: LifecycleActionDef[] = [
  { action: "escalate", target: "ESCALATED", label: "Escalate", prompt: "Reason for escalation" },
  { action: "transfer", target: "TRANSFERRED", label: "Transfer", prompt: "Reason for transfer" },
  { action: "reopen", target: "REOPENED", label: "Reopen", prompt: "Reason for reopening" },
  { action: "cancel", target: "CANCELLED", label: "Cancel", prompt: "Reason for cancellation", danger: true },
];

export function ReferralActionMenu({
  sessionId,
  status,
  onActionComplete,
}: {
  sessionId: string;
  status?: string;
  onActionComplete?: () => void;
}) {
  const allowed = useReferralAllowedActions(sessionId);
  const lifecycle = useReferralLifecycleAction(sessionId);
  const [openAction, setOpenAction] = useState<ReferralLifecycleActionName | null>(null);
  const [reason, setReason] = useState("");

  const targets = allowed.data?.data?.allowedTargets ?? [];
  const permitted = LIFECYCLE_ACTIONS.filter((a) => targets.includes(a.target));
  const active = permitted.find((a) => a.action === openAction) ?? null;

  function toggle(action: ReferralLifecycleActionName) {
    setOpenAction((current) => (current === action ? null : action));
    setReason("");
  }

  function submit() {
    const trimmed = reason.trim();
    if (!active || !trimmed) return;
    lifecycle.mutate(
      { action: active.action, reason: trimmed },
      {
        onSuccess: () => {
          setOpenAction(null);
          setReason("");
          onActionComplete?.();
        },
      },
    );
  }

  return (
    <div data-testid="referral-action-menu" className="space-y-2">
      <h4 className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        Lifecycle
      </h4>

      {status && (
        <p className="text-[11px] text-muted-foreground">Current state: {status}</p>
      )}

      {allowed.isLoading ? (
        <p className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" /> Checking available actions…
        </p>
      ) : allowed.isError ? (
        <div className="rounded-lg border border-rose-200 bg-rose-50 p-2 text-xs text-rose-700" data-testid="referral-action-error">
          Available actions could not be loaded.
          <button
            type="button"
            onClick={() => allowed.refetch()}
            className="ml-2 rounded-md border border-rose-300 px-2 py-0.5 text-[11px] font-medium hover:bg-rose-100"
          >
            Try again
          </button>
        </div>
      ) : permitted.length === 0 ? (
        <p className="text-xs text-muted-foreground" data-testid="referral-action-empty">
          No further actions available.
        </p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {permitted.map((a) => (
            <button
              key={a.action}
              type="button"
              data-testid={`referral-action-${a.action}`}
              onClick={() => toggle(a.action)}
              className={`rounded-lg border px-2.5 py-1 text-xs font-medium ${
                a.danger
                  ? "border-rose-300 text-rose-700 hover:bg-rose-50"
                  : "border-slate-300 text-slate-600 hover:bg-slate-50"
              } ${openAction === a.action ? "ring-1 ring-primary/40" : ""}`}
            >
              {a.label}
            </button>
          ))}
        </div>
      )}

      {active && (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-2.5" data-testid="referral-action-dialog">
          <label className="block text-xs font-medium text-slate-600">
            {active.prompt} (required)
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={2}
              placeholder="Reason (required)"
              className="mt-1 block w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm"
            />
          </label>
          {lifecycle.isError && (
            <p className="mt-1 inline-flex items-center gap-1 text-[11px] text-rose-700">
              <AlertTriangle className="h-3 w-3" /> The action could not be applied. Please retry.
            </p>
          )}
          <div className="mt-2 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => {
                setOpenAction(null);
                setReason("");
              }}
              className="rounded-lg px-2.5 py-1 text-xs text-slate-500 hover:bg-slate-100"
            >
              Dismiss
            </button>
            <button
              type="button"
              data-testid="referral-action-submit"
              onClick={submit}
              disabled={!reason.trim() || lifecycle.isPending}
              className={`inline-flex items-center gap-1 rounded-lg px-3 py-1 text-xs font-medium text-white disabled:opacity-40 ${
                active.danger ? "bg-rose-600 hover:bg-rose-700" : "bg-primary hover:bg-primary-hover"
              }`}
            >
              {lifecycle.isPending && <Loader2 className="h-3 w-3 animate-spin" />}
              Confirm {active.label.toLowerCase()}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
