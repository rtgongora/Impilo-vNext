"use client";

/**
 * The student's own view of their registration application (NCZ-W1C).
 *
 * Route: /professional/regulatory/apply/student/[applicationId]
 *
 * The screen this replaces does not exist: before W1C a regulator could only accept or reject a
 * whole application, so an applicant whose certified copy was unreadable started again. What this
 * page is FOR is the returned section — showing exactly what came back, why, and offering to fix
 * that alone while everything else they already completed stays completed.
 */

import { useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { AlertCircle, Check, Clock, RotateCcw } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useApplicationSections,
  useResubmitSection,
  type ApplicationSection,
} from "@/hooks/queries/useStudentRegistration";

function SectionRow({ section, applicationId }: { section: ApplicationSection; applicationId: string }) {
  const resubmit = useResubmitSection(applicationId);
  const returned = section.state === "RETURNED";
  const contentKeys = useMemo(() => {
    const content = section.content && typeof section.content === "object" ? section.content : {};
    const keys = Object.keys(content);
    return keys.length > 0 ? keys : ["notes"];
  }, [section.content]);

  const [draft, setDraft] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!returned) return;
    const content = section.content && typeof section.content === "object" ? section.content : {};
    const next: Record<string, string> = {};
    for (const key of contentKeys) {
      const value = content[key];
      next[key] = value == null ? "" : String(value);
    }
    setDraft(next);
  }, [returned, section.content, contentKeys]);

  const payload = useMemo(() => {
    const body: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(draft)) {
      if (value.trim()) body[key] = value.trim();
    }
    return body;
  }, [draft]);

  const canResubmit = Object.keys(payload).length > 0 && !resubmit.isPending;

  return (
    <li
      className={`rounded-xl border p-4 ${
        returned ? "border-amber-400/50 bg-amber-500/5" : "border-border bg-card"
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-medium text-foreground">{section.label}</p>
          {section.state === "COMPLETE" ? (
            <p className="mt-0.5 flex items-center gap-1.5 text-xs text-emerald-600">
              <Check className="h-3.5 w-3.5" aria-hidden /> Complete
            </p>
          ) : null}
          {section.state === "NOT_STARTED" || section.state === "IN_PROGRESS" ? (
            <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
              <Clock className="h-3.5 w-3.5" aria-hidden /> Not finished yet
            </p>
          ) : null}
        </div>
        {section.returnCount > 0 ? (
          <span className="rounded-full border border-border px-2 py-0.5 text-[10px] text-muted-foreground">
            returned {section.returnCount}×
          </span>
        ) : null}
      </div>

      {returned ? (
        <div className="mt-3 space-y-3">
          <p className="flex items-start gap-2 text-sm text-amber-700 dark:text-amber-300">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            <span>{section.returnedReason}</span>
          </p>
          <p className="text-xs text-muted-foreground">
            Only this part needs changing. Everything else you have completed stays as it is, and
            your application keeps its place — you are not starting again.
          </p>
          <div className="space-y-2">
            {contentKeys.map((key) => (
              <label key={key} className="block text-xs">
                <span className="text-muted-foreground">{key}</span>
                <textarea
                  value={draft[key] ?? ""}
                  onChange={(e) => setDraft((d) => ({ ...d, [key]: e.target.value }))}
                  rows={3}
                  className="mt-1 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                />
              </label>
            ))}
          </div>
          <button
            type="button"
            onClick={() => resubmit.mutate({ sectionKey: section.sectionKey, content: payload })}
            disabled={!canResubmit}
            className="inline-flex items-center gap-1.5 rounded-lg border border-amber-400/60 px-3 py-1.5 text-sm font-medium text-amber-700 hover:bg-amber-500/10 disabled:opacity-50 dark:text-amber-300"
          >
            <RotateCcw className="h-3.5 w-3.5" aria-hidden />
            {resubmit.isPending ? "Sending…" : "Send this part again"}
          </button>
          {!canResubmit && !resubmit.isPending ? (
            <p className="text-xs text-muted-foreground">
              Enter the corrected information before sending this part again.
            </p>
          ) : null}
        </div>
      ) : null}
    </li>
  );
}

export default function StudentApplicationPage() {
  const params = useParams<{ applicationId: string }>();
  const applicationId = decodeURIComponent(String(params?.applicationId ?? ""));
  const { data: sections, isLoading, isError } = useApplicationSections(applicationId);

  const returned = (sections ?? []).filter((s) => s.state === "RETURNED");

  return (
    <AppLayout>
      <PageShell
        title="Student registration"
        subtitle="Your application to the Nurses Council of Zimbabwe"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          {isLoading ? <p className="text-sm text-muted-foreground">Loading your application…</p> : null}
          {isError ? (
            <p className="text-sm text-muted-foreground">
              We could not load your application just now. Nothing you have submitted has changed.
            </p>
          ) : null}

          {returned.length > 0 ? (
            <div className="rounded-xl border border-amber-400/50 bg-amber-500/10 p-4">
              <p className="font-medium text-amber-800 dark:text-amber-200">
                {returned.length === 1
                  ? "One part of your application needs attention"
                  : `${returned.length} parts of your application need attention`}
              </p>
              <p className="mt-1 text-sm text-amber-700/90 dark:text-amber-200/80">
                The Council has asked for a correction. Your application has not been rejected and
                you do not need to start again.
              </p>
            </div>
          ) : null}

          {sections?.length ? (
            <ul className="space-y-3">
              {sections.map((section) => (
                <SectionRow
                  key={section.sectionKey}
                  section={section}
                  applicationId={applicationId}
                />
              ))}
            </ul>
          ) : null}

          {sections && sections.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              This application has no sections yet.
            </p>
          ) : null}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
