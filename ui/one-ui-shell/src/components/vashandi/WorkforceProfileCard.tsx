"use client";

import Link from "next/link";
import type { WorkforceProfile } from "@/lib/vashandi/types";
import { WorkforceStatusBadge } from "./WorkforceStatusBadge";

interface WorkforceProfileCardProps {
  profile: WorkforceProfile;
}

export function WorkforceProfileCard({ profile }: WorkforceProfileCardProps) {
  return (
    <Link
      href={`/work/vashandi/workforce/${profile.id}`}
      className="block rounded-2xl border border-border bg-card p-4 shadow-sm transition hover:border-indigo-300"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-medium text-foreground">
            {profile.profession ?? profile.workerType ?? "Workforce profile"}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            {profile.providerWorkerId ?? profile.healthId ?? profile.id}
          </p>
          {profile.cadre ? <p className="mt-1 text-xs text-muted-foreground">Cadre: {profile.cadre}</p> : null}
        </div>
        <WorkforceStatusBadge status={profile.currentStatus} />
      </div>
      {profile.accessRiskStatus && profile.accessRiskStatus !== "clear" ? (
        <p className="mt-3 text-xs font-medium text-warning-foreground">Access risk: {profile.accessRiskStatus}</p>
      ) : null}
    </Link>
  );
}
