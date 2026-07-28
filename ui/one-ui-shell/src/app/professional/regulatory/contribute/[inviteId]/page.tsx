"use client";

/**
 * The training institution's bounded contribution (NCZ-W1C).
 *
 * Route: /professional/regulatory/contribute/[inviteId]
 *
 * Addressed by INVITATION, never by application. The institution confirms an enrolment; it has no
 * business reading the applicant's identity documents or the Council's assessment, and this page
 * has no way to ask for them — the boundary is the route, not a filter on the response.
 */

import { useParams } from "next/navigation";
import { Check, Lock, ShieldCheck } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCompleteContribution,
  useContributionSections,
} from "@/hooks/queries/useStudentRegistration";

export default function ContributorPage() {
  const params = useParams<{ inviteId: string }>();
  const inviteId = decodeURIComponent(String(params?.inviteId ?? ""));
  const { data: sections, isLoading, isError, error } = useContributionSections(inviteId);
  const complete = useCompleteContribution(inviteId);

  return (
    <AppLayout>
      <PageShell
        title="Confirm a student's enrolment"
        subtitle="A request from the Nurses Council of Zimbabwe"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          <div className="flex items-start gap-2 rounded-xl border border-border bg-muted/30 p-4 text-sm">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
            <div>
              <p className="font-medium text-foreground">You are being asked about one thing only</p>
              <p className="mt-0.5 text-muted-foreground">
                This request covers the parts of the application listed below. The rest of the
                student&rsquo;s application — their identity documents, their correspondence with the
                Council, and the Council&rsquo;s own assessment — is not shared with you, and cannot
                be reached from here.
              </p>
            </div>
          </div>

          {isLoading ? <p className="text-sm text-muted-foreground">Loading the request…</p> : null}
          {isError ? (
            <p className="rounded-xl border border-border bg-muted/30 p-4 text-sm text-muted-foreground">
              {String((error as Error)?.message ?? "")
                .toLowerCase()
                .includes("expired")
                ? "This request has expired. Ask the Council to send it again."
                : "This request is no longer available. It may have been completed or withdrawn."}
            </p>
          ) : null}

          {sections?.length ? (
            <ul className="space-y-3">
              {sections.map((section) => (
                <li key={section.sectionKey} className="rounded-xl border border-border bg-card p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-medium text-foreground">{section.label}</p>
                      {section.state === "COMPLETE" ? (
                        <p className="mt-0.5 flex items-center gap-1.5 text-xs text-emerald-600">
                          <Check className="h-3.5 w-3.5" aria-hidden /> Confirmed — thank you
                        </p>
                      ) : (
                        <p className="mt-0.5 text-xs text-muted-foreground">Awaiting your confirmation</p>
                      )}
                    </div>
                    <Lock className="h-3.5 w-3.5 shrink-0 text-muted-foreground/60" aria-hidden />
                  </div>

                  {section.state !== "COMPLETE" ? (
                    <button
                      type="button"
                      onClick={() =>
                        complete.mutate({ sectionKey: section.sectionKey, content: { confirmed: true } })
                      }
                      disabled={complete.isPending}
                      className="mt-3 rounded-lg border border-teal-500/50 px-3 py-1.5 text-sm font-medium text-teal-700 hover:bg-teal-500/10 disabled:opacity-50 dark:text-teal-300"
                    >
                      {complete.isPending ? "Confirming…" : "Confirm this"}
                    </button>
                  ) : null}
                </li>
              ))}
            </ul>
          ) : null}

          {sections && sections.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              There is nothing outstanding on this request.
            </p>
          ) : null}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
