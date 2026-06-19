"use client";

import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { AccessRiskTable } from "@/components/vashandi/AccessRiskTable";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import { useAccessRisks, isVashandiDegraded } from "@/hooks/useVashandi";

export default function Page() {
  const { data, isLoading } = useAccessRisks({ status: "open" });

  return (
    <VashandiShell title="Access Review" subtitle="Workforce access risk detection and remediation">
      {data?.response && isVashandiDegraded(data.response) ? (
        <VashandiFriendlyBlockedState
          title={data.response.friendlyTitle ?? "Service degraded"}
          description={data.response.friendlyMessage ?? data.response.integrationMessage}
        />
      ) : null}
      <AccessRiskTable risks={data?.items ?? []} isLoading={isLoading} />
    </VashandiShell>
  );
}
