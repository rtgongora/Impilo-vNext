"use client";

import { useState } from "react";
import { NdilaAddressSearchBox } from "@/components/ndila/NdilaAddressSearchBox";
import { NdilaNearbyServicesMap } from "@/components/ndila/NdilaNearbyServicesMap";
import { DEFAULT_MAP_CENTER } from "@/lib/ndila/geo-map-markers";
import type { NdilaCoordinate } from "@/lib/ndila/ndila-client";
import { FacilitiesGeoMapPanel } from "./FacilitiesGeoMapPanel";

export function DiscoverFacilitiesMapPanel() {
  const [origin, setOrigin] = useState<NdilaCoordinate>(DEFAULT_MAP_CENTER);
  const [search, setSearch] = useState("");

  return (
    <div className="space-y-4" data-testid="discover-facilities-map-panel">
      <div className="grid gap-4 lg:grid-cols-2">
        <div>
          <p className="mb-2 text-xs font-medium text-slate-700">Search near a location</p>
          <NdilaAddressSearchBox
            country="ZWE"
            purposeOfUse="CITIZEN_DISCOVERY"
            onSelect={(result) => setOrigin({ latitude: result.latitude, longitude: result.longitude })}
          />
          <div className="mt-3">
            <NdilaNearbyServicesMap
              origin={origin}
              radiusMeters={25_000}
              entityTypes={["FACILITY", "PHARMACY", "CLINIC", "HOSPITAL"]}
              height={300}
            />
          </div>
        </div>
        <div>
          <label className="mb-2 block text-xs font-medium text-slate-700" htmlFor="discover-facility-search">
            Filter Tuso registry facilities
          </label>
          <input
            id="discover-facility-search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Facility name or type…"
            className="mb-3 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
          />
          <FacilitiesGeoMapPanel
            title="Registered facilities"
            subtitle="Governed facility registry coordinates"
            search={search || undefined}
            size={60}
          />
        </div>
      </div>
    </div>
  );
}
