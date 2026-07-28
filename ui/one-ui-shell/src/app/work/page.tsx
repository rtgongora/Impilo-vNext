"use client";

/**
 * Work Home (Phase F1/F3) — the role/mode-aware operational landing page.
 *
 * Route: /work (guard: auth, NOT facility — oversight, programme, regulatory and support
 * contexts have no facility anchor at all and must still land somewhere real). When no
 * single context is recommended, renders WorkHomeWorkplacePicker in-page rather than
 * bouncing to /facility.
 *
 * Sections come entirely from the BFF (`GET /internal/v1/work-home`) — this page does not
 * decide which sections exist for which family, that branching lives in the BFF's family
 * adapters (Phase E4). Section presentation (icon/accent) comes from
 * `lib/work-home/section-registry.ts`, metadata only.
 */

import { useState } from "react";
import { Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { useWorkHome, useWorkHomeSectionRetry } from "@/hooks/queries/useWorkHome";
import { WorkHomeWorkplacePicker } from "@/components/work-home/WorkHomeWorkplacePicker";
import { WorkHomeSectionCard } from "@/components/work-home/WorkHomeSectionCard";
import { WORK_HOME_FAMILY_LABELS } from "@/lib/work-home/section-registry";

export default function WorkHomePage() {
  const { contract, isLoading: contractLoading } = useSessionExperienceContract();
  const resolvedContexts = contract?.resolvedWorkContexts ?? [];

  const [manualContextId, setManualContextId] = useState<string | null>(null);
  const [manualMode, setManualMode] = useState<string | null>(null);
  // Distinct from "no context resolved": lets a person with a recommended context still
  // return to the picker (Switch workplace) without the recommendation immediately winning again.
  const [showPicker, setShowPicker] = useState(false);

  const activeContextId = showPicker ? undefined : (manualContextId ?? contract?.recommendedContextId ?? undefined);
  const activeMode = manualContextId ? (manualMode ?? undefined) : undefined;

  const { workHome, isLoading: workHomeLoading, isError } = useWorkHome(activeContextId, activeMode);
  const retrySection = useWorkHomeSectionRetry(activeContextId, activeMode);

  function selectContext(contextId: string, mode: string | null) {
    setManualContextId(contextId);
    setManualMode(mode);
    setShowPicker(false);
  }

  function returnToPicker() {
    setShowPicker(true);
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

  // No recommended context and none manually chosen yet (or the person asked to switch):
  // render the chooser in-page.
  if (!activeContextId) {
    return (
      <AppLayout>
        <PageShell title="Work" density="compact">
          <WorkHomeWorkplacePicker contexts={resolvedContexts} onSelect={selectContext} />
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
    return (
      <AppLayout>
        <PageShell title="Work" density="compact">
          <div className="rounded-lg border border-border bg-card p-6 text-center">
            <p className="text-sm font-medium text-foreground">
              {workHome.friendlyState === "work_mode_unavailable"
                ? "This workplace isn't available in the requested mode right now."
                : "Your work context isn't available right now."}
            </p>
            <p className="mt-1 text-sm text-muted-foreground">
              Try choosing a different workplace, or check back shortly.
            </p>
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
        <div className="mb-3 flex items-center justify-between">
          <p className="text-xs text-muted-foreground">
            {resolvedContexts.find((c) => c.contextId === activeContextId)?.label}
          </p>
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
      </PageShell>
    </AppLayout>
  );
}
