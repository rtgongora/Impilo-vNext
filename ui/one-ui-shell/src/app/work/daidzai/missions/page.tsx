"use client";

/**
 * EMS mission board — for a selected incident (?incident=id), resolves the dispatched
 * EMS mission and drives its validated state machine + prehospital ePCR via the
 * EmsMissionWorkspace. Daidzai owns mission execution; this is the crew/dispatcher
 * cockpit. Route: /work/daidzai/missions?incident={id}
 */

import { Suspense } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { AlertTriangle, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { EmsMissionWorkspace } from "@/components/clinical/EmsMissionWorkspace";
import { useEmsMissionByIncident } from "@/hooks/queries/useDaidzai";

function MissionsInner() {
  const params = useSearchParams();
  const incidentId = params.get("incident");
  const mission = useEmsMissionByIncident(incidentId ?? undefined);

  if (!incidentId) {
    return (
      <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
        Select an incident from the dispatch console to open its EMS mission.
      </div>
    );
  }

  if (mission.isLoading) {
    return (
      <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading mission…
      </div>
    );
  }

  const missionId = mission.data?.id ? String(mission.data.id) : undefined;

  if (mission.isError || !missionId) {
    return (
      <div className="space-y-3">
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
          <div className="mb-1 flex items-center gap-2 font-semibold">
            <AlertTriangle className="h-4 w-4" /> No EMS mission for this incident yet
          </div>
          <p>Dispatch an ambulance from the dispatch console first.</p>
        </div>
        <Link href="/work/daidzai/dispatch" className="text-sm text-primary underline">
          → Go to EMS dispatch
        </Link>
      </div>
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
      <EmsMissionWorkspace missionId={missionId} pctEncounterRef={mission.data?.pctEncounterRef ? String(mission.data.pctEncounterRef) : undefined} />
      <NompiloContextualGuidance routePath="/work/daidzai" />
    </div>
  );
}

export default function DaidzaiMissionsPage() {
  return (
    <AppLayout>
      <PageShell
        title="EMS mission"
        subtitle="Advance the ambulance crew through the state machine and capture the ePCR"
        serviceSlug="daidzai"
      >
        <Suspense
          fallback={
            <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading…
            </div>
          }
        >
          <MissionsInner />
        </Suspense>
      </PageShell>
    </AppLayout>
  );
}
