"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { AdministrationGovernanceShell } from "@/components/administration-governance/AdministrationGovernanceShell";
import { OnboardOptionGrid } from "@/components/administration-governance/OnboardOptionGrid";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { ONBOARD_ACTOR_OPTIONS } from "@/lib/administration-governance";

export default function OnboardNewActorPage() {
  const { contract } = useSessionExperienceContract();

  return (
    <AdministrationGovernanceShell
      title="Onboard New Actor"
      subtitle="Choose who you are onboarding. Options are filtered by your management authority."
      requireGovernanceEntry={false}
    >
      <div className="space-y-6">
        <Link href="/work/administration-governance" className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900">
          <ArrowLeft className="h-4 w-4" />
          Administration & Governance
        </Link>
        <p className="text-sm text-slate-600">
          Each flow is a guided wizard — not a single monster form. You only see flows your organisation and role may initiate.
        </p>
        {contract && <OnboardOptionGrid contract={contract} options={ONBOARD_ACTOR_OPTIONS} />}
      </div>
    </AdministrationGovernanceShell>
  );
}
