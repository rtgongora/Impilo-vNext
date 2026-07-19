"use client";

/**
 * Report a lost or stolen card (CJ8). The citizen never supplies a card id — the BFF
 * (/internal/v1/wallet/cards/report-lost) resolves their own active SMART card from the
 * authenticated actor, revokes it so it can no longer be used, and optionally requests a
 * replacement. Only LOST / STOLEN are citizen-reportable.
 */

import { useState } from "react";
import { ShieldAlert, CheckCircle2, AlertCircle } from "lucide-react";
import { useReportLostCard } from "@/hooks/queries/useVitoCards";
import { StepUpPrompt } from "@/components/citizen/StepUpPrompt";
import { readStepUp } from "@/lib/stepUp";

export function ReportLostCard() {
  const report = useReportLostCard();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<"LOST" | "STOLEN">("LOST");
  const [replace, setReplace] = useState(true);
  const [stepUpMethods, setStepUpMethods] = useState<string[] | null>(null);

  const result = report.data?.data;

  async function submit() {
    try {
      await report.mutateAsync({ reason, requestReplacement: replace });
      setStepUpMethods(null);
    } catch (err) {
      const stepUp = readStepUp(err);
      if (stepUp.required) {
        setStepUpMethods(stepUp.methods);
      }
    }
  }

  // Success / outcome view.
  if (result) {
    const reported = result.status === "REPORTED";
    return (
      <section className="rounded-lg border border-border bg-card p-4">
        <div className="flex items-start gap-3">
          <CheckCircle2 className={`mt-0.5 h-5 w-5 shrink-0 ${reported ? "text-emerald-600" : "text-amber-600"}`} />
          <div>
            <h2 className="text-sm font-medium text-foreground">
              {reported ? "Your card has been blocked" : "No active card found"}
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {result.message ||
                (reported
                  ? "Your card can no longer be used."
                  : "There was no active card on your record to report.")}
            </p>
            {result.replacementRequested && (
              <p className="mt-1 text-sm text-muted-foreground">
                A replacement card has been requested — you&apos;ll be notified when it&apos;s ready to collect.
              </p>
            )}
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-border bg-card p-4">
      {stepUpMethods && (
        <StepUpPrompt
          methods={stepUpMethods}
          onCompleted={() => {
            setStepUpMethods(null);
            void submit();
          }}
          onCancel={() => setStepUpMethods(null)}
        />
      )}

      <div className="flex items-start gap-3">
        <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-danger" />
        <div className="flex-1">
          <h2 className="text-sm font-medium text-foreground">Lost or stolen card?</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Report it to block it immediately so no one else can use it.
          </p>

          {!open ? (
            <button
              type="button"
              onClick={() => setOpen(true)}
              className="mt-3 text-sm font-medium text-danger underline hover:no-underline"
            >
              Report a lost or stolen card
            </button>
          ) : (
            <div className="mt-3 space-y-3">
              <fieldset className="space-y-2">
                <legend className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  What happened?
                </legend>
                {(["LOST", "STOLEN"] as const).map((r) => (
                  <label key={r} className="flex items-center gap-2 text-sm text-foreground">
                    <input
                      type="radio"
                      name="report-reason"
                      checked={reason === r}
                      onChange={() => setReason(r)}
                    />
                    {r === "LOST" ? "I lost my card" : "My card was stolen"}
                  </label>
                ))}
              </fieldset>

              <label className="flex items-center gap-2 text-sm text-foreground">
                <input type="checkbox" checked={replace} onChange={(e) => setReplace(e.target.checked)} />
                Request a replacement card
              </label>

              {report.isError && (
                <p className="flex items-center gap-1 text-sm text-danger">
                  <AlertCircle className="h-4 w-4" /> Something went wrong. Please try again.
                </p>
              )}

              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => void submit()}
                  disabled={report.isPending}
                  className="rounded-md bg-danger px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                >
                  {report.isPending ? "Blocking…" : "Block my card"}
                </button>
                <button
                  type="button"
                  onClick={() => setOpen(false)}
                  disabled={report.isPending}
                  className="rounded-md border border-border px-3 py-1.5 text-sm text-foreground"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
