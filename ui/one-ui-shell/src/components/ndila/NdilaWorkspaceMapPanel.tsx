"use client";

import { useMemo, useState } from "react";
import { Loader2, MapPin, Navigation } from "lucide-react";
import { useNdilaTileConfig } from "@/hooks/queries/useNdila";
import type { NdilaCoordinate } from "@/lib/ndila/ndila-client";
import { NdilaAddressSearchBox } from "./NdilaAddressSearchBox";
import { NdilaMap } from "./NdilaMap";
import { NdilaNearbyServicesMap } from "./NdilaNearbyServicesMap";
import { NdilaOfflineNotice } from "./NdilaOfflineNotice";
import { NdilaProviderStatusBadge } from "./NdilaProviderStatusBadge";
import { NdilaRoutePreview } from "./NdilaRoutePreview";

const DEFAULT_CENTER: NdilaCoordinate = { latitude: -17.8252, longitude: 31.0335 };

export function NdilaWorkspaceMapPanel() {
  const tileQ = useNdilaTileConfig();
  const [origin, setOrigin] = useState<NdilaCoordinate>(DEFAULT_CENTER);
  const [destination, setDestination] = useState<NdilaCoordinate | null>(null);
  const [radiusKm, setRadiusKm] = useState(15);

  const mapMarkers = useMemo(
    () => [
      {
        id: "origin",
        label: "Map centre",
        latitude: origin.latitude,
        longitude: origin.longitude,
        markerType: "ORIGIN",
        status: "ACTIVE",
      },
      ...(destination
        ? [
            {
              id: "destination",
              label: "Route destination",
              latitude: destination.latitude,
              longitude: destination.longitude,
              markerType: "DESTINATION",
              status: "PLANNED",
            },
          ]
        : []),
    ],
    [origin, destination],
  );

  const tileOffline = tileQ.isError || (!tileQ.isLoading && !tileQ.data?.provider && !tileQ.data?.tileUrlTemplate);

  return (
    <section
      className="rounded-xl border border-info/25 bg-card p-4 shadow-sm"
      data-testid="ndila-workspace-map-panel"
    >
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 text-sm font-medium text-primary-hover">
            <MapPin className="h-4 w-4 text-indigo-600" />
            Ndila map workspace
          </div>
          <p className="mt-1 text-xs text-primary-hover/80">
            Governed tiles, geocoding, nearby services, and route preview — provider selection is server-side only.
          </p>
        </div>
        <NdilaProviderStatusBadge
          provider={tileQ.data?.provider}
          productionSafe={!tileQ.data?.tileUrlTemplate?.startsWith("mock://")}
        />
      </div>

      {tileQ.isLoading ? (
        <div className="mb-3 flex items-center gap-2 text-xs text-muted-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Loading Ndila tile policy…
        </div>
      ) : null}

      <NdilaOfflineNotice offline={tileOffline} />

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <div className="space-y-3">
          <div>
            <p className="mb-1 text-xs font-medium text-foreground">Search map centre</p>
            <NdilaAddressSearchBox
              country="ZWE"
              purposeOfUse="GEOSPATIAL_INTELLIGENCE"
              onSelect={(result) => setOrigin({ latitude: result.latitude, longitude: result.longitude })}
            />
          </div>
          <div>
            <p className="mb-1 text-xs font-medium text-foreground">Route destination (optional)</p>
            <NdilaAddressSearchBox
              country="ZWE"
              purposeOfUse="GEOSPATIAL_INTELLIGENCE"
              onSelect={(result) =>
                setDestination({ latitude: result.latitude, longitude: result.longitude })
              }
            />
          </div>
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <label htmlFor="ndila-radius-km" className="font-medium">
              Nearby radius
            </label>
            <select
              id="ndila-radius-km"
              className="rounded border border-border px-2 py-1 text-xs"
              value={radiusKm}
              onChange={(e) => setRadiusKm(Number(e.target.value))}
            >
              <option value={5}>5 km</option>
              <option value={15}>15 km</option>
              <option value={30}>30 km</option>
              <option value={50}>50 km</option>
            </select>
          </div>
          <NdilaMap
            center={origin}
            zoom={12}
            markers={mapMarkers}
            height={360}
            layers={["facilities", "catchment"]}
            mode="OPERATIONS"
            fitToMarkers={mapMarkers.length > 1}
          />
        </div>

        <div className="space-y-4">
          <div>
            <p className="mb-2 text-xs font-medium text-foreground">Nearby governed services</p>
            <NdilaNearbyServicesMap
              origin={origin}
              radiusMeters={radiusKm * 1000}
              entityTypes={["FACILITY", "PHARMACY", "PUBLIC_HEALTH_SITE"]}
              height={280}
            />
          </div>
          {destination ? (
            <div data-testid="ndila-route-preview-section">
              <p className="mb-2 flex items-center gap-1 text-xs font-medium text-foreground">
                <Navigation className="h-3.5 w-3.5" />
                Route preview
              </p>
              <NdilaRoutePreview origin={origin} destination={destination} mode="MOTORBIKE" />
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">
              Add a route destination above to preview governed ETA and distance.
            </p>
          )}
        </div>
      </div>
    </section>
  );
}
