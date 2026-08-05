/**
 * OpsReportsHubScreen — Tier-3 wave 4 parity for web operations + reports professional landings.
 */
import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { ProfessionalHubBody } from "../../components/ProfessionalHubBody";
import { resolveHubSections } from "../../lib/hubUi";
import { HUB_FALLBACK_SECTIONS } from "../../lib/hubCatalogue.generated";
import { useAuth } from "@impilo/mobile-auth";
import { fetchOpsReportsHub } from "../../services/opsReportsService";

export function OpsReportsHubScreen() {
  const auth = useAuth();
  const { data, isPending, isError, refetch, isRefetching } = useQuery({
    queryKey: ["ops-reports-hub"],
    queryFn: fetchOpsReportsHub,
    retry: 1,
  });

  // Offline the bundled layout is filtered here, because there is no BFF to do it.
  // Memoised: a fresh [] each render would change the dep below on every render.
  const roles = useMemo(() => auth.user?.realm_access?.roles ?? [], [auth.user]);
  const { sections, isOfflineLayout } = useMemo(
    () => resolveHubSections(data?.sections, HUB_FALLBACK_SECTIONS["ops-reports"], roles),
    [data, roles],
  );

  return (
    <ProfessionalHubBody
      rootTestID="ops-reports-hub-screen"
      heading="Operations & Reports"
      description="Canonical web landings for platform operations and reporting. Full workflows open in the workspace when available."
      sections={sections}
      isPending={isPending}
      isError={isError}
      isOfflineLayout={isOfflineLayout}
      refreshedAt={data?.refreshed_at}
      isRefetching={isRefetching}
      onRefresh={() => refetch()}
      getSectionTestId={(id) => `ops-reports-section-${id}`}
    />
  );
}
