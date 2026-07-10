"use client";

import { useState } from "react";
import { LocateFixed } from "lucide-react";
import { NdilaAddressSearchBox } from "@/components/ndila/NdilaAddressSearchBox";
import { NdilaNearbyServicesMap } from "@/components/ndila/NdilaNearbyServicesMap";
import { DEFAULT_MAP_CENTER } from "@/lib/ndila/geo-map-markers";
import type { NdilaCoordinate } from "@/lib/ndila/ndila-client";
import { FacilitiesGeoMapPanel } from "./FacilitiesGeoMapPanel";
import { NearbyFacilitiesList } from "./NearbyFacilitiesList";

const SERVICE_TYPES: Array<{ value: string; label: string }> = [
  { value: "", label: "All services" },
  { value: "HOSPITAL", label: "Hospitals" },
  { value: "CLINIC", label: "Clinics" },
  { value: "RURAL_HEALTH_CENTRE", label: "Rural health centres" },
  { value: "PHARMACY", label: "Pharmacies" },
  { value: "LABORATORY", label: "Laboratories" },
];

export function DiscoverFacilitiesMapPanel() {
  const [origin, setOrigin] = useState<NdilaCoordinate>(DEFAULT_MAP_CENTER);
  const [locating, setLocating] = useState(false);
  const [locateError, setLocateError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [serviceType, setServiceType] = useState("");

  const useMyLocation = () => {
    setLocateError(null);
    if (!("geolocation" in navigator)) {
      setLocateError("Location is not available on this device — search for a place instead.");
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setOrigin({ latitude: pos.coords.latitude, longitude: pos.coords.longitude });
        setLocating(false);
      },
      () => {
        setLocateError("We could not read your location — search for a place instead.");
        setLocating(false);
      },
      { timeout: 10_000 },
    );
  };

  return (
    <div className="space-y-4" data-testid="discover-facilities-map-panel">
      <div className="grid gap-4 lg:grid-cols-2">
        <div>
          <div className="mb-2 flex items-center justify-between gap-2">
            <p className="text-xs font-medium text-foreground">Facilities near you</p>
            <button
              type="button"
              onClick={useMyLocation}
              disabled={locating}
              data-testid="use-my-location"
              className="inline-flex items-center gap-1.5 rounded-lg border border-border px-2.5 py-1 text-xs font-medium text-foreground hover:bg-background disabled:opacity-50"
            >
              <LocateFixed className="h-3.5 w-3.5" />
              {locating ? "Locating…" : "Use my location"}
            </button>
          </div>
          {locateError ? (
            <p className="mb-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs text-warning-foreground">
              {locateError}
            </p>
          ) : null}
          <NdilaAddressSearchBox
            country="ZWE"
            purposeOfUse="CITIZEN_DISCOVERY"
            onSelect={(result) => setOrigin({ latitude: result.latitude, longitude: result.longitude })}
          />
          <div className="mt-3">
            <NearbyFacilitiesList origin={origin} onPickFacility={(name) => setSearch(name)} />
          </div>
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
          <label className="mb-2 block text-xs font-medium text-foreground" htmlFor="discover-facility-search">
            Search the national facility registry
          </label>
          <div className="mb-3 flex gap-2">
            <input
              id="discover-facility-search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Facility name, district or code…"
              className="w-full rounded-lg border border-border px-3 py-2 text-sm"
            />
            <select
              value={serviceType}
              onChange={(e) => setServiceType(e.target.value)}
              data-testid="discover-service-type"
              aria-label="Filter by service"
              className="shrink-0 rounded-lg border border-border px-2 py-2 text-sm text-foreground"
            >
              {SERVICE_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>
          <FacilitiesGeoMapPanel
            title="Registered facilities"
            subtitle="Governed facility registry — 1,778 sites nationwide"
            search={search || undefined}
            facilityType={serviceType || undefined}
            size={60}
          />
        </div>
      </div>
    </div>
  );
}
