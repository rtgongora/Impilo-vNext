"use client";

import Link from "next/link";
import { LayoutDashboard, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useMadiDashboard, useMadiThirtyDayForecast } from "@/hooks/queries/useMadi";

export default function MadiDashboardPage() {
  const facility = useFacilityStore((s) => s.facility);
  const scope = facility?.id ? "facility" : "local";
  const { data, isPending, isError } = useMadiDashboard({
    scope,
    facilityId: facility?.id,
  });
  const forecast = useMadiThirtyDayForecast(facility?.id);

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

        <section className="mt-8 rounded-2xl border border-gray-200 bg-white p-5">
          <div className="flex items-center justify-between gap-3 mb-4">
            <h2 className="text-sm font-semibold text-gray-900">30-day demand forecast</h2>
            {forecast.isPending && <Loader2 className="h-4 w-4 animate-spin text-gray-400" />}
          </div>
          <p className="text-xs text-gray-500 mb-2">
            Baseline demand uses the last 30 days of orders; epidemiology signals from surveillance may tune projected demand.
          </p>
          {forecast.data?.epidemiologyMultiplier && forecast.data.epidemiologyMultiplier > 1 && (
            <div className="mb-4 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
              Epidemiology multiplier: <strong>{forecast.data.epidemiologyMultiplier.toFixed(2)}×</strong>
              {(forecast.data.epidemiologySignals ?? []).length > 0 && (
                <span className="ml-2">
                  ({forecast.data.epidemiologySignals!.map((s) => String(s.syndromeCode ?? s.status ?? "")).filter(Boolean).join(", ")})
                </span>
              )}
            </div>
          )}
          {forecast.isError && (
            <p className="text-sm text-rose-700">Forecast unavailable for this context.</p>
          )}
          {forecast.data?.rows && forecast.data.rows.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead>
                  <tr className="text-left text-xs uppercase tracking-wide text-gray-500 border-b">
                    <th className="py-2 pr-4">Group</th>
                    <th className="py-2 pr-4">Component</th>
                    <th className="py-2 pr-4">Available</th>
                    <th className="py-2 pr-4">Baseline</th>
                    <th className="py-2 pr-4">Tuned demand</th>
                    <th className="py-2 pr-4">Supply (30d)</th>
                    <th className="py-2 pr-4">Net gap</th>
                    <th className="py-2">Risk</th>
                  </tr>
                </thead>
                <tbody>
                  {forecast.data.rows.map((row) => (
                    <tr key={`${row.bloodGroup}-${row.componentType}`} className="border-b border-gray-100">
                      <td className="py-2 pr-4 font-medium">{row.bloodGroup}</td>
                      <td className="py-2 pr-4">{String(row.componentType ?? "").replace(/_/g, " ")}</td>
                      <td className="py-2 pr-4">{row.currentAvailable ?? 0}</td>
                      <td className="py-2 pr-4 text-gray-500">{row.baselineDemand30d ?? row.projectedDemand30d ?? 0}</td>
                      <td className="py-2 pr-4 font-medium">{row.projectedDemand30d ?? 0}</td>
                      <td className="py-2 pr-4">{row.projectedSupply30d ?? 0}</td>
                      <td className={`py-2 pr-4 ${(row.netGap ?? 0) < 0 ? "text-rose-700 font-medium" : "text-green-700"}`}>
                        {row.netGap ?? 0}
                      </td>
                      <td className="py-2">
                        <span className={`rounded-full px-2 py-0.5 text-xs ${
                          row.risk === "HIGH"
                            ? "bg-rose-100 text-rose-800"
                            : row.risk === "MEDIUM"
                              ? "bg-amber-100 text-amber-800"
                              : "bg-green-100 text-green-800"
                        }`}>
                          {row.risk ?? "LOW"}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : !forecast.isPending && !forecast.isError ? (
            <p className="text-sm text-gray-500">No forecast rows yet — orders and stock movements will populate this view.</p>
          ) : null}
        </section>

        <div className="mt-6 flex flex-wrap gap-3 text-sm">
          <Link href="/madi/drives" className="text-rose-600 hover:underline">Drives</Link>
          <Link href="/madi/blood-bank/stock" className="text-rose-600 hover:underline">Stock</Link>
          <Link href="/madi/orders" className="text-rose-600 hover:underline">Orders</Link>
          <Link href="/madi/central-bank" className="text-rose-600 hover:underline">Central bank</Link>
        </div>
      </PageShell>
    </AppLayout>
  );
}
