"use client";

/**
 * Select Facility — Facility selection page.
 * Route: /facility | pageTitle: "Select Facility"
 */

import { BarChart3, Receipt, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { WorkplaceSelectionHub } from "@/components/home/WorkplaceSelectionHub";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilities, type FacilityResource } from "@/hooks/queries/useFacilities";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";

export default function FacilityPage() {
  const { hasRole } = useAuthStore();
  const { data, isLoading } = useFacilities();
  const { facility, selectFacility, enterMode } = useExperienceEntry();

  const facilities = data?.data ?? [];

  function handleSelect(facilityResource: FacilityResource) {
    selectFacility(
      {
        id: facilityResource.id,
        name: facilityResource.attributes.name,
        code: facilityResource.attributes.code,
        facilityType: facilityResource.attributes.facilityType,
        capabilities: facilityResource.attributes.capabilities ?? [],
      },
      {
        mode: "clinical",
        nextPath: "/workspace",
      }
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Select Facility"
        subtitle="Complete the first step in experience entry by choosing the facility that anchors your current work context."
      >
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
    </AppLayout>
  );
}
