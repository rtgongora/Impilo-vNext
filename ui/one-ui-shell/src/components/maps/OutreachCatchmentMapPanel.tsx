"use client";

import { FacilitiesGeoMapPanel } from "./FacilitiesGeoMapPanel";

export function OutreachCatchmentMapPanel() {
  return (
    <FacilitiesGeoMapPanel
      title="Outreach catchment map"
      subtitle="Facility coverage for campaign geofence planning — Ndila-backed, no raw PII"
      size={100}
    />
  );
}
