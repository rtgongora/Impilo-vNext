"use client";

import { useState } from "react";
import { Loader2, GitBranch, AlertTriangle } from "lucide-react";
import {
  useLifecycleTransitions,
  useTransitionLifecycle,
  TERMINAL_LIFECYCLE_STATES,
} from "@/hooks/queries/useProviderLifecycle";

function label(state: string): string {
  return state.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

function transitionError(error: unknown): string {
  if (error && typeof error === "object") {
    const err = (error as { error?: { message?: string } }).error;
    if (err?.message && typeof err.message === "string") return err.message;
  }
  return "Transition failed.";
}

/**
 * Operator lifecycle console — current 18-state lifecycle status + allowed-next transitions
 * (suspend/restrict/lapse/restore/retire/deceased/remove). Terminal transitions require a reason;
 * DECEASED requires a source reference. Every transition is audited server-side (R2/G10).
 */
export function ProviderLifecyclePanel({ providerPublicId }: { providerPublicId: string }) {
  const { data, isPending } = useLifecycleTransitions(providerPublicId);
  const transition = useTransitionLifecycle(providerPublicId);

  const [target, setTarget] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [effectiveDate, setEffectiveDate] = useState("");
  const [sourceRef, setSourceRef] = useState("");
  const [warnings, setWarnings] = useState<string[]>([]);

  const isTerminal = target ? TERMINAL_LIFECYCLE_STATES.has(target) : false;
  const needsSource = target === "DECEASED";
  const canSubmit =
    !!target && (!isTerminal || reason.trim().length > 0) && (!needsSource || sourceRef.trim().length > 0);

  function reset() {
    setTarget(null);
    setReason("");
    setEffectiveDate("");
    setSourceRef("");
  }

  function submit() {
    if (!target || !canSubmit) return;
    setWarnings([]);
    transition.mutate(
      {
        targetState: target,
        reason: reason.trim() || undefined,
        effectiveDate: effectiveDate || undefined,
        sourceRef: sourceRef.trim() || undefined,
      },
      {
        onSuccess: (res) => {
          const w = (res as { data?: { warnings?: string[] } })?.data?.warnings ?? [];
          setWarnings(Array.isArray(w) ? w : []);
          reset();
        },
      },
    );
  }

  return (
    <div className="bg-card rounded-xl border border-border shadow-sm">
      <div className="flex items-center gap-2 px-4 py-3 border-b border-border">
        <GitBranch className="h-4 w-4 text-primary" />
        <h3 className="text-sm font-semibold text-foreground">Lifecycle</h3>
      </div>
      <div className="p-4">
        {isPending ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
        ) : (
          <>
            <p className="text-sm text-muted-foreground">
              Current state: <span className="font-medium text-foreground">{label(data?.current ?? "—")}</span>
            </p>

            {(data?.allowed?.length ?? 0) === 0 ? (
              <p className="mt-2 text-xs text-muted-foreground">
                No operator-invocable transitions from this state (it is workflow-owned or terminal).
              </p>
            ) : (
              <div className="mt-3 flex flex-wrap gap-2">
                {data!.allowed.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => { setTarget(s); setReason(""); setSourceRef(""); }}
                    className={`rounded-lg border px-3 py-1.5 text-xs font-medium ${
                      target === s
                        ? "border-primary bg-primary-soft text-primary-hover"
                        : TERMINAL_LIFECYCLE_STATES.has(s)
                          ? "border-red-300 text-red-700 hover:bg-red-50"
                          : "border-border text-foreground hover:bg-background"
                    }`}
                  >
                    {label(s)}
                  </button>
                ))}
              </div>
            )}

            {target && (
              <div className="mt-3 space-y-2 rounded-lg border border-border bg-background p-3">
                <p className="text-xs font-medium text-foreground">Transition to {label(target)}</p>
                {(isTerminal || true) && (
                  <textarea
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    rows={2}
                    placeholder={isTerminal ? "Reason (required)" : "Reason (optional)"}
                    className="w-full rounded border border-border px-2 py-1.5 text-sm"
                  />
                )}
                <div className="grid gap-2 sm:grid-cols-2">
                  <label className="text-xs text-muted-foreground">Effective date
                    <input type="date" value={effectiveDate} onChange={(e) => setEffectiveDate(e.target.value)}
                      className="mt-0.5 w-full rounded border border-border px-2 py-1.5 text-sm" />
                  </label>
                  {needsSource && (
                    <label className="text-xs text-muted-foreground">Source reference (required)
                      <input value={sourceRef} onChange={(e) => setSourceRef(e.target.value)}
                        placeholder="e.g. death notification ref"
                        className="mt-0.5 w-full rounded border border-border px-2 py-1.5 text-sm" />
                    </label>
                  )}
                </div>
                {transition.isError && (
                  <p className="text-xs text-red-600">{transitionError(transition.error)}</p>
                )}
                <div className="flex justify-end gap-2">
                  <button type="button" onClick={reset} className="rounded px-3 py-1.5 text-xs text-muted-foreground">Cancel</button>
                  <button type="button" onClick={submit} disabled={!canSubmit || transition.isPending}
                    className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50">
                    {transition.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null} Confirm
                  </button>
                </div>
              </div>
            )}

            {warnings.length > 0 && (
              <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/40 bg-warning-soft p-2 text-xs text-warning-foreground">
                <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                <ul className="space-y-0.5">{warnings.map((w, i) => <li key={i}>{w}</li>)}</ul>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
