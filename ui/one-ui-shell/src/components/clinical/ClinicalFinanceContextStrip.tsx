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
      className="flex flex-wrap items-center gap-2 rounded-lg border border-emerald-100 bg-emerald-50/60 px-3 py-2 text-xs text-emerald-950"
    >
      <Wallet className="h-3.5 w-3.5 shrink-0 text-emerald-700" />
      <span className="font-medium">Coverage &amp; finance</span>
      <span className="text-emerald-800/90">COSTA / MusheX consoles stay on dedicated finance routes — no simulated balances here.</span>
      <Link href="/coverage" className="font-semibold text-emerald-800 underline-offset-2 hover:underline">
        Coverage workspace
      </Link>
      {(isFinance || isAdmin) && (
        <>
          <span className="text-emerald-700/70">·</span>
          <Link href="/finance/workspace" className="font-semibold text-emerald-800 underline-offset-2 hover:underline">
            COSTA / MusheX hub
          </Link>
        </>
      )}
    </div>
  );
}
