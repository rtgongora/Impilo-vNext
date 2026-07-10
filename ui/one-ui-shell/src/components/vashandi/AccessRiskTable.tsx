"use client";

import { useState } from "react";
import { useResolveAccessRisk, useRevokeAccessForRisk, useScanAccessRisks } from "@/hooks/useVashandi";
import type { AccessRisk } from "@/lib/vashandi/types";
import { VashandiFriendlyBlockedState } from "./VashandiFriendlyBlockedState";

interface AccessRiskTableProps {
  risks: AccessRisk[];
  isLoading?: boolean;
}

export function AccessRiskTable({ risks, isLoading }: AccessRiskTableProps) {
  const scan = useScanAccessRisks();
  const resolve = useResolveAccessRisk();
  const revoke = useRevokeAccessForRisk();
  const [actionError, setActionError] = useState<string | null>(null);

  const act = (mutation: typeof resolve, riskId: string, label: string) => {
    setActionError(null);
    mutation.mutate(
      { riskId },
      {
        onSuccess: (response) => {
          if (response && response.success === false) {
            setActionError(response.friendlyMessage ?? `${label} was not accepted.`);
          }
        },
        onError: (e) => setActionError(e instanceof Error ? e.message : `${label} failed.`),
      },
    );
  };

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
      {actionError ? (
        <p className="rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger" data-testid="risk-action-error">
          {actionError}
        </p>
      ) : null}
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
                <th className="px-4 py-2 font-medium">Actions</th>
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
                  <td className="px-4 py-2">
                    {(risk.status ?? "open").toLowerCase() === "open" ? (
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          disabled={resolve.isPending || revoke.isPending}
                          onClick={() => act(resolve, risk.id, "Resolve")}
                          data-testid={`risk-resolve-${risk.id}`}
                          className="rounded border border-emerald-300 px-2 py-0.5 text-xs font-medium text-emerald-700 hover:bg-emerald-50 disabled:opacity-50"
                        >
                          Resolve
                        </button>
                        <button
                          type="button"
                          disabled={resolve.isPending || revoke.isPending}
                          onClick={() => act(revoke, risk.id, "Revoke access")}
                          data-testid={`risk-revoke-${risk.id}`}
                          className="rounded border border-rose-300 px-2 py-0.5 text-xs font-medium text-rose-700 hover:bg-rose-50 disabled:opacity-50"
                        >
                          Revoke access
                        </button>
                      </div>
                    ) : (
                      <span className="text-xs text-muted-foreground">
                        {risk.resolvedBy ? `by ${risk.resolvedBy}` : "—"}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
