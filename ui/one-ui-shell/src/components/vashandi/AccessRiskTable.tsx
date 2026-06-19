"use client";

import { useScanAccessRisks } from "@/hooks/useVashandi";
import type { AccessRisk } from "@/lib/vashandi/types";
import { VashandiFriendlyBlockedState } from "./VashandiFriendlyBlockedState";

interface AccessRiskTableProps {
  risks: AccessRisk[];
  isLoading?: boolean;
}

export function AccessRiskTable({ risks, isLoading }: AccessRiskTableProps) {
  const scan = useScanAccessRisks();

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading access risks…</p>;
  }

  if (scan.data && !scan.data.success) {
    return (
      <VashandiFriendlyBlockedState
        state={scan.data.blockedReason}
        title={scan.data.friendlyTitle ?? "Scan blocked"}
        description={scan.data.friendlyMessage ?? scan.data.integrationMessage}
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Workforce access risks detected in your scope.</p>
        <button
          type="button"
          onClick={() => scan.mutate(undefined)}
          disabled={scan.isPending}
          className="rounded-lg border border-border px-3 py-1.5 text-sm font-medium hover:bg-muted disabled:opacity-50"
        >
          {scan.isPending ? "Scanning…" : "Scan now"}
        </button>
      </div>
      {risks.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-muted/30 p-6 text-sm text-muted-foreground">
          No open access risks returned. Run a scan to detect stale or orphaned access.
        </div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="min-w-full text-sm">
            <thead className="bg-muted/50 text-left">
              <tr>
                <th className="px-4 py-2 font-medium">Risk type</th>
                <th className="px-4 py-2 font-medium">Severity</th>
                <th className="px-4 py-2 font-medium">Status</th>
                <th className="px-4 py-2 font-medium">Detected</th>
                <th className="px-4 py-2 font-medium">Profile</th>
              </tr>
            </thead>
            <tbody>
              {risks.map((risk) => (
                <tr key={risk.id} className="border-t border-border">
                  <td className="px-4 py-2">{risk.riskType}</td>
                  <td className="px-4 py-2 capitalize">{risk.severity}</td>
                  <td className="px-4 py-2 capitalize">{risk.status}</td>
                  <td className="px-4 py-2 text-muted-foreground">{risk.detectedAt}</td>
                  <td className="px-4 py-2 font-mono text-xs">{risk.workforceProfileId}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
