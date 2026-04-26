"use client";

import Link from "next/link";
import { EnterpriseWorkspaceShell } from "@/components/enterprise/EnterpriseWorkspaceShell";

export default function EnterpriseWarehousingPage() {
  return (
    <EnterpriseWorkspaceShell
      title="Warehousing & distribution"
      subtitle="Dispatch, proof of delivery, and warehouse master data will bind to Integration Hub and Msika Flow once read APIs are exposed on the BFF."
    >
      <div className="rounded-2xl border border-slate-200 bg-slate-50 p-6 text-sm text-slate-700">
        <p className="font-medium text-slate-900">No warehouse execution API is wired here yet</p>
        <p className="mt-2">
          Use inventory movements and procurement GRN flows for now. When `integration-hub-service` exposes governed
          dispatch lists to the BFF, this page will render live rows instead of placeholders.
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          <Link href="/inventory/movements" className="text-sm font-semibold text-impilo-600 underline">
            Inventory movements
          </Link>
          <Link href="/erp/procurement" className="text-sm font-semibold text-impilo-600 underline">
            Procurement / GRN
          </Link>
          <Link href="/marketplace/vendor" className="text-sm font-semibold text-impilo-600 underline">
            Vendor fulfilment
          </Link>
        </div>
      </div>
    </EnterpriseWorkspaceShell>
  );
}
