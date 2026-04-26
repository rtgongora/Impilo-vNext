"use client";

import Link from "next/link";
import { EnterpriseWorkspaceShell } from "@/components/enterprise/EnterpriseWorkspaceShell";

export default function EnterpriseFleetPage() {
  return (
    <EnterpriseWorkspaceShell
      title="Fleet & logistics"
      subtitle="Vehicle assignment, routes, and cold-chain telemetry require a dedicated logistics bounded context."
    >
      <div className="rounded-2xl border border-slate-200 bg-slate-50 p-6 text-sm text-slate-700">
        <p className="font-medium text-slate-900">Fleet APIs are not yet exposed through the Experience BFF</p>
        <p className="mt-2">
          This surface intentionally stays empty to avoid misleading operators. Hook Msika Flow fulfilment events and
          national dispatch contracts here when available.
        </p>
        <Link href="/enterprise" className="mt-4 inline-block text-sm font-semibold text-impilo-600 underline">
          Back to enterprise dashboard
        </Link>
      </div>
    </EnterpriseWorkspaceShell>
  );
}
