"use client";

import { useState } from "react";
import { usePrecheckAssignment } from "@/hooks/useVashandi";
import type { VashandiActionResponse, WorkforceEligibilityResult } from "@/lib/vashandi/types";
import { VashandiFriendlyBlockedState } from "./VashandiFriendlyBlockedState";

interface EligibilityCheckPanelProps {
  assignmentId: string;
}

export function EligibilityCheckPanel({ assignmentId }: EligibilityCheckPanelProps) {
  const mutation = usePrecheckAssignment();
  const [result, setResult] = useState<VashandiActionResponse<WorkforceEligibilityResult> | null>(null);

  async function runPrecheck() {
    const response = await mutation.mutateAsync({ assignmentId });
    setResult(response);
  }

  if (result && !result.success) {
    return (
      <VashandiFriendlyBlockedState
        state={result.blockedReason}
        title={result.friendlyTitle ?? "Eligibility blocked"}
        description={result.friendlyMessage ?? result.integrationMessage}
      />
    );
  }

  const eligibility = result?.data;

  return (
    <div className="rounded-2xl border border-border bg-card p-5 space-y-3">
      <h3 className="font-medium text-foreground">Eligibility precheck</h3>
      <p className="text-sm text-muted-foreground">
        Runs OPA and upstream checks before assignment activation.
      </p>
      <button
        type="button"
        onClick={runPrecheck}
        disabled={mutation.isPending}
        className="rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-muted disabled:opacity-50"
      >
        {mutation.isPending ? "Checking…" : "Run precheck"}
      </button>
      {eligibility ? (
        <div className="rounded-lg border border-border bg-muted/40 p-3 text-sm">
          <p>
            <span className="font-medium">Overall:</span> {eligibility.overallStatus ?? "unknown"}
          </p>
          {eligibility.message ? <p className="mt-1 text-muted-foreground">{eligibility.message}</p> : null}
          {eligibility.checks?.length ? (
            <ul className="mt-2 space-y-1 text-muted-foreground">
              {eligibility.checks.map((check, index) => {
                const c = check as Record<string, unknown>;
                const dependency = String(c.dependency ?? `check-${index}`);
                const status = String(c.status ?? "unknown");
                const payload =
                  c.payload && typeof c.payload === "object"
                    ? (c.payload as Record<string, unknown>)
                    : undefined;
                const gateDecision =
                  dependency === "fundo-training-gate" && payload
                    ? String((payload as Record<string, unknown>).decision ?? "")
                    : "";
                return (
                  <li key={index} className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-foreground">{dependency}</span>
                    <span
                      className={
                        status === "LIVE"
                          ? "rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] text-emerald-800"
                          : "rounded-full bg-amber-100 px-2 py-0.5 text-[11px] text-amber-800"
                      }
                    >
                      {status}
                    </span>
                    {gateDecision ? (
                      <span
                        data-testid="training-gate-decision"
                        className={
                          gateDecision === "ALLOW"
                            ? "rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-medium text-emerald-800"
                            : gateDecision === "ADVISE"
                              ? "rounded-full bg-sky-100 px-2 py-0.5 text-[11px] font-medium text-sky-800"
                              : "rounded-full bg-red-100 px-2 py-0.5 text-[11px] font-medium text-red-800"
                        }
                      >
                        Training gate: {gateDecision}
                      </span>
                    ) : null}
                    {typeof c.message === "string" && c.message !== "ok" ? (
                      <span className="text-xs">{c.message}</span>
                    ) : null}
                  </li>
                );
              })}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
