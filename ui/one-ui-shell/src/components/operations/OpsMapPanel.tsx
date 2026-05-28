"use client";



import { useEffect, useMemo, useState } from "react";

import { Loader2, MapPin } from "lucide-react";

import { useNdilaTileConfig } from "@/hooks/queries/useNdila";



export interface OpsMapMarker {

  id: string;

  label: string;

  latitude?: number;

  longitude?: number;

  status?: string;

}



interface OpsMapPanelProps {

  title: string;

  subtitle?: string;

  markers: OpsMapMarker[];

  emptyHint?: string;

  /** When true, loads Ndila tile config from BFF (OSM fallback on failure). */

  useTiles?: boolean;

}



function tileUrlForZoom(template: string, zoom: number): string {

  return template.replace("{z}", String(zoom)).replace("{x}", "0").replace("{y}", "0");

}



/**

 * Ops map with Ndila tile layer (via BFF) and marker overlay list.

 */

export function OpsMapPanel({

  title,

  subtitle,

  markers,

  emptyHint,

  useTiles = true,

}: OpsMapPanelProps) {

  const tilesQ = useNdilaTileConfig();

  const [zoom, setZoom] = useState(12);



  const tileTemplate = useMemo(() => {

    if (!useTiles || tilesQ.isError) return "";

    const cfg = tilesQ.data;

    return typeof cfg?.tileUrlTemplate === "string" ? cfg.tileUrlTemplate : "";

  }, [tilesQ.data, tilesQ.isError, useTiles]);



  const attribution =

    typeof tilesQ.data?.attribution === "string"

      ? tilesQ.data.attribution

      : "";



  useEffect(() => {

    if (!tileTemplate) return;

    const max = typeof tilesQ.data?.maxZoom === "number" ? tilesQ.data.maxZoom : 18;

    setZoom((z) => Math.min(z, max));

  }, [tileTemplate, tilesQ.data?.maxZoom]);



  const mapStyle = useMemo(() => {

    if (!tileTemplate) {

      return {

        background: "linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)",

      };

    }

    return {

      backgroundImage: `url(${tileUrlForZoom(tileTemplate, zoom)})`,

      backgroundSize: "cover",

      backgroundPosition: "center",

    };

  }, [tileTemplate, zoom]);



  const geoMarkers = markers.filter((m) => m.latitude != null && m.longitude != null);



  return (

    <section className="rounded-xl border border-slate-200 bg-white p-4">

      <div className="mb-3 flex items-center justify-between gap-2">

        <div className="flex items-center gap-2">

          <MapPin className="h-4 w-4 text-rose-600" />

          <div>

            <h3 className="text-sm font-semibold text-slate-900">{title}</h3>

            {subtitle ? <p className="text-xs text-slate-500">{subtitle}</p> : null}

          </div>

        </div>

        {tileTemplate ? (

          <div className="flex items-center gap-1">

            <button

              type="button"

              aria-label="Zoom out"

              className="rounded border border-slate-200 px-2 py-0.5 text-xs text-slate-600"

              onClick={() => setZoom((z) => Math.max(4, z - 1))}

            >

              −

            </button>

            <span className="text-xs text-slate-500">{zoom}</span>

            <button

              type="button"

              aria-label="Zoom in"

              className="rounded border border-slate-200 px-2 py-0.5 text-xs text-slate-600"

              onClick={() => setZoom((z) => Math.min(18, z + 1))}

            >

              +

            </button>

          </div>

        ) : null}

      </div>



      <div className="relative mb-3 h-44 overflow-hidden rounded-lg border border-slate-200" style={mapStyle}>

        {useTiles && tilesQ.isLoading ? (

          <div className="absolute inset-0 flex items-center justify-center bg-white/60">

            <Loader2 className="h-5 w-5 animate-spin text-slate-400" />

          </div>

        ) : null}

        {geoMarkers.slice(0, 12).map((marker, index) => (

          <span

            key={marker.id}

            title={`${marker.label} (${marker.latitude?.toFixed(4)}, ${marker.longitude?.toFixed(4)})`}

            className="absolute h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-rose-600 shadow"

            style={{

              left: `${12 + ((index * 17) % 76)}%`,

              top: `${18 + ((index * 23) % 64)}%`,

            }}

          />

        ))}

      </div>



      {useTiles && tilesQ.isError ? (
        <p className="mb-2 text-xs text-amber-700">
          Ndila tiles unavailable — showing markers only until geospatial service is reachable.
        </p>
      ) : null}
      {tileTemplate && attribution ? (
        <p className="mb-2 text-[10px] text-slate-400">{attribution}</p>
      ) : null}



      {markers.length === 0 ? (

        <p className="text-xs text-slate-500">{emptyHint ?? "No geo-tagged assets for this view yet."}</p>

      ) : (

        <ul className="max-h-48 space-y-2 overflow-y-auto text-xs">

          {markers.map((marker) => (

            <li key={marker.id} className="flex justify-between gap-2 rounded-lg bg-slate-50 px-2 py-1.5">

              <span className="font-medium text-slate-800">{marker.label}</span>

              <span className="text-slate-500">

                {marker.latitude != null && marker.longitude != null

                  ? `${marker.latitude.toFixed(3)}, ${marker.longitude.toFixed(3)}`

                  : (marker.status ?? "—")}

              </span>

            </li>

          ))}

        </ul>

      )}

    </section>

  );

}


