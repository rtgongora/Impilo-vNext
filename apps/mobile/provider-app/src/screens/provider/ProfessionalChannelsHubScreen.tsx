/**
 * ProfessionalChannelsHubScreen — Tier-3 wave 7 parity for omnichannel, coverage, credentials, and public-health routes.
 */
import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { ProfessionalHubBody } from "../../components/ProfessionalHubBody";
import {
  fetchProfessionalChannelsHub,
  type ProfessionalChannelsSection,
} from "../../services/professionalChannelsHubService";

const FALLBACK_SECTIONS: ProfessionalChannelsSection[] = [
  { id: "omnichannel", title: "Omnichannel Hub", web_path: "/omnichannel", hint: "Queues, messaging, and channel routing." },
  // /coverage admits only ADMIN; this hub is handed to providers. Ruvimbo Provider is the face
  // their roles actually open (role group CLINICAL). Tapping a section opens web_path in the web
  // shell, so a role the route refuses becomes a silent bounce to /home in a browser the person
  // just got thrown into — kept in step with the BFF stub and the one-ui-shell catalogue.
  { id: "coverage", title: "Coverage Operations", web_path: "/ruvimbo/provider", hint: "Schemes, eligibility, and verification." },
  { id: "home_credentials", title: "Credentials & CPD", web_path: "/home/credentials", hint: "Professional licenses and learning credits." },
  { id: "ph_surveillance", title: "Surveillance", web_path: "/public-health/surveillance", hint: "Signals, case lines, and indicators." },
  { id: "ph_campaigns", title: "Campaigns", web_path: "/public-health/campaigns", hint: "Immunisation and outreach waves." },
  { id: "ph_site_registry", title: "Site Registry", web_path: "/public-health/site-registry", hint: "Community sites and outreach anchors." },
  { id: "ph_site_profile", title: "Site Profile", web_path: "/public-health/site-registry/[siteId]", hint: "Single-site programme detail." },
];

export function ProfessionalChannelsHubScreen() {
  const { data, isPending, isError, refetch, isRefetching } = useQuery({
    queryKey: ["professional-channels-hub"],
    queryFn: fetchProfessionalChannelsHub,
    retry: 1,
  });

  const sections = useMemo(() => {
    const remote = data?.sections?.filter((s) => s?.id && s?.title);
    return remote && remote.length > 0 ? remote : FALLBACK_SECTIONS;
  }, [data]);

  return (
    <ProfessionalHubBody
      rootTestID="professional-channels-hub-screen"
      heading="Channels & public health"
      description="Omnichannel and coverage operations, professional credentials, and public-health programme surfaces aligned with the web shell."
      sections={sections}
      isPending={isPending}
      isError={isError}
      refreshedAt={data?.refreshed_at}
      isRefetching={isRefetching}
      onRefresh={() => refetch()}
      getSectionTestId={(id) => `professional-channels-hub-section-${id}`}
    />
  );
}
