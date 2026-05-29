"use client";

/**
 * Nhume Fleet Tracking Map
 *
 * Visualises fleet positions and active deliveries. The actual map renderer is
 * intentionally provider-agnostic: the page asks Ndila (via the backend) which
 * provider to use and renders an accessible HTML fallback with markers, route
 * polylines and ETAs as a list. When the production Ndila WebGL component is
 * available it can drop into <NhumeMapCanvas/> without changing this page.
 */

import { Map as MapIcon, Loader2, Filter } from "lucide-react";
import { useMemo, useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NhumeStatusChip } from "@/components/nhume/NhumeStatusChip";
import { UnifiedLogisticsMapPanel } from "@/components/operations/UnifiedLogisticsMapPanel";
import { useNhumeFleet, useNhumeDeliveries, useNhumeMapToken, useNhumeZones } from "@/hooks/useNhume";

export default function NhumeMapPage() {
  const map = useNhumeMapToken();
  const fleet = useNhumeFleet();
  const zones = useNhumeZones();
  const deliveries = useNhumeDeliveries({ status: "IN_TRANSIT", size: 50 });

  const [showVehicles, setShowVehicles] = useState(true);
  const [showDeliveries, setShowDeliveries] = useState(true);
  const [showZones, setShowZones] = useState(true);

  const assets = useMemo(() => fleet.data?.data ?? [], [fleet.data]);
  const inTransit = useMemo(() => deliveries.data?.data ?? [], [deliveries.data]);
  const zoneRows = useMemo(() => zones.data?.data ?? [], [zones.data]);

  return (
    <AppLayout>
      <PageShell
        title="Fleet Tracking Map"
        subtitle="Vehicles, drones, routes and dispatch zones through Ndila"
        icon={<MapIcon className="h-6 w-6" />}
      >
        <div className="rounded-2xl border border-teal-100 bg-teal-50 p-4 text-sm text-teal-900 flex items-center justify-between mb-4">
          <div>
            <strong>Map provider:</strong> {map.data?.provider ?? "Ndila"}
            {map.data?.simulated && (
              <span className="ml-2 inline-flex items-center rounded-full bg-amber-100 px-2 py-0.5 text-[11px] text-amber-800">
                Simulation
              </span>
            )}
          </div>
          <div className="text-xs text-teal-800">
            Map rendering metadata, routing and ETAs are provided by services/ndila-service.
          </div>
        </div>

        <div className="flex flex-wrap gap-3 mb-4 items-center">
          <Filter className="h-4 w-4 text-gray-500" />
          <Toggle label="Vehicles" checked={showVehicles} onChange={setShowVehicles} />
          <Toggle label="Active deliveries" checked={showDeliveries} onChange={setShowDeliveries} />
          <Toggle label="Dispatch zones" checked={showZones} onChange={setShowZones} />
        </div>

        {(fleet.isPending || deliveries.isPending) && (
          <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" />
            Loading fleet & deliveries…
          </div>
        )}

        {!(fleet.isPending || deliveries.isPending) && (
          <>
            <UnifiedLogisticsMapPanel
              title="Nhume + dispatch logistics map"
              subtitle="Shared Ndila tile layer with dispatch fleet overlay"
              includeNhume
              includeDispatch
              nhumeDeliveryStatus={showDeliveries ? "IN_TRANSIT" : undefined}
            />
            <div className="mt-6 grid grid-cols-1 lg:grid-cols-2 gap-4">
            {showVehicles && <Card title={`Vehicles (${assets.length})`} rows={assets} kind="asset" />}
            {showDeliveries && <Card title={`In transit (${inTransit.length})`} rows={inTransit as unknown as Array<Record<string, unknown>>} kind="delivery" />}
            {showZones && <Card title={`Dispatch zones (${zoneRows.length})`} rows={zoneRows} kind="zone" />}
            </div>
          </>
        )}

        <p className="mt-4 text-xs text-gray-500">
          Full WebGL Ndila client can replace the tile preview when <code>NEXT_PUBLIC_NDILA_MAP_TOKEN</code> is set.
          Lists below remain available for low-connectivity dispatch.
        </p>
      </PageShell>
    </AppLayout>
  );
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="inline-flex items-center gap-2 text-sm cursor-pointer">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      {label}
    </label>
  );
}

function Card({ title, rows, kind }: { title: string; rows: Array<Record<string, unknown>>; kind: "asset" | "delivery" | "zone" }) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white">
      <div className="border-b border-gray-100 px-5 py-3"><h3 className="font-semibold text-gray-900">{title}</h3></div>
      {rows.length === 0 ? (
        <div className="px-5 py-6 text-sm text-gray-500 text-center">No data.</div>
      ) : (
        <ul className="divide-y divide-gray-100">
          {rows.slice(0, 20).map((r, idx) => (
            <li key={idx} className="px-5 py-3 text-sm">
              {kind === "asset" && (
                <Link href={`/nhume/fleet/${String(r.asset_id ?? r.id ?? "")}`} className="text-teal-700 hover:text-teal-900 font-medium">
                  {String(r.registration ?? r.label ?? r.asset_id ?? "asset")}
                </Link>
              )}
              {kind === "delivery" && (
                <div className="flex items-center justify-between">
                  <Link href={`/nhume/deliveries/${String(r.delivery_id ?? "")}`} className="text-teal-700 hover:text-teal-900 font-medium">
                    {String(r.reference ?? r.delivery_id ?? "delivery")}
                  </Link>
                  <NhumeStatusChip status={String(r.status ?? "")} />
                </div>
              )}
              {kind === "zone" && (
                <span className="text-gray-800">{String(r.name ?? r.code ?? "zone")}</span>
              )}
              <div className="text-xs text-gray-500 mt-1">
                {kind === "asset" && r.vehicle_type ? `Type ${String(r.vehicle_type)}` : null}
                {kind === "delivery" && r.destination_label ? `→ ${String(r.destination_label)}` : null}
                {kind === "zone" && r.jurisdiction_ref ? `Jurisdiction ${String(r.jurisdiction_ref)}` : null}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
