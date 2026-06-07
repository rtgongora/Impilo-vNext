"use client";

import Link from "next/link";
import { Landmark, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMadiCentralBankMetrics } from "@/hooks/queries/useMadi";

export default function CentralBankPage() {
  const { data, isPending, isError } = useMadiCentralBankMetrics();

  return (
    <AppLayout>
      <PageShell title="Central Blood Bank" subtitle="National inventory and haemovigilance oversight" icon={<Landmark className="h-6 w-6" />}>
        {isPending && (
          <div className="flex items-center gap-2 text-gray-500">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading central metrics…
          </div>
        )}

        {isError && (
          <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
            Central metrics unavailable. Verify admin role and retry.
          </div>
        )}

        {data && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {[
              { label: "Total units", value: data.totalUnits },
              { label: "Available units", value: data.availableUnits },
              { label: "Open haemovigilance cases", value: data.openHaemovigilanceCases },
            ].map(({ label, value }) => (
              <div key={label} className="rounded-2xl border border-gray-200 bg-white p-5">
                <p className="text-xs uppercase tracking-wide text-gray-500">{label}</p>
                <p className="mt-2 text-3xl font-semibold text-gray-900">{String(value ?? 0)}</p>
              </div>
            ))}
          </div>
        )}

        <div className="mt-6 flex gap-3">
          <Link href="/madi/dashboard" className="text-sm text-rose-600 hover:underline">Facility dashboard</Link>
          <Link href="/madi/haemovigilance" className="text-sm text-rose-600 hover:underline">Haemovigilance</Link>
        </div>
      </PageShell>
    </AppLayout>
  );
}
