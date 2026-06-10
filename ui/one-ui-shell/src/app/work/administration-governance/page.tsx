"use client";

import Link from "next/link";
import { ArrowLeft, Search } from "lucide-react";
import { AdministrationGovernanceShell } from "@/components/administration-governance/AdministrationGovernanceShell";
import { AuthorityCard } from "@/components/administration-governance/AuthorityCard";
import { ManagementTileGrid } from "@/components/administration-governance/ManagementTileGrid";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { MANAGEMENT_TILES, QUICK_ACTION_TILES } from "@/lib/administration-governance";

export default function AdministrationGovernancePage() {
  const { contract } = useSessionExperienceContract();

  return (
    <AdministrationGovernanceShell
      title="Administration & Governance"
      subtitle="Organisation-scoped management guided by your Session Experience Contract — not generic user admin."
    >
      <div className="space-y-8">
        <Link href="/provider-workspace" className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900">
          <ArrowLeft className="h-4 w-4" />
          Back to Work
        </Link>

        <div className="rounded-xl border border-indigo-100 bg-indigo-50/70 px-4 py-3 text-sm text-indigo-950">
          <strong>Trust doctrine:</strong> Personal (Health ID) → Professional (Provider ID) → Work (active assignment).
          Management tools appear only for your organisation type, role and OPA policy — never a global superuser screen.
        </div>

        {contract && <AuthorityCard contract={contract} />}

        <section>
          <h3 className="text-sm font-semibold text-slate-900">Quick actions</h3>
          <div className="mt-3">
            {contract && <ManagementTileGrid contract={contract} tiles={QUICK_ACTION_TILES} />}
          </div>
          <Link
            href="/work/administration-governance/access-review"
            className="mt-3 inline-flex items-center gap-1 text-sm font-medium text-indigo-700 hover:underline"
          >
            <Search className="h-4 w-4" />
            View My Scope
          </Link>
        </section>

        <section>
          <h3 className="text-sm font-semibold text-slate-900">Management workspaces</h3>
          <p className="mt-1 text-xs text-slate-500">Tiles render only when your contract lists the matching management workspace.</p>
          <div className="mt-4">{contract && <ManagementTileGrid contract={contract} tiles={MANAGEMENT_TILES} />}</div>
        </section>
      </div>
    </AdministrationGovernanceShell>
  );
}
