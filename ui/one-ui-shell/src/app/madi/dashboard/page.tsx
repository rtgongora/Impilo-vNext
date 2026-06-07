"use client";

import Link from "next/link";
import { LayoutDashboard, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useMadiDashboard } from "@/hooks/queries/useMadi";

export default function MadiDashboardPage() {
  const facility = useFacilityStore((s) => s.facility);
  const scope = facility?.id ? "facility" : "local";
  const { data, isPending, isError } = useMadiDashboard({
    scope,
    facilityId: facility?.id,
  });

  const tiles = [
    { key: "donors", label: "Donors" },
    { key: "activeDrives", label: "Active drives" },
    { key: "pendingOrders", label: "Pending orders" },
    { key: "openOrders", label: "Open orders" },
    { key: "availableUnits", label: "Available units" },
  ];

  return (
    <AppLayout>
      <PageShell title="Madi Dashboard" subtitle="Blood services metrics for your context" icon={<LayoutDashboard className="h-6 w-6" />}>
        {!facility?.id && (
          <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-xl p-3 mb-4">
            Showing tenant-wide local metrics. Select a facility for facility-scoped counts.
          </p>
        )}

        {isPending && (
          <div className="flex items-center gap-2 text-gray-500">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading dashboard…
          </div>
        )}

        {isError && (
          <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">Dashboard unavailable.</div>
        )}

        {data && (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
            {tiles.map(({ key, label }) =>
              data[key] !== undefined ? (
                <div key={key} className="rounded-2xl border border-gray-200 bg-white p-4">
                  <p className="text-xs text-gray-500 uppercase tracking-wide">{label}</p>
                  <p className="mt-2 text-2xl font-semibold text-gray-900">{String(data[key])}</p>
                </div>
              ) : null,
            )}
          </div>
        )}

        <div className="mt-6 flex flex-wrap gap-3 text-sm">
          <Link href="/madi/drives" className="text-rose-600 hover:underline">Drives</Link>
          <Link href="/madi/blood-bank/stock" className="text-rose-600 hover:underline">Stock</Link>
          <Link href="/madi/orders" className="text-rose-600 hover:underline">Orders</Link>
        </div>
      </PageShell>
    </AppLayout>
  );
}
