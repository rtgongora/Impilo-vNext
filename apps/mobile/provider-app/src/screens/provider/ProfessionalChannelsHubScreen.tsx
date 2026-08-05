/**
 * ProfessionalChannelsHubScreen — Tier-3 wave 7 parity for omnichannel, coverage, credentials, and public-health routes.
 */
import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { ProfessionalHubBody } from "../../components/ProfessionalHubBody";
import { resolveHubSections } from "../../lib/hubUi";
import { HUB_FALLBACK_SECTIONS } from "../../lib/hubCatalogue.generated";
import { useAuth } from "@impilo/mobile-auth";
import {
  fetchProfessionalChannelsHub,
} from "../../services/professionalChannelsHubService";

export function ProfessionalChannelsHubScreen() {
  const auth = useAuth();
  const { data, isPending, isError, refetch, isRefetching } = useQuery({
    queryKey: ["professional-channels-hub"],
    queryFn: fetchProfessionalChannelsHub,
    retry: 1,
  });

  // Offline the bundled layout is filtered here, because there is no BFF to do it.
  // Memoised: a fresh [] each render would change the dep below on every render.
  const roles = useMemo(() => auth.user?.realm_access?.roles ?? [], [auth.user]);
  const { sections, isOfflineLayout } = useMemo(
    () => resolveHubSections(data?.sections, HUB_FALLBACK_SECTIONS["professional-channels"], roles),
    [data, roles],
  );

  return (
    <ProfessionalHubBody
      rootTestID="professional-channels-hub-screen"
      heading="Channels & public health"
      description="Omnichannel and coverage operations, professional credentials, and public-health programme surfaces aligned with the web shell."
      sections={sections}
      isPending={isPending}
      isError={isError}
      isOfflineLayout={isOfflineLayout}
      refreshedAt={data?.refreshed_at}
      isRefetching={isRefetching}
      onRefresh={() => refetch()}
      getSectionTestId={(id) => `professional-channels-hub-section-${id}`}
    />
  );
}
