"use client";

import Link from "next/link";
import { Truck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function MadiLogisticsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Blood Logistics"
        subtitle="Dispatch blood products with cold-chain custody via Nhume"
        icon={<Truck className="h-6 w-6" />}
      >
        <div className="rounded-2xl border border-gray-200 bg-white p-6 space-y-4">
          <p className="text-sm text-gray-700">
            Blood unit dispatch uses Nhume last-mile logistics with chain-of-custody tracking.
            Create a delivery with delivery type <strong>BLOOD_PRODUCT</strong> and link clinical context
            from the issuing blood bank.
          </p>
          <div className="flex flex-wrap gap-3">
            <Link href="/nhume/deliveries/new" className="inline-flex rounded-xl bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700">
              New blood dispatch (Nhume)
            </Link>
            <Link href="/nhume/deliveries" className="inline-flex rounded-xl border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
              Track deliveries
            </Link>
            <Link href="/madi/blood-bank/issue" className="inline-flex rounded-xl border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-medium text-rose-800 hover:bg-rose-100">
              Issue from blood bank
            </Link>
          </div>
        </div>

        <div className="mt-6 rounded-xl border border-teal-200 bg-teal-50 p-4 text-sm text-teal-900">
          Cold-chain and custody exceptions surface on the{" "}
          <Link href="/nhume/dashboard" className="font-medium underline">Nhume operations dashboard</Link>.
        </div>
      </PageShell>
    </AppLayout>
  );
}
