"use client";

import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { NdilaIntelligencePanel, NdilaWorkspaceMapPanel } from "@/components/ndila";
import { UnifiedLogisticsMapPanel } from "@/components/operations/UnifiedLogisticsMapPanel";

export default function NdilaWorkspacePage() {
  return (
    <PlaneWorkspaceShell
      title="Ndila geospatial intelligence"
      subtitle="Facility mapping, routes, catchment areas, and location-based service discovery"
      serviceSlug="ndila"
      plane="Data & Intelligence Plane"
      maturity="partial"
      maturityDetail="Full map workspace with governed tiles, nearby search, and route preview"
      relatedLinks={[
        { label: "Data & intelligence", href: "/data-intelligence" },
        { label: "NDR pipelines", href: "/data-intelligence/pipelines" },
        { label: "Nhume fleet map", href: "/nhume/map" },
        { label: "Public health sites", href: "/public-health/site-registry" },
        { label: "Facility registry", href: "/registry/facilities" },
      ]}
      nompiloHint="Ask Nompilo for nearby facilities, route guidance, or catchment context."
    >
      <TrustContextBanner purposeOfUse="GEOSPATIAL_INTELLIGENCE" />
      <div className="mb-4">
        <FeatureMaturityBadge
          status="partial"
          detail="Live Ndila BFF + map workspace when service is up; no silent coordinate mocks"
        />
      </div>
      <div className="space-y-6">
        <NdilaWorkspaceMapPanel />
        <NdilaIntelligencePanel
          question="Which facilities and public health sites are in this catchment, and what are routing priorities for field teams?"
          areaScope={{ level: "NATIONAL" }}
          purposeOfUse="GEOSPATIAL_INTELLIGENCE"
        />
        <UnifiedLogisticsMapPanel
          title="Ndila logistics overlay"
          subtitle="Nhume last-mile + dispatch task ops on Ndila tiles"
        />
      </div>
    </PlaneWorkspaceShell>
  );
}
