"use client";

import type { SessionExperienceContract } from "@/lib/trust";

interface AuthorityCardProps {
  contract: SessionExperienceContract;
}

export function AuthorityCard({ contract }: AuthorityCardProps) {
  const org = contract.organisation;
  const assignment = contract.activeWorkAssignments[0];

  return (
    <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
      <h3 className="text-sm font-semibold text-foreground">Your current authority</h3>
      <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-muted-foreground">Organisation</dt>
          <dd className="font-medium text-foreground">{org?.organisationName ?? org?.organisationType ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Organisation type</dt>
          <dd className="font-medium text-foreground">{org?.organisationType ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Trust tier</dt>
          <dd className="font-medium text-foreground">{org?.organisationTrustTier ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Access environment</dt>
          <dd className="font-medium text-foreground">{org?.organisationAccessEnvironment ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Role templates</dt>
          <dd className="font-medium text-foreground">{contract.roleTemplates.join(", ") || "—"}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Work context</dt>
          <dd className="font-medium text-foreground">{assignment?.contextType ?? contract.selectedContext ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Policy version</dt>
          <dd className="font-medium text-foreground">{contract.policyMetadata.contractVersion}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">HSC employment</dt>
          <dd className="font-medium text-foreground">{contract.publicSectorEmployment?.publicSectorEmploymentStatus ?? "—"}</dd>
        </div>
      </dl>
    </div>
  );
}
