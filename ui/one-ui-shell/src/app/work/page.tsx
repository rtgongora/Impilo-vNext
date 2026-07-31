"use client";

/**
 * Work Home (Phase F1/F3) — the role/mode-aware operational landing page.
 *
 * Route: /work (guard: auth, NOT facility — oversight, programme, regulatory and support
 * contexts have no facility anchor at all and must still land somewhere real). When no
 * duty session is active, renders WorkHomeWorkplacePicker which mints before composing.
 *
 * Sections come entirely from the BFF (`GET /internal/v1/work-home`) — this page does not
 * decide which sections exist for which family.
 */

import { Loader2, RefreshCw } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { useWorkSessionStore } from "@/hooks/useWorkSessionStore";
import { useWorkHome, useWorkHomeSectionRetry } from "@/hooks/queries/useWorkHome";
import { WorkHomeWorkplacePicker } from "@/components/work-home/WorkHomeWorkplacePicker";
import { WorkHomeSectionCard } from "@/components/work-home/WorkHomeSectionCard";
import { WorkOperationsPanel } from "@/components/work-home/WorkOperationsPanel";
import { WORK_HOME_FAMILY_LABELS } from "@/lib/work-home/section-registry";
import { describeWorkContextRestrictions, describeWorkMode } from "@/lib/work-home/restriction-copy";

function friendlyWorkHomeMessage(state: string | undefined): { title: string; body: string } {
  switch (state) {
    case "work_mode_unavailable":
      return {
        title: "This workplace isn't available in the requested mode right now.",
        body: "Try another mode for this workplace, or choose a different workplace.",
      };
    case "work_context_unavailable":
      return {
        title: "Your work context isn't available right now.",
        body: "The assignment could not be re-proven from its source. Try again shortly, or pick another workplace.",
      };
    default:
      return {
        title: "Your work context isn't available right now.",
        body: "Try choosing a different workplace, or check back shortly.",
      };
  }
}

export default function WorkHomePage() {
  const searchParams = useSearchParams();
  const preferMode = searchParams.get("prefer_mode");
  const { contract, isLoading: contractLoading } = useSessionExperienceContract();
  const resolvedContexts = contract?.resolvedWorkContexts ?? [];
  const session = useWorkSessionStore((s) => s.session);

  // Compose only on a minted duty session — never on recommendedContextId alone.
  const activeContextId = session?.contextId || undefined;
  const activeMode = session?.workMode || undefined;
  const activeContext = resolvedContexts.find((c) => c.contextId === activeContextId);
  const restrictionLines = describeWorkContextRestrictions(activeContext?.restrictions);

  const { workHome, isLoading: workHomeLoading, isError } = useWorkHome(activeContextId, activeMode);
  const retrySection = useWorkHomeSectionRetry(activeContextId, activeMode);

  function returnToPicker() {
    // Clear local session so the picker mints fresh; token revocation happens on next mint
    // via previousJti inside useSwitchWorkContext.
    useWorkSessionStore.getState().clearSession();
    window.location.assign("/work");
  }

  if (contractLoading) {
    return (
      <AppLayout>
        <PageShell title="Work" density="compact">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading your work context…
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  if (!activeContextId) {
    return (
      <AppLayout>
        <PageShell title="Work" density="compact">
          <WorkHomeWorkplacePicker contexts={resolvedContexts} preferMode={preferMode} />
        </PageShell>
      </AppLayout>
    );
  }

  if (workHomeLoading) {
    return (
      <AppLayout>
        <PageShell title="Work" density="compact">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className="h-32 animate-pulse rounded-lg border border-border bg-card" />
            ))}
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  if (isError || workHome.friendlyState) {
    const copy = friendlyWorkHomeMessage(workHome.friendlyState);
    return (
      <AppLayout>
        <PageShell title="Work" density="compact">
          <div className="rounded-lg border border-border bg-card p-6 text-center">
            <p className="text-sm font-medium text-foreground">{copy.title}</p>
            <p className="mt-1 text-sm text-muted-foreground">{copy.body}</p>
            <button
              type="button"
              onClick={returnToPicker}
              className="mt-3 inline-flex items-center gap-1 rounded-lg border border-border px-3 py-1.5 text-sm hover:bg-background"
            >
              <RefreshCw className="h-3.5 w-3.5" /> Choose a workplace
            </button>
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Work"
        subtitle={workHome.family ? WORK_HOME_FAMILY_LABELS[workHome.family] ?? workHome.family : undefined}
        density="compact"
      >
        <div className="mb-3 flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs text-muted-foreground">
              {activeContext?.label ?? activeContextId}
              {activeMode ? ` · ${describeWorkMode(activeMode)}` : ""}
            </p>
            {restrictionLines.map((line) => (
              <p key={line} className="text-xs text-amber-700 dark:text-amber-400">
                {line}
              </p>
            ))}
          </div>
          {resolvedContexts.length > 1 && (
            <button
              type="button"
              onClick={returnToPicker}
              className="text-xs text-muted-foreground underline hover:text-foreground"
            >
              Switch workplace
            </button>
          )}
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {workHome.sections.map((section) => (
            <WorkHomeSectionCard key={section.sectionId} section={section} onRetry={retrySection} />
          ))}
        </div>
        {workHome.sections.length === 0 && (
          <p className="text-sm text-muted-foreground">No sections to show for this workplace yet.</p>
        )}

        {workHome.family === "FACILITY_CLINICAL" && (
          <div className="mt-4">
            <WorkOperationsPanel />
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
