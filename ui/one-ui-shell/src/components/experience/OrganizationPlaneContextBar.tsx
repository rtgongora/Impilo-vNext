"use client";

/**
 * Organization administration plane banner when entering from `/organization-admin`
 * or when operational context is organization_admin with a remembered surface.
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Building2 } from "lucide-react";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { OPERATIONAL_MODE_DEF, type OrganizationAdminSurface } from "@/lib/operational-context";

const SURFACE_LABEL: Record<OrganizationAdminSurface, string> = {
  finance_revenue: "Finance & revenue",
  facility_admin: "Facility & ward administration",
  service_point_admin: "Service points & devices",
  staffing_roster: "Staffing & scheduling",
  approvals: "Policies & approvals",
  reports: "Operational reports",
  settings: "Organization settings",
};

interface OrganizationPlaneContextBarProps {
  preferStore?: boolean;
}

export function OrganizationPlaneContextBar({ preferStore = false }: OrganizationPlaneContextBarProps) {
  const searchParams = useSearchParams();
  const fromOrgAdmin = searchParams.get("from") === "organization-admin";
  const operationalMode = useOperationalContextStore((s) => s.operationalMode);
  const surface = useOperationalContextStore((s) => s.organizationAdminSurface);

  const activePlane =
    fromOrgAdmin || (preferStore && operationalMode === "organization_admin");

  if (!activePlane) return null;

  const modeDef = OPERATIONAL_MODE_DEF.organization_admin;
  const subLabel = surface ? SURFACE_LABEL[surface] : null;

  return (
    <div className="mb-4 rounded-xl border border-violet-300/90 bg-gradient-to-r from-violet-50 to-violet-100/40 px-4 py-3 text-sm text-violet-950 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-start gap-2">
          <Building2 className="mt-0.5 h-4 w-4 shrink-0 text-violet-800" aria-hidden />
          <div>
            <p className="font-semibold text-violet-950">{modeDef.label}</p>
            <p className="text-xs text-violet-900/85">
              {subLabel ? (
                <>
                  Active surface: <span className="font-medium">{subLabel}</span>. Governance and configuration work
                  for this organization — not sovereign registry operations.
                </>
              ) : (
                <>
                  You entered from organization administration. This plane covers facility operations management, not
                  point-of-care Facility Work execution.
                </>
              )}
            </p>
          </div>
        </div>
        <Link
          href="/organization-admin"
          className="shrink-0 rounded-lg border border-violet-400/80 bg-card/80 px-3 py-1.5 text-xs font-medium text-violet-950 hover:bg-card"
        >
          Org admin home
        </Link>
      </div>
    </div>
  );
}
