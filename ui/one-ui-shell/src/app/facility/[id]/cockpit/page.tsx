"use client";

/**
 * Facility Mode cockpit — home / overview / quick-actions.
 * Route: /facility/[id]/cockpit
 *
 * T4 owns this route (the destination a provider lands in after ENTERing facility
 * mode). Rendered strictly from the FacilityModeContext read-model produced by TUSO
 * and composed by the experience-bff FacilityModeController.
 */

import { useParams, useSearchParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, ArrowLeft } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { CockpitOverview } from "@/components/facility-mode/CockpitOverview";
import { useFacilityModeContext } from "@/components/facility-mode/useFacilityMode";

export default function FacilityCockpitPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const id = params.id as string;
  const pctFacilityId = searchParams.get("pctFacilityId") ?? undefined;

  const { data, isLoading, error } = useFacilityModeContext(id, pctFacilityId);

  return (
    <AppLayout>
      <PageShell title="Facility Mode" subtitle="Cockpit overview and quick actions">
        <div className="mb-4">
          <Link
            href="/facility"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to facilities
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading facility mode…</span>
          </div>
        ) : error || !data ? (
          <div className="rounded-lg border border-danger/28 bg-danger-soft p-6 text-center">
            <AlertTriangle className="mx-auto mb-2 h-8 w-8 text-red-400" />
            <p className="text-sm text-red-600">
              Failed to load facility-mode context. The facility registry (TUSO) may be
              unavailable.
            </p>
          </div>
        ) : (
          <div className="max-w-4xl">
            <CockpitOverview facilityId={id} envelope={data} />
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
