/**
 * DeveloperHubScreen — Tier-3 wave 5 parity for web developer portal landings.
 */
import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { ProfessionalHubBody } from "../../components/ProfessionalHubBody";
import { resolveHubSections } from "../../lib/hubUi";
import { fetchDeveloperHub, type DeveloperHubSection } from "../../services/developerHubService";

const FALLBACK_SECTIONS: DeveloperHubSection[] = [
  { id: "developer", title: "Developer Portal", web_path: "/developer", hint: "Integrator landing and documentation." },
  { id: "developer_api_catalog", title: "API Catalog", web_path: "/developer/api-catalog", hint: "Browse versioned APIs and schemas." },
  { id: "developer_clients", title: "Client Registration", web_path: "/developer/clients", hint: "Register OAuth clients and callbacks." },
  { id: "developer_sandbox", title: "Sandbox", web_path: "/developer/sandbox", hint: "Test tenants and sample data access." },
];

export function DeveloperHubScreen() {
  const { data, isPending, isError, refetch, isRefetching } = useQuery({
    queryKey: ["developer-hub"],
    queryFn: fetchDeveloperHub,
    retry: 1,
  });

  // An empty answer from the BFF is a real answer — see resolveHubSections.
  const { sections, isOfflineLayout } = useMemo(
    () => resolveHubSections(data?.sections, FALLBACK_SECTIONS),
    [data],
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
