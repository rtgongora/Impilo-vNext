"use client";

import { useMemo } from "react";
import { GovernanceActionResult } from "./GovernanceActionResult";
import { useGdhcnReadiness } from "@/hooks/useAdminGovernance";
import { isActionResponse } from "@/lib/admin-governance/api/client";
import type { GdhcnReadinessDomainRow } from "@/lib/admin-governance/api/gdhcnReadinessApi";

function maturityLabel(value: string): string {
  return value.replaceAll("_", " ").toLowerCase();
}

/**
 * GDHCN readiness cockpit (Wave B B6). Read-only view of the per-tenant readiness assessment
 * across every domain. Data is real (tshepo-authz via the BFF); domains not yet assessed show
 * NOT_STARTED toward a PRODUCTION_NOT_READY target — the cockpit never overstates readiness.
 * Editing a domain is admin + step-up gated server-side and is surfaced in a follow-up.
 */
export function GdhcnReadinessBoard() {
  const { data, isLoading, isError } = useGdhcnReadiness();

  const domains = useMemo<GdhcnReadinessDomainRow[]>(() => {
    if (!data || isActionResponse(data)) return [];
    return data;
  }, [data]);

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading GDHCN readiness assessment…</p>;
  }

  if (data && isActionResponse(data)) {
    return <GovernanceActionResult result={data} />;
  }

  if (isError) {
    return (
      <div className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground">
        GDHCN readiness is unavailable — backend integration pending.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-border bg-muted/40 px-4 py-3 text-xs text-muted-foreground">
        Foundations only. &ldquo;Ready&rdquo; here means foundations exist and are proven —
        never certified. No domain may advance beyond <span className="font-medium">production not ready</span>{" "}
        until the deferred GDHCN work (VDHC, IPS/PHR, DSC operations, gateway onboarding) is built.
      </div>
      <div className="overflow-x-auto rounded-xl border border-border">
        <table className="w-full text-left text-sm">
          <thead className="bg-card text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <th className="px-4 py-3">Domain</th>
              <th className="px-4 py-3">Current → target</th>
              <th className="px-4 py-3">Owner</th>
              <th className="px-4 py-3">Evidence</th>
              <th className="px-4 py-3">Remediation</th>
              <th className="px-4 py-3">Component</th>
            </tr>
          </thead>
          <tbody>
            {domains.map((d) => (
              <tr key={d.domainKey} className="border-t border-border align-top">
                <td className="px-4 py-3 font-medium text-foreground">{d.title}</td>
                <td className="px-4 py-3 text-muted-foreground">
                  <span className="capitalize">{maturityLabel(d.currentMaturity)}</span>
                  <span className="px-1">→</span>
                  <span className="capitalize">{maturityLabel(d.targetMaturity)}</span>
                </td>
                <td className="px-4 py-3 text-muted-foreground">{d.owner ?? "—"}</td>
                <td className="px-4 py-3 text-muted-foreground">{d.evidenceStatus ?? "—"}</td>
                <td className="px-4 py-3 text-muted-foreground">{d.remediationStatus ?? "—"}</td>
                <td className="px-4 py-3 text-muted-foreground">{d.linkedComponent ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
