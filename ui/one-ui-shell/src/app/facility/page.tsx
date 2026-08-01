"use client";

/**
 * Select Facility — Facility selection page.
 * Route: /facility | pageTitle: "Select Facility"
 */

import { useSearchParams } from "next/navigation";
import { BarChart3, Building2, Receipt, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { WorkplaceSelectionHub } from "@/components/home/WorkplaceSelectionHub";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilities, type FacilityResource } from "@/hooks/queries/useFacilities";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";
import { resolvePostFacilitySelectionPath } from "@/lib/resolve-post-login-destination";
import { matchRouteDefinition } from "@/lib/routes";

/**
 * Facility Mode ENTER trigger (L3 owns the entry; L2 owns the cockpit).
 *
 * Selecting a facility flips the derived ShellMode to `facility_mode`
 * (identity-context derives it once a facility + affiliation is present) and,
 * for facility-mode-eligible actors, routes into L2's facility cockpit at
 * `/facility/{id}`. T1 only triggers entry + routes; it never builds the
 * cockpit, setup wizard, or any `facility/[id]/**` destination. Authorization
 * to enter (FACILITY-MODE-ENTER) is specced to track P and enforced at the
 * ext_authz gate.
 */
function buildFacilityContext(facilityResource: FacilityResource) {
  return {
    id: facilityResource.id,
    name: facilityResource.attributes.name,
    code: facilityResource.attributes.code,
    facilityType: facilityResource.attributes.facilityType,
    capabilities: facilityResource.attributes.capabilities ?? [],
    operatingModel: facilityResource.attributes.operatingModel,
  };
}

export default function FacilityPage() {
  const { hasRole } = useAuthStore();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const { data, isLoading } = useFacilities();
  const { facility, selectFacility, enterMode } = useExperienceEntry();

  const facilities = data?.data ?? [];
  const pendingRoute = returnTo ? matchRouteDefinition(returnTo) : null;

  // Actors who operate a facility as a place enter Facility Mode rather than a
  // single clinical workspace. Clinical workers continue into their workspace.
  const facilityModeEligible =
    hasRole("SYSTEM_ADMIN") ||
    hasRole("FACILITY_ADMIN") ||
    hasRole("FACILITY_WORKFORCE_MANAGER") ||
    hasRole("FACILITY_MANAGER");

  function handleSelect(facilityResource: FacilityResource) {
    // When entering Facility Mode, route into L2's cockpit (/facility/{id});
    // otherwise resolve the normal post-selection destination.
    const nextPath = facilityModeEligible && !returnTo
      ? `/facility/${facilityResource.id}`
      : resolvePostFacilitySelectionPath(returnTo);
    selectFacility(buildFacilityContext(facilityResource), {
      mode: "clinical",
      nextPath,
    });
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
          title="Choose your workplace"
          subtitle={
            facility
              ? `${facility.name} is your current context. Choose it again to continue, or select another authorised workplace.`
              : "Select an authorised facility, then continue into the workspace and shift you need."
          }
          modeActions={[
            ...(facilityModeEligible && facility
              ? [{
                  label: "Enter Facility Mode",
                  description: "Open the facility-wide operational and admin context.",
                  icon: Building2,
                  onClick: () => enterMode("clinical", `/facility/${facility.id}/mode`),
                }]
              : []),
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
