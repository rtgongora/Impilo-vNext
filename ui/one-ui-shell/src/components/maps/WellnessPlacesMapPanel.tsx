"use client";

import { useState } from "react";
import { NdilaAddressSearchBox } from "@/components/ndila/NdilaAddressSearchBox";
import { NdilaNearbyServicesMap } from "@/components/ndila/NdilaNearbyServicesMap";
import { DEFAULT_MAP_CENTER } from "@/lib/ndila/geo-map-markers";
import type { NdilaCoordinate } from "@/lib/ndila/ndila-client";

export function WellnessPlacesMapPanel() {
  const [origin, setOrigin] = useState<NdilaCoordinate>(DEFAULT_MAP_CENTER);

  return (
    <section className="rounded-xl border border-teal-200 bg-teal-50/40 p-4" data-testid="wellness-places-map-panel">
      <p className="mb-2 text-sm font-medium text-teal-950">Routes &amp; places map</p>
      <p className="mb-3 text-xs text-teal-900/80">
        Wellness discover list pairs with Ndila nearby search for parks, routes, and fitness venues near you.
      </p>
      <NdilaAddressSearchBox
        country="ZWE"
        purposeOfUse="WELLNESS_DISCOVERY"
        onSelect={(result) => setOrigin({ latitude: result.latitude, longitude: result.longitude })}
      />
      <div className="mt-3">
        <NdilaNearbyServicesMap
          origin={origin}
          radiusMeters={20_000}
          entityTypes={["PARK", "FITNESS", "WELLNESS_VENUE", "FACILITY"]}
          height={280}
        />
      </div>
    </section>
  );
}
