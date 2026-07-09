"use client";

/**
 * Fleet & Asset Management
 *
 * Lists fleet assets (vehicles, drones, robots, smart lockers) with key
 * dispatch attributes. Asset detail and editing happen on /nhume/fleet/[id].
 */

import Link from "next/link";
import { Truck, Loader2, Plus } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useNhumeFleet } from "@/hooks/useNhume";
import { FleetAssetCreatePanel } from "@/components/nhume/FleetAssetCreatePanel";

const STATUS_TONE: Record<string, string> = {
  AVAILABLE: "bg-success-soft text-primary-hover border-success/25",
  IN_USE: "bg-info-soft text-primary-hover border-info/25",
  MAINTENANCE: "bg-warning-soft text-warning-foreground border-warning/35",
  OFFLINE: "bg-neutral-100 text-foreground border-border",
};

export default function NhumeFleetPage() {
  const { data, isPending } = useNhumeFleet();
  const searchParams = useSearchParams();
  const showCreate = searchParams.get("create") === "1";
  const rows = data?.data ?? [];
  return (
    <AppLayout>
      <PageShell
        title="Fleet & Assets"
        subtitle="Vehicles, drones, robots and smart-locker network"
        icon={<Truck className="h-6 w-6" />}
      >
        {!showCreate ? (
          <div className="flex justify-end mb-4">
            <Link href="/nhume/fleet?create=1" className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary">
              <Plus className="h-4 w-4" />
              Add asset
            </Link>
          </div>
        ) : (
          <FleetAssetCreatePanel />
        )}

        {isPending ? (
          <div className="rounded-2xl border border-border bg-card p-10 text-center text-muted-foreground">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" />
            Loading fleet…
          </div>
        ) : rows.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-border bg-background p-12 text-center">
            <Truck className="mx-auto h-10 w-10 text-muted-foreground" />
            <h3 className="mt-3 font-semibold text-foreground">No assets registered</h3>
            <p className="mt-1 text-sm text-muted-foreground">Add vehicles, drones, robots or lockers to start dispatching.</p>
          </div>
        ) : (
          <div className="rounded-2xl border border-border bg-card overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-background text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 text-left">Registration</th>
                  <th className="px-4 py-3 text-left">Type</th>
                  <th className="px-4 py-3 text-left">Mode</th>
                  <th className="px-4 py-3 text-left">Owner</th>
                  <th className="px-4 py-3 text-left">Availability</th>
                  <th className="px-4 py-3 text-left">Last seen</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {rows.map((r) => {
                  const id = String(r.asset_id ?? r.id ?? "");
                  const status = String(r.availability_status ?? "AVAILABLE").toUpperCase();
                  return (
                    <tr key={id} className="hover:bg-background">
                      <td className="px-4 py-3">
                        <Link href={`/nhume/fleet/${id}`} className="font-medium text-teal-700 hover:text-teal-900">
                          {String(r.registration_number ?? r.label ?? id)}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-foreground">{String(r.vehicle_type ?? "—")}</td>
                      <td className="px-4 py-3 text-foreground">{String(r.mode ?? "—")}</td>
                      <td className="px-4 py-3 text-foreground">{String(r.owner_org_ref ?? r.facility_base_ref ?? "—")}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] ${STATUS_TONE[status] ?? STATUS_TONE.AVAILABLE}`}>
                          {status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        {r.last_seen_at ? new Date(String(r.last_seen_at)).toLocaleString() : "—"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
