"use client";

import { ProviderClaimJourney } from "@/components/auth/ProviderClaimJourney";

/**
 * Citizen-facing provider claim/recovery journey (IATG WS-F).
 *
 * Route: /citizen/provider-claim — reached from the ProviderRoleActivationRail
 * when no Provider ID is linked to the signed-in Health ID. The journey is
 * fully backed by the BFF /api/v1/provider-claim endpoints; feature-pending
 * lanes render honestly as "coming soon".
 */
export default function CitizenProviderClaimPage() {
  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <ProviderClaimJourney />
    </div>
  );
}
