"use client";

/**
 * Post-consult summary — plain-language wrap-up after the visit. Reads the
 * clinician's response fields directly off the teleconsult session payload
 * (responseNote / actionPlan / followUp / redFlags, written at Stage 6).
 *
 * SEAM: the teleconsult session payload does not expose billing/payment
 * fields today, so payment status is intentionally omitted. When COSTA
 * billing context lands on the session detail, add a payment card here.
 */

import { CalendarClock, ClipboardList, FileText, HeartPulse } from "lucide-react";

function text(session: Record<string, unknown> | null, key: string): string {
  const value = session?.[key];
  return typeof value === "string" && value.trim().length > 0 ? value : "";
}

export function PostConsultSummary({ session }: { session: Record<string, unknown> | null }) {
  const responseNote = text(session, "responseNote");
  const actionPlan = text(session, "actionPlan");
  const followUp = text(session, "followUp");
  const redFlags = text(session, "redFlags");
  const hasInstructions = Boolean(responseNote || actionPlan || followUp || redFlags);

  return (
    <div className="max-w-2xl mx-auto space-y-4" data-testid="post-consult-summary">
      <div className="bg-card rounded-xl border border-border p-5 space-y-2">
        <h2 className="text-base font-semibold text-foreground flex items-center gap-2">
          <FileText className="w-4 h-4 text-primary" /> Your visit is complete
        </h2>
        <p className="text-sm text-muted-foreground">
          Thank you for joining. Here is what your care team wants you to know.
        </p>
      </div>

      {hasInstructions ? (
        <>
          {responseNote && (
            <div className="bg-card rounded-xl border border-border p-5 space-y-1.5">
              <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <ClipboardList className="w-4 h-4 text-muted-foreground" /> What the clinician said
              </h3>
              <p className="text-sm text-muted-foreground whitespace-pre-line">{responseNote}</p>
            </div>
          )}
          {actionPlan && (
            <div className="bg-card rounded-xl border border-border p-5 space-y-1.5">
              <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <ClipboardList className="w-4 h-4 text-muted-foreground" /> Your plan
              </h3>
              <p className="text-sm text-muted-foreground whitespace-pre-line">{actionPlan}</p>
            </div>
          )}
          {redFlags && (
            <div className="bg-card rounded-xl border border-amber-300 p-5 space-y-1.5">
              <h3 className="text-sm font-semibold text-warning-foreground flex items-center gap-2">
                <HeartPulse className="w-4 h-4" /> Get help sooner if…
              </h3>
              <p className="text-sm text-muted-foreground whitespace-pre-line">{redFlags}</p>
            </div>
          )}
          {followUp && (
            <div className="bg-card rounded-xl border border-border p-5 space-y-1.5">
              <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <CalendarClock className="w-4 h-4 text-muted-foreground" /> Follow-up
              </h3>
              <p className="text-sm text-muted-foreground whitespace-pre-line">{followUp}</p>
            </div>
          )}
        </>
      ) : (
        <div className="bg-card rounded-xl border border-border p-5">
          <p className="text-sm text-muted-foreground" data-testid="post-consult-placeholder">
            Your care team is still writing up your visit notes. Instructions and any
            follow-up plans will appear here once they are ready — you can come back to
            this page any time.
          </p>
        </div>
      )}
    </div>
  );
}
