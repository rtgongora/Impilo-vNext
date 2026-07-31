"use client";

/**
 * The regulator reviewing a student registration (NCZ-W1C).
 *
 * Route: /work/regulatory/[orgId]/student-applications/[applicationId]
 *
 * Two things this screen is built to make ordinary. Returning ONE section rather than rejecting a
 * person — the reason is mandatory here as well as at the database, because "returned" with no
 * reason leaves an applicant staring at a form. And seeing the fee verdict BEFORE deciding, so a
 * fee the Council has not set reads as "awaiting NCZ-DEC-002" rather than surfacing as a failure
 * at the moment of admission.
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { AlertTriangle, Loader2, Undo2 } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useApplicationSections,
  useFeeVerdict,
  useReturnSection,
} from "@/hooks/queries/useStudentRegistration";
import { useRequireRegulatorySession } from "@/hooks/useRequireRegulatorySession";

export default function RegulatorStudentApplicationPage() {
  const params = useParams<{ orgId: string; applicationId: string }>();
  const orgId = decodeURIComponent(String(params?.orgId ?? ""));
  const applicationId = decodeURIComponent(String(params?.applicationId ?? ""));
  const sessionOk = useRequireRegulatorySession(orgId);

  const { data: sections, isLoading } = useApplicationSections(applicationId);
  const { data: fee } = useFeeVerdict(orgId, "STUDENT_INDEX");
  const returnSection = useReturnSection(applicationId);
  const [reasons, setReasons] = useState<Record<string, string>>({});

  const outstanding = (sections ?? []).filter((s) => s.state !== "COMPLETE");

  if (!sessionOk) {
    return (
      <AppLayout>
        <PageShell title="Student application" serviceSlug="varapi">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Checking regulatory session…
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Student registration review"
        subtitle={`Application ${applicationId}`}
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          {fee && !fee.chargeable ? (
            <div className="rounded-xl border border-amber-400/50 bg-amber-500/10 p-4 text-sm">
              <p className="flex items-center gap-2 font-medium text-amber-800 dark:text-amber-200">
                <AlertTriangle className="h-4 w-4" aria-hidden />
                The student index fee is not set
              </p>
              <p className="mt-1 text-amber-700/90 dark:text-amber-200/80">{fee.explanation}</p>
              <p className="mt-1 text-amber-700/70 dark:text-amber-200/60">
                You can review this application, but it cannot be admitted until the Council sets
                the fee. Nothing is charged and nothing is waived in the meantime.
              </p>
            </div>
          ) : null}

          {isLoading ? <p className="text-sm text-muted-foreground">Loading the application…</p> : null}

          {outstanding.length === 0 && sections?.length ? (
            <p className="text-sm text-emerald-700 dark:text-emerald-300">
              Every section is complete.
            </p>
          ) : null}

          {sections?.length ? (
            <ul className="space-y-3">
              {sections.map((section) => (
                <li key={section.sectionKey} className="rounded-xl border border-border bg-card p-4">
                  <div className="flex items-baseline justify-between gap-3">
                    <p className="font-medium text-foreground">{section.label}</p>
                    <span className="text-xs uppercase tracking-wide text-muted-foreground">
                      {section.state.toLowerCase().replace(/_/g, " ")}
                    </span>
                  </div>

                  {section.state === "RETURNED" ? (
                    <p className="mt-1 text-xs text-amber-700 dark:text-amber-300">
                      Returned: {section.returnedReason}
                    </p>
                  ) : null}

                  {section.state === "COMPLETE" ? (
                    <div className="mt-3 space-y-2">
                      <label
                        htmlFor={`reason-${section.sectionKey}`}
                        className="block text-xs text-muted-foreground"
                      >
                        Send this part back for correction — say what needs to change
                      </label>
                      <div className="flex gap-2">
                        <input
                          id={`reason-${section.sectionKey}`}
                          value={reasons[section.sectionKey] ?? ""}
                          onChange={(e) =>
                            setReasons((r) => ({ ...r, [section.sectionKey]: e.target.value }))
                          }
                          placeholder="e.g. The certified copy is unreadable"
                          className="flex-1 rounded-lg border border-border bg-background px-3 py-1.5 text-sm"
                        />
                        <button
                          type="button"
                          disabled={
                            !reasons[section.sectionKey]?.trim() || returnSection.isPending
                          }
                          onClick={() =>
                            returnSection.mutate({
                              sectionKey: section.sectionKey,
                              reason: reasons[section.sectionKey],
                            })
                          }
                          className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-muted disabled:opacity-40"
                        >
                          <Undo2 className="h-3.5 w-3.5" aria-hidden /> Return
                        </button>
                      </div>
                      <p className="text-xs text-muted-foreground">
                        Returning one section does not reject the application or move it out of the
                        queue.
                      </p>
                    </div>
                  ) : null}
                </li>
              ))}
            </ul>
          ) : null}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
