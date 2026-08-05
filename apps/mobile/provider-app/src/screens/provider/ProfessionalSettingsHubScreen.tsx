/**
 * ProfessionalSettingsHubScreen — Tier-3 wave 6 parity for web `/settings*` professional landings.
 */
import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { ProfessionalHubBody } from "../../components/ProfessionalHubBody";
import { resolveHubSections } from "../../lib/hubUi";
import { HUB_FALLBACK_SECTIONS } from "../../lib/hubCatalogue.generated";
import { useAuth } from "@impilo/mobile-auth";
import {
  fetchProfessionalSettingsHub,
} from "../../services/professionalSettingsHubService";

export function ProfessionalSettingsHubScreen() {
  const auth = useAuth();
  const { data, isPending, isError, refetch, isRefetching } = useQuery({
    queryKey: ["professional-settings-hub"],
    queryFn: fetchProfessionalSettingsHub,
    retry: 1,
  });

  // Offline the bundled layout is filtered here, because there is no BFF to do it.
  // Memoised: a fresh [] each render would change the dep below on every render.
  const roles = useMemo(() => auth.user?.realm_access?.roles ?? [], [auth.user]);
  const { sections, isOfflineLayout } = useMemo(
    () => resolveHubSections(data?.sections, HUB_FALLBACK_SECTIONS["professional-settings"], roles),
    [data, roles],
  );

  return (
    <ProfessionalHubBody
      rootTestID="professional-settings-hub-screen"
      heading="Professional settings"
      description="Mirrors the web settings sidebar for the professional context; full editors open in workspace when available."
      sections={sections}
      isPending={isPending}
      isError={isError}
      isOfflineLayout={isOfflineLayout}
      refreshedAt={data?.refreshed_at}
      isRefetching={isRefetching}
      onRefresh={() => refetch()}
      getSectionTestId={(id) => `professional-settings-hub-section-${id}`}
    />
  );
}
