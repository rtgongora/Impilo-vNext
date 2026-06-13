"use client";

/**
 * Identity Assurance Banner — Health OS §10, §11
 *
 * "Every temporary ID shall display: its current assurance state, the reason
 *  for that state, the permissions currently available, the requirements for
 *  upgrade, the eligible upgrade pathways, the next best step."
 *
 * "Temporary identity should feel like a guided journey, not a dead end."
 */

import Link from "next/link";
import { ShieldAlert, ArrowUpRight, CheckCircle2 } from "lucide-react";
import { useAssuranceStatus, type AssuranceStatus } from "@/hooks/queries/useIdentity";

const STATE_LABELS: Record<string, { label: string; color: string }> = {
  SELF_REGISTERED: { label: "Self-Registered", color: "border-amber-300 bg-warning-soft text-warning-foreground" },
  ASSISTED_REGISTRATION: { label: "Assisted Registration", color: "border-primary/25 bg-primary-soft text-impilo-800" },
  FACILITY_CONFIRMED: { label: "Facility Confirmed", color: "border-green-300 bg-green-50 text-green-900" },
  REGISTRY_MATCHED: { label: "Registry Matched", color: "border-green-400 bg-green-50 text-green-900" },
  COUNCIL_VALIDATED: { label: "Council Validated", color: "border-emerald-400 bg-success-soft text-primary-hover" },
  FULLY_VERIFIED: { label: "Fully Verified", color: "border-emerald-500 bg-success-soft text-primary-hover" },
};

export function IdentityAssuranceBanner() {
  const { data: assuranceData, isLoading } = useAssuranceStatus();
  const status: AssuranceStatus | null = assuranceData?.data ?? null;

  if (isLoading) return null;
  if (!status) return null;
  if (status.assuranceState === "FULLY_VERIFIED") return null; // No banner needed

  const stateInfo = STATE_LABELS[status.assuranceState] ?? STATE_LABELS.SELF_REGISTERED;

  return (
    <div className={`rounded-lg border p-4 mb-4 ${stateInfo.color}`}>
      <div className="flex items-start gap-3">
        <ShieldAlert className="h-5 w-5 mt-0.5 shrink-0" />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <h3 className="text-sm font-semibold">Identity: {stateInfo.label}</h3>
          </div>
          <p className="text-xs mb-2">{status.reason}</p>

          {status.permissionsAvailable.length > 0 && (
            <div className="mb-2">
              <p className="text-[10px] uppercase tracking-wide font-medium mb-1">Available permissions</p>
              <div className="flex gap-1 flex-wrap">
                {status.permissionsAvailable.map((p) => (
                  <span key={p} className="inline-flex items-center gap-1 rounded-full bg-card/60 px-2 py-0.5 text-[10px]">
                    <CheckCircle2 className="h-2.5 w-2.5" /> {p}
                  </span>
                ))}
              </div>
            </div>
          )}

          {status.upgradeRequirements.length > 0 && (
            <div className="mb-2">
              <p className="text-[10px] uppercase tracking-wide font-medium mb-1">To upgrade, you need</p>
              <ul className="text-xs space-y-0.5">
                {status.upgradeRequirements.map((r) => (
                  <li key={r} className="flex items-center gap-1">
                    <span className="h-1 w-1 rounded-full bg-current shrink-0" /> {r}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {status.eligibleUpgradePathways.length > 0 && (
            <div className="mb-2">
              <p className="text-[10px] uppercase tracking-wide font-medium mb-1">Upgrade pathways</p>
              <div className="flex gap-2 flex-wrap">
                {status.eligibleUpgradePathways.map((path) => (
                  <Link
                    key={path.id}
                    href={path.href}
                    className="inline-flex items-center gap-1 rounded-lg border border-current/20 bg-card/40 px-2.5 py-1 text-xs font-medium hover:bg-card/60 transition"
                  >
                    {path.label} <ArrowUpRight className="h-3 w-3" />
                  </Link>
                ))}
              </div>
            </div>
          )}

          <p className="text-xs font-medium mt-2">
            Next step: {status.nextBestStep}
          </p>
        </div>
      </div>
    </div>
  );
}
