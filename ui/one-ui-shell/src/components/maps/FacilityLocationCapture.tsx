"use client";

import { NdilaLocationPicker } from "@/components/ndila/NdilaLocationPicker";
import { NdilaMap } from "@/components/ndila/NdilaMap";
import type { NdilaCoordinate } from "@/lib/ndila/ndila-client";
import { DEFAULT_MAP_CENTER } from "@/lib/ndila/geo-map-markers";

interface FacilityLocationCaptureProps {
  value?: NdilaCoordinate | null;
  onChange?: (next: NdilaCoordinate | null) => void;
  label?: string;
  purposeOfUse?: string;
}

export function FacilityLocationCapture({
  value,
  onChange,
  label = "Facility coordinates (Tuso / Ndila)",
  purposeOfUse = "REGISTRY_INTAKE",
}: FacilityLocationCaptureProps) {
  const center = value ?? DEFAULT_MAP_CENTER;
  const markers = value
    ? [{ id: "facility-draft", label: "Selected location", latitude: value.latitude, longitude: value.longitude, markerType: "facility" }]
    : [];

  return (
    <section className="rounded-xl border border-emerald-200 bg-emerald-50/30 p-4" data-testid="facility-location-capture">
      <p className="mb-2 text-sm font-medium text-emerald-950">{label}</p>
      <p className="mb-3 text-xs text-emerald-900/80">
        Governed geocoding via Ndila — required for facility map surfaces and catchment intelligence.
      </p>
      <NdilaLocationPicker value={value} onChange={onChange} country="ZWE" purposeOfUse={purposeOfUse} />
      <div className="mt-4">
        <NdilaMap center={center} markers={markers} height={240} fitToMarkers={Boolean(value)} mode="FIELD" />
      </div>
    </section>
  );
}
