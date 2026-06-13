"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { AccessRequestBoard } from "@/components/administration-governance/AccessRequestBoard";
import { AdministrationGovernanceShell } from "@/components/administration-governance/AdministrationGovernanceShell";

export default function AccessRequestsPage() {
  return (
    <AdministrationGovernanceShell
      title="Access Requests & Approvals"
      subtitle="Pending verification, assignment, sandbox and partner access requests in your scope."
    >
      <div className="space-y-6">
        <Link href="/work/administration-governance" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" />
          Administration & Governance
        </Link>
        <AccessRequestBoard />
      </div>
    </AdministrationGovernanceShell>
  );
}
