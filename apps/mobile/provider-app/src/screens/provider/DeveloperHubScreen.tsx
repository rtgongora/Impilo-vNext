/**
 * DeveloperHubScreen — Tier-3 wave 5 parity for web developer portal landings.
 */
import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { ProfessionalHubBody } from "../../components/ProfessionalHubBody";
import { resolveHubSections } from "../../lib/hubUi";
import { HUB_FALLBACK_SECTIONS } from "../../lib/hubCatalogue.generated";
import { useAuth } from "@impilo/mobile-auth";
import { fetchDeveloperHub } from "../../services/developerHubService";

export function DeveloperHubScreen() {
  const auth = useAuth();
  const { data, isPending, isError, refetch, isRefetching } = useQuery({
    queryKey: ["developer-hub"],
    queryFn: fetchDeveloperHub,
    retry: 1,
  });

  // Offline the bundled layout is filtered here, because there is no BFF to do it.
  // Memoised: a fresh [] each render would change the dep below on every render.
  const roles = useMemo(() => auth.user?.realm_access?.roles ?? [], [auth.user]);
  const { sections, isOfflineLayout } = useMemo(
    () => resolveHubSections(data?.sections, HUB_FALLBACK_SECTIONS["developer"], roles),
    [data, roles],
  );

  return (
    <ProfessionalHubBody
      rootTestID="developer-hub-screen"
      heading="Developer Portal"
      description="API catalog, client registration, and sandbox entry points aligned with the web shell."
      sections={sections}
      isPending={isPending}
      isError={isError}
      isOfflineLayout={isOfflineLayout}
      refreshedAt={data?.refreshed_at}
      isRefetching={isRefetching}
      onRefresh={() => refetch()}
      getSectionTestId={(id) => `developer-hub-section-${id}`}
    />
  );
}
