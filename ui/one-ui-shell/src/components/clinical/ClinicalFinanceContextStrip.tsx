"use client";

import Link from "next/link";
import { Wallet } from "lucide-react";
import { useRoleGroup } from "@/hooks/useRoleGroup";

/**
 * Lightweight COSTA / MusheX / coverage entry — does not fetch fake balances.
 */
export function ClinicalFinanceContextStrip({ patientId }: { patientId: string }) {
  const { isFinance, isAdmin, isClinical } = useRoleGroup();
  if (!isFinance && !isAdmin && !isClinical) return null;

  return (
    <div
      data-patient-scope={patientId}
      className="flex flex-wrap items-center gap-2 rounded-lg border border-emerald-100 bg-success-soft/60 px-3 py-2 text-xs text-emerald-950"
    >
      <Wallet className="h-3.5 w-3.5 shrink-0 text-primary-hover" />
      <span className="font-medium">Coverage &amp; finance</span>
      <span className="text-primary-hover/90">COSTA / MusheX consoles stay on dedicated finance routes — no simulated balances here.</span>
      {/* This strip renders for clinical, finance AND admin. /coverage admits only the ADMIN
          group, so the other two were sent to a route that bounces them home. /ruvimbo is the
          role-aware resolver and never dead-ends. */}
      <Link href="/ruvimbo" className="font-semibold text-primary-hover underline-offset-2 hover:underline">
        Coverage workspace
      </Link>
      {/* Gated on isFinance alone, not (isFinance || isAdmin): /finance/workspace requires the
          FINANCE group, which already contains SYSTEM_ADMIN and FACILITY_ADMIN. The old
          condition additionally showed it to DEVELOPER, whom the route then refused. */}
      {isFinance && (
        <>
          <span className="text-primary-hover/70">·</span>
          <Link href="/finance/workspace" className="font-semibold text-primary-hover underline-offset-2 hover:underline">
            COSTA / MusheX hub
          </Link>
        </>
      )}
    </div>
  );
}
