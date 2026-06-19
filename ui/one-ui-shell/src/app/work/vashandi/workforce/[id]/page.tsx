"use client";

import { useParams } from "next/navigation";
import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { WorkforceStatusBadge } from "@/components/vashandi/WorkforceStatusBadge";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import { useWorkforceProfile, isVashandiDegraded } from "@/hooks/useVashandi";
import { extractSingleItem } from "@/lib/vashandi/api/client";
import type { WorkforceProfile } from "@/lib/vashandi/types";

export default function Page() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const { data, isLoading } = useWorkforceProfile(id);
  const profile = data ? extractSingleItem<WorkforceProfile>(data) : undefined;

  return (
    <VashandiShell title="Workforce Profile" subtitle="Operational workforce profile detail">
      {isLoading ? <p className="text-sm text-muted-foreground">Loading profile…</p> : null}
      {data && isVashandiDegraded(data) ? (
        <VashandiFriendlyBlockedState
          title={data.friendlyTitle ?? "Profile unavailable"}
          description={data.friendlyMessage ?? data.integrationMessage}
        />
      ) : null}
      {data?.success && profile ? (
        <div className="rounded-2xl border border-border bg-card p-5 space-y-3">
          <div className="flex items-center justify-between gap-3">
            <h3 className="font-medium text-foreground">{profile.profession ?? "Workforce profile"}</h3>
            <WorkforceStatusBadge status={profile.currentStatus} />
          </div>
          <dl className="grid gap-2 text-sm sm:grid-cols-2">
            <div><dt className="text-muted-foreground">Profile ID</dt><dd className="font-mono text-xs">{profile.id}</dd></div>
            <div><dt className="text-muted-foreground">Health ID</dt><dd>{profile.healthId ?? "—"}</dd></div>
            <div><dt className="text-muted-foreground">Provider worker</dt><dd>{profile.providerWorkerId ?? "—"}</dd></div>
            <div><dt className="text-muted-foreground">Cadre</dt><dd>{profile.cadre ?? "—"}</dd></div>
            <div><dt className="text-muted-foreground">Access risk</dt><dd>{profile.accessRiskStatus ?? "clear"}</dd></div>
          </dl>
        </div>
      ) : null}
      {data && !data.success && !isVashandiDegraded(data) ? (
        <VashandiFriendlyBlockedState state={data.blockedReason} title={data.friendlyTitle} description={data.friendlyMessage} />
      ) : null}
    </VashandiShell>
  );
}
