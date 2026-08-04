"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, MapPin, Navigation } from "lucide-react";
import { apiClient } from "@/lib/api-client";

/**
 * Nearby emergency care — surfaces real facilities through the anonymous
 * gateway find-care lane (TUSO registry + Ndila routing behind the BFF).
 * Reachable without sign-in; degrades honestly when the search is unavailable.
 */

interface CareResult {
  facilityId?: string | number;
  facilityUuid?: string;
  name?: string;
  facilityType?: string;
  district?: string;
  province?: string;
  distanceMeters?: number;
  etaMinutes?: number;
  openNow?: boolean;
  operationalStatus?: string;
}

interface CareSearchResponse {
  results?: CareResult[];
}

function formatDistance(meters?: number): string | null {
  if (meters === undefined || meters === null) return null;
  if (meters < 1000) return `${Math.round(meters)} m`;
  return `${(meters / 1000).toFixed(1)} km`;
}

export function NearbyEmergencyCare({
  lat,
  lng,
  province,
}: {
  lat?: number;
  lng?: number;
  province?: string;
}) {
  const [results, setResults] = useState<CareResult[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const haveLocation = (lat !== undefined && lng !== undefined) || Boolean(province);

  useEffect(() => {
    if (!haveLocation) return;
    let cancelled = false;
    setLoading(true);
    setError("");
    const params = new URLSearchParams();
    // Match on the EMERGENCY service *capability*, not the word "emergency" in a
    // facility name. Free-text q=emergency was falling through to a name LIKE search
    // and surfacing the single facility literally named "Emergency…", far away.
    params.set("service", "EMERGENCY");
    if (lat !== undefined && lng !== undefined) {
      params.set("lat", String(lat));
      params.set("lng", String(lng));
    } else if (province) {
      params.set("province", province);
    }
    params.set("page", "0");
    params.set("size", "3");
    apiClient
      .get<CareSearchResponse>(`/internal/v1/public/gateway/find-care/search?${params.toString()}`)
      .then((res) => {
        if (!cancelled) setResults(res.results ?? []);
      })
      .catch(() => {
        if (!cancelled) setError("Facility search is temporarily unavailable.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [lat, lng, province, haveLocation]);

  if (!haveLocation) return null;

  return (
    <div data-testid="nearby-emergency-care" className="mt-3 rounded-xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-4">
      <h3 className="flex items-center gap-1.5 text-sm font-semibold text-slate-900">
        <MapPin className="h-4 w-4 text-emerald-600" aria-hidden />
        Nearest care with emergency services
      </h3>
      {loading && (
        <p className="mt-2 flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Searching nearby facilities…
        </p>
      )}
      {error && <p className="mt-2 text-sm text-slate-600">{error}</p>}
      {results && results.length === 0 && !loading && (
        <p className="mt-2 text-sm text-slate-600">
          No matching facilities found for this location — call the emergency numbers for guidance.
        </p>
      )}
      {results && results.length > 0 && (
        <ul className="mt-2 divide-y divide-slate-100">
          {results.map((r) => {
            const distance = formatDistance(r.distanceMeters);
            return (
              <li key={String(r.facilityUuid ?? r.facilityId ?? r.name)} className="py-2">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <Link
                      href={`/welcome/find-care/${encodeURIComponent(String(r.facilityId ?? r.facilityUuid ?? ""))}`}
                      className="text-sm font-semibold text-emerald-800 underline-offset-2 hover:underline"
                    >
                      {r.name}
                    </Link>
                    <p className="text-xs text-slate-500">
                      {[r.facilityType, r.district, r.province].filter(Boolean).join(" · ")}
                    </p>
                  </div>
                  <div className="shrink-0 text-right text-xs text-slate-600">
                    {distance && (
                      <span className="inline-flex items-center gap-1">
                        <Navigation className="h-3 w-3" aria-hidden />
                        {distance}
                      </span>
                    )}
                    {r.openNow !== undefined && (
                      <p className={r.openNow ? "text-emerald-700" : "text-slate-500"}>
                        {r.openNow ? "Open now" : "May be closed"}
                      </p>
                    )}
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
