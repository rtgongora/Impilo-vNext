"use client";

import Link from "next/link";
import { EnterpriseWorkspaceShell } from "@/components/enterprise/EnterpriseWorkspaceShell";

export default function EnterpriseChargeSheetPage() {
  return (
    <EnterpriseWorkspaceShell
      title="Charge sheet"
      subtitle="Clinical-facing billing capture stays inside encounters; this page explains how COSTA and MusheX split responsibilities."
    >
      <div className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-700 shadow-sm">
        <p>
          <strong className="text-slate-900">COSTA (costing-engine-service)</strong> computes tariffs, exemptions, bill
          lifecycle, and claims packing. <strong className="text-slate-900">MusheX (mushex-service)</strong> captures
          payments, wallets, settlement, and payer rails.
        </p>
        <p>
          Consumables decrements should post through inventory ledger events once charge approval workflows are bound —
          today, use the clinical encounter workspace and finance billing for patient-specific totals.
        </p>
        <div className="flex flex-wrap gap-3 pt-2">
          <Link href="/queue" className="font-semibold text-impilo-600 underline">
            Clinical queue
          </Link>
          <Link href="/finance/billing" className="font-semibold text-impilo-600 underline">
            Billing
          </Link>
          <Link href="/finance/claims" className="font-semibold text-impilo-600 underline">
            Claims
          </Link>
          <Link href="/inventory/movements" className="font-semibold text-impilo-600 underline">
            Inventory ledger
          </Link>
        </div>
      </div>
    </EnterpriseWorkspaceShell>
  );
}
