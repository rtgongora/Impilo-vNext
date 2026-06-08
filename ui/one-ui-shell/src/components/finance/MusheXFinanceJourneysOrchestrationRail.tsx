"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { CreditCard, FileText, Shield, Wallet } from "lucide-react";
import { useServiceAccessDecisionsList } from "@/hooks/queries/useServiceAccessDecisions";

export function MusheXFinanceJourneysOrchestrationRail() {
  const searchParams = useSearchParams();
  const encounterId = searchParams.get("encounterId");
  const decisionsQ = useServiceAccessDecisionsList(encounterId);
  const decisions = decisionsQ.data ?? [];
  const exemptionCount = decisions.filter((row) =>
    String(row.access_status ?? "").toUpperCase().includes("EXEMPT"),
  ).length;
  const deferredCount = decisions.filter((row) =>
    String(row.access_status ?? "").toUpperCase().includes("DEFERRED"),
  ).length;

  const handoff = (href: string) => {
    const q = searchParams.toString();
    return q ? `${href}?${q}` : href;
  };

  return (
    <section
      className="rounded-2xl border border-indigo-200 bg-indigo-50/80 p-4"
      data-testid="mushex-finance-journeys-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-indigo-900">
            <CreditCard className="h-4 w-4" />
            MusheX finance journeys
          </p>
          <p className="mt-1 text-xs text-indigo-800" data-testid="mushex-finance-kpi-strip">
            {decisionsQ.isLoading
              ? "Loading service-access decisions…"
              : encounterId
                ? `${decisions.length} access decision(s) · ${exemptionCount} exemption · ${deferredCount} deferred`
                : "Wallet, claims, and exemption flows — open with encounter context for scoped decisions"}
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          <Link
            href={handoff("/wallet")}
            className="inline-flex items-center gap-1 rounded-lg border border-indigo-200 bg-white px-2.5 py-1.5 font-medium text-indigo-900 hover:border-indigo-300"
          >
            <Wallet className="h-3.5 w-3.5" />
            Wallet
          </Link>
          <Link
            href={handoff("/finance/claims")}
            className="inline-flex items-center gap-1 rounded-lg border border-indigo-200 bg-white px-2.5 py-1.5 font-medium text-indigo-900 hover:border-indigo-300"
          >
            <FileText className="h-3.5 w-3.5" />
            Claims
          </Link>
          <Link
            href={handoff("/finance/service-access")}
            className="inline-flex items-center gap-1 rounded-lg border border-indigo-200 bg-white px-2.5 py-1.5 font-medium text-indigo-900 hover:border-indigo-300"
          >
            <Shield className="h-3.5 w-3.5" />
            Exemptions & deferred
          </Link>
        </div>
      </div>
    </section>
  );
}
