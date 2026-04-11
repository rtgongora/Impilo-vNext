"use client";

import Link from "next/link";
import { AlertTriangle, ArrowLeft, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function MarketplaceOpsPage() {
  return (
    <AppLayout>
      <PageShell title="Marketplace ops" subtitle="Operational review queues for commerce workflows.">
        <div className="mb-4">
          <Link
            href="/marketplace"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" /> Back to marketplace
          </Link>
        </div>

        <div className="rounded-xl border border-amber-200 bg-amber-50 p-5">
          <div className="flex items-start gap-3">
            <AlertTriangle className="h-5 w-5 text-amber-700 mt-0.5" />
            <div>
              <p className="font-medium text-amber-950">Unsupported (awaiting contract)</p>
              <p className="mt-1 text-sm text-amber-900/90">
                Ops queues (stuck routes, reviews, audit) are not exposed through Experience BFF under the canonical
                commerce family yet. This page stays explicit to avoid any fake review queues.
              </p>
              <div className="mt-3 flex items-center gap-2 text-sm">
                <ShieldAlert className="h-4 w-4 text-amber-800" />
                <Link href="/finance/commerce-integrations" className="font-medium text-indigo-800 hover:underline">
                  See integration map
                </Link>
              </div>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}

