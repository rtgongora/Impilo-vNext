"use client";

import { useSearchParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FacilityClaimJourney } from "@/components/facility/FacilityClaimJourney";

/**
 * Facility administrator claim journey (IATG Wave 2, WS-E).
 *
 * Route: /facility/claim?facilityUuid=<uuid> — reached from a facility detail page via the
 * "Claim administration" link. Fully backed by the BFF /api/v1/facility-claim endpoints;
 * feature-pending lanes (document / org invitation) render honestly as "coming soon".
 */
export default function FacilityClaimPage() {
  const searchParams = useSearchParams();
  const facilityUuid = searchParams.get("facilityUuid") ?? undefined;

  return (
    <AppLayout>
      <PageShell
        title="Claim facility administration"
        subtitle="Request administrator access for a facility"
      >
        <div className="mx-auto max-w-2xl">
          <FacilityClaimJourney facilityUuid={facilityUuid} />
        </div>
      </PageShell>
    </AppLayout>
  );
}
