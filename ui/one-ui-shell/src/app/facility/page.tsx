"use client";

/**
 * Select Facility — Facility selection page.
 * Route: /facility | pageTitle: "Select Facility"
 */

import { useSearchParams } from "next/navigation";
import { BarChart3, Receipt, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { WorkplaceSelectionHub } from "@/components/home/WorkplaceSelectionHub";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilities, type FacilityResource } from "@/hooks/queries/useFacilities";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";
import { resolvePostFacilitySelectionPath } from "@/lib/resolve-post-login-destination";
import { matchRouteDefinition } from "@/lib/routes";

export default function FacilityPage() {
  const { hasRole } = useAuthStore();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const { data, isLoading } = useFacilities();
  const { facility, selectFacility, enterMode } = useExperienceEntry();

  const facilities = data?.data ?? [];
  const pendingRoute = returnTo ? matchRouteDefinition(returnTo) : null;

  function handleSelect(facilityResource: FacilityResource) {
    selectFacility(
      {
        id: facilityResource.id,
        name: facilityResource.attributes.name,
        code: facilityResource.attributes.code,
        facilityType: facilityResource.attributes.facilityType,
        capabilities: facilityResource.attributes.capabilities ?? [],
        operatingModel: facilityResource.attributes.operatingModel,
      },
      {
        mode: "clinical",
        nextPath: resolvePostFacilitySelectionPath(returnTo),
      }
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Start Work Session"
        subtitle="Where are you working today?"
      >
        {pendingRoute ? (
          <p className="mb-4 rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm text-primary-hover">
            Select a facility to continue to{" "}
            <span className="font-semibold">{pendingRoute.pageTitle ?? pendingRoute.navLabel ?? returnTo}</span>.
          </p>
        ) : null}
        <WorkplaceSelectionHub
          facilities={facilities}
          isLoading={isLoading}
          onSelectFacility={handleSelect}
          selectedFacilityId={facility?.id}
          title="Workplace Selection Hub"
          subtitle="Lovable-style inline facility cards keep the selection flow in one place while preserving the existing auth and router guard sequence."
          modeActions={[
            ...(hasRole("SYSTEM_ADMIN") || hasRole("FACILITY_ADMIN") || hasRole("DEVELOPER")
              ? [{
                  label: "Administration",
                  description: "Open the admin surface without binding to a facility first.",
                  icon: Shield,
                  onClick: () => enterMode("admin", "/admin"),
                }]
              : []),
            ...(hasRole("SYSTEM_ADMIN") || hasRole("FACILITY_ADMIN") || hasRole("FINANCE")
              ? [{
                  label: "Finance",
                  description: "Jump directly into finance orchestration and review queues.",
                  icon: Receipt,
                  onClick: () => enterMode("finance", "/finance"),
                }]
              : []),
            {
              label: "Reports",
              description: "Review cross-facility service performance and monitoring.",
              icon: BarChart3,
              href: "/reports",
            },
          ]}
        />
      </PageShell>

      <NompiloHint
        message="Select the facility where you'll be working today. You can only see facilities your employer's HR has linked to your profile."
      />
    </AppLayout>
  );
}
