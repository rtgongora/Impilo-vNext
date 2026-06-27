"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { GdhcnReadinessBoard } from "@/components/administration-governance/GdhcnReadinessBoard";
import { AdministrationGovernanceShell } from "@/components/administration-governance/AdministrationGovernanceShell";

export default function GdhcnReadinessPage() {
  return (
    <AdministrationGovernanceShell
      title="GDHCN Readiness"
      subtitle="Honest readiness of the Tshepo trust plane towards GDHCN participation, by domain."
    >
      <div className="space-y-6">
        <Link
          href="/work/administration-governance"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" />
          Administration & Governance
        </Link>
        <GdhcnReadinessBoard />
      </div>
    </AdministrationGovernanceShell>
  );
}
