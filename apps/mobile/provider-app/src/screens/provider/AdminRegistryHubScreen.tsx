/**
 * AdminRegistryHubScreen — Tier-3 wave 3 parity for web admin + registry professional landings.
 * Single mobile hub maps multiple canonical routes (see parity matrix tier3-wave3).
 */
import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { ProfessionalHubBody } from "../../components/ProfessionalHubBody";
import { fetchAdminRegistryHub, type AdminRegistrySection } from "../../services/adminRegistryService";

const FALLBACK_SECTIONS: AdminRegistrySection[] = [
  { id: "admin", title: "Administration", web_path: "/admin", hint: "Users, roles, policies, and platform configuration." },
  { id: "registry", title: "Registry Hub", web_path: "/registry", hint: "Patient and provider registry entry points." },
  { id: "registry_admin", title: "Registry Administration", web_path: "/registry-admin", hint: "Registry configuration and governance." },
  { id: "organization_admin", title: "Organization Administration", web_path: "/organization-admin", hint: "Sites, cadres, and org structure." },
  { id: "public_health", title: "Public Health", web_path: "/public-health", hint: "Programmes, surveillance, and campaigns." },
  { id: "id_services", title: "Identity Services", web_path: "/id-services", hint: "Identity proofing and credential services." },
  { id: "access", title: "Access Channels", web_path: "/access", hint: "Kiosk, landline, and alternate access paths." },
  { id: "ai_governance", title: "AI Governance", web_path: "/ai-governance", hint: "Model registry, safety, and audit controls." },
];

export function AdminRegistryHubScreen() {
  const { data, isPending, isError, refetch, isRefetching } = useQuery({
    queryKey: ["admin-registry-hub"],
    queryFn: fetchAdminRegistryHub,
    retry: 1,
  });

  const sections = useMemo(() => {
    const remote = data?.sections?.filter((s) => s?.id && s?.title);
    return remote && remote.length > 0 ? remote : FALLBACK_SECTIONS;
  }, [data]);

  return (
    <ProfessionalHubBody
      rootTestID="admin-registry-hub-screen"
      heading="Admin & Registry"
      description="Mobile entry points aligned with the web professional plane. Deep links open in the full workspace when available."
      sections={sections}
      isPending={isPending}
      isError={isError}
      refreshedAt={data?.refreshed_at}
      isRefetching={isRefetching}
      onRefresh={() => refetch()}
      getSectionTestId={(id) => `admin-registry-section-${id}`}
    />
  );
}
