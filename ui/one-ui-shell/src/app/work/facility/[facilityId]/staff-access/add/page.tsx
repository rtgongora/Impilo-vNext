"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { AdministrationGovernanceShell } from "@/components/administration-governance/AdministrationGovernanceShell";
import { FacilityStaffAssignmentForm } from "@/components/administration-governance/FacilityStaffAssignmentForm";
import { FriendlyBlockedState } from "@/components/administration-governance/FriendlyBlockedState";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { canAccessAdministrationPath } from "@/lib/administration-governance";

export default function FacilityStaffAccessAddPage() {
  const params = useParams<{ facilityId: string }>();
  const facilityId = params.facilityId;
  const { contract } = useSessionExperienceContract();
  const allowed = contract ? canAccessAdministrationPath(contract, `/work/facility/${facilityId}/staff-access/add`) : false;

  return (
    <AdministrationGovernanceShell title="Assign Verified Provider / Worker" requireGovernanceEntry={false}>
      <div className="space-y-6">
        <Link href={`/work/facility/${facilityId}/staff-access`} className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900">
          <ArrowLeft className="h-4 w-4" />
          Staff & Access
        </Link>
        {!allowed || !contract ? (
          <FriendlyBlockedState state={contract?.friendlyResolutionState ?? "policy_denied"} />
        ) : (
          <FacilityStaffAssignmentForm facilityId={facilityId} />
        )}
      </div>
    </AdministrationGovernanceShell>
  );
}
