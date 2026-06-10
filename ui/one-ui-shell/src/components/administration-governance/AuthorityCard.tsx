"use client";

import type { SessionExperienceContract } from "@/lib/trust";

interface AuthorityCardProps {
  contract: SessionExperienceContract;
}

export function AuthorityCard({ contract }: AuthorityCardProps) {
  const org = contract.organisation;
  const assignment = contract.activeWorkAssignments[0];

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="text-sm font-semibold text-slate-900">Your current authority</h3>
      <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-slate-500">Organisation</dt>
          <dd className="font-medium text-slate-900">{org?.organisationName ?? org?.organisationType ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Organisation type</dt>
          <dd className="font-medium text-slate-900">{org?.organisationType ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Trust tier</dt>
          <dd className="font-medium text-slate-900">{org?.organisationTrustTier ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Access environment</dt>
          <dd className="font-medium text-slate-900">{org?.organisationAccessEnvironment ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Role templates</dt>
          <dd className="font-medium text-slate-900">{contract.roleTemplates.join(", ") || "—"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Work context</dt>
          <dd className="font-medium text-slate-900">{assignment?.contextType ?? contract.selectedContext ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Policy version</dt>
          <dd className="font-medium text-slate-900">{contract.policyMetadata.contractVersion}</dd>
        </div>
        <div>
          <dt className="text-slate-500">HSC employment</dt>
          <dd className="font-medium text-slate-900">{contract.publicSectorEmployment?.publicSectorEmploymentStatus ?? "—"}</dd>
        </div>
      </dl>
    </div>
  );
}
