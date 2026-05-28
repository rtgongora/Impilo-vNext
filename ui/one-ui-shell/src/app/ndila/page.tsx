"use client";

import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { NdilaIntelligencePanel } from "@/components/ndila";
import { OpsMapPanel } from "@/components/operations/OpsMapPanel";

export default function NdilaWorkspacePage() {
  return (
    <PlaneWorkspaceShell
      title="Ndila geospatial intelligence"
      subtitle="Facility mapping, routes, catchment areas, and location-based service discovery"
      plane="Data & Intelligence Plane"
      maturity="partial"
      maturityDetail="Tile preview and markers; full map client rollout in progress"
      relatedLinks={[
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
          detail="Live Ndila BFF when service is up; no silent coordinate mocks"
        />
      </div>
      <div className="space-y-6">
        <NdilaIntelligencePanel
          question="Which facilities and public health sites are in this catchment, and what are routing priorities for field teams?"
          areaScope={{ level: "NATIONAL" }}
          purposeOfUse="GEOSPATIAL_INTELLIGENCE"
        />
        <OpsMapPanel
          title="Ndila operations map"
          subtitle="Facility and asset markers via Ndila tile config"
          markers={[]}
          emptyHint="Load facility registry or enable Ndila tile service for map markers."
        />
      </div>
    </PlaneWorkspaceShell>
  );
}
