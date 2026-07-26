"use client";

/**
 * Hero "Get Health Services" surface — a live, searchable access-to-care panel
 * embedded in the landing hero's right column (replacing the old passive photo).
 *
 * It reuses the anonymous find-care lane end-to-end: the shared journey store,
 * the same `GET /internal/v1/public/gateway/find-care/search` call (TUSO registry
 * + Ndila routing via the BFF), the shared MapLibre map, and the honest result
 * card. NOTHING here is fabricated — there is no "open now"/rating on this lane
 * (the codebase deliberately refuses them: openNow is always null, hours show as
 * "not verified", ratings live only on the verified detail page). The surface
 * shows real fields only: name, type/level/district, service-match, distance/ETA.
 *
 * "Open full search" deep-links to /welcome/find-care?service=…&q=…, which the
 * full page re-seeds from the same store — the journey carries over seamlessly.
 */

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import {
  List,
  Loader2,
  Map as MapIcon,
  Navigation,
  Search,
  X,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";
import type { CareResult, CareSearchResponse } from "@/lib/find-care/types";
import { SERVICE_CHIPS } from "@/lib/find-care/service-chips";
import { useFindCareJourneyStore } from "@/hooks/useFindCareJourneyStore";
import { FindCareResultCard } from "./find-care/FindCareResultCard";
import { FindCareMap } from "./find-care/FindCareMap";

const PAGE_SIZE = 10;
const HERO_CHIPS = SERVICE_CHIPS.slice(0, 4);

type GeoStatus = "idle" | "requesting" | "denied" | "unsupported" | "error";

export function HeroFindCareSurface() {
  const {
    need,
    serviceToken,
    location,
    results,
    selectedFacilityId,
    hydrated,
    setNeed,
    setService,
    setLocation,
    clearLocation,
    setResults,
    setSelectedFacility,
    hydrate,
  } = useFindCareJourneyStore();

  // The hero leads with the map (per the landing concept). This is a local UI
  // preference — it does not overwrite the full find-care page's own view.
  const [view, setView] = useState<"map" | "list">("map");
  const [needDraft, setNeedDraft] = useState(need);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [geoStatus, setGeoStatus] = useState<GeoStatus>("idle");

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    setNeedDraft(need);
  }, [need]);

  const runSearch = useCallback(async () => {
    const s = useFindCareJourneyStore.getState();
    const activeService = s.serviceToken;
    const activeNeed = s.need.trim();
    const activeLocation = s.location;
    const activeFilters = s.filters;

    if (!activeService && !activeNeed && !activeLocation && !activeFilters.province) {
      setError("Tell us what care you need, or share your location, to search.");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      if (activeService) params.set("service", activeService);
      else if (activeNeed) params.set("q", activeNeed);
      if (activeLocation) {
        params.set("lat", String(activeLocation.lat));
        params.set("lng", String(activeLocation.lng));
      }
      if (activeFilters.province) params.set("province", activeFilters.province);
      if (activeFilters.district) params.set("district", activeFilters.district);
      if (activeFilters.facilityType) params.set("facilityType", activeFilters.facilityType);
      params.set("page", "0");
      params.set("size", String(PAGE_SIZE));

      const res = await apiClient.get<CareSearchResponse>(
        `/internal/v1/public/gateway/find-care/search?${params.toString()}`,
      );
      setResults(res);
    } catch (err) {
      const status = (err as { status?: number })?.status;
      setError(
        status === 429
          ? "You've searched a lot in a short time. Please wait a moment and try again."
          : "Care search is temporarily unavailable. Open the full search or try again shortly.",
      );
    } finally {
      setLoading(false);
    }
  }, [setResults]);

  const requestLocation = useCallback(() => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setGeoStatus("unsupported");
      return;
    }
    setGeoStatus("requesting");
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setGeoStatus("idle");
        setLocation({
          lat: Number(pos.coords.latitude.toFixed(5)),
          lng: Number(pos.coords.longitude.toFixed(5)),
          source: "device",
          label: "Your current location",
        });
        setTimeout(() => void runSearch(), 0);
      },
      (err) => {
        setGeoStatus(err.code === err.PERMISSION_DENIED ? "denied" : "error");
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }, [setLocation, runSearch]);

  function submitNeed(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setNeed(needDraft);
    setTimeout(() => void runSearch(), 0);
  }

  function pickService(token: string, label: string) {
    setService(token, label);
    setTimeout(() => void runSearch(), 0);
  }

  const rows: CareResult[] = results?.results ?? [];
  const featured = useMemo(
    () => rows.find((r) => r.facilityId === selectedFacilityId) ?? rows[0] ?? null,
    [rows, selectedFacilityId],
  );
  const locationShared = Boolean(location);

  // Deep-link the full experience, seeded from the same store keys.
  const fullHref = useMemo(() => {
    const qs = new URLSearchParams();
    if (serviceToken) qs.set("service", serviceToken);
    else if (need.trim()) qs.set("q", need.trim());
    const s = qs.toString();
    return s ? `/welcome/find-care?${s}` : "/welcome/find-care";
  }, [serviceToken, need]);

  return (
    <section
      aria-label="Get health services"
      className="flex h-full min-h-[30rem] flex-col overflow-hidden rounded-[1.6rem] border border-emerald-100 bg-white shadow-sm"
    >
      {/* Header + Map/List toggle */}
      <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-4 pt-4 pb-3 sm:px-5">
        <div>
          <h2 className="text-lg font-bold text-slate-900 sm:text-xl">Get Health Services</h2>
          <p className="text-xs text-slate-500">Find trusted care near you</p>
        </div>
        <div className="inline-flex shrink-0 rounded-lg border border-slate-200 bg-slate-50 p-0.5 text-xs font-semibold">
          <button
            type="button"
            onClick={() => setView("map")}
            aria-pressed={view === "map"}
            className={`inline-flex min-h-8 items-center gap-1 rounded-md px-2.5 py-1 ${
              view === "map" ? "bg-white text-emerald-800 shadow-sm" : "text-slate-500 hover:text-slate-800"
            }`}
          >
            <MapIcon className="h-3.5 w-3.5" aria-hidden /> Map
          </button>
          <button
            type="button"
            onClick={() => setView("list")}
            aria-pressed={view === "list"}
            className={`inline-flex min-h-8 items-center gap-1 rounded-md px-2.5 py-1 ${
              view === "list" ? "bg-white text-emerald-800 shadow-sm" : "text-slate-500 hover:text-slate-800"
            }`}
          >
            <List className="h-3.5 w-3.5" aria-hidden /> List
          </button>
        </div>
      </div>

      {/* Search + Near me + service chips */}
      <div className="space-y-2.5 px-4 pt-3 sm:px-5">
        <form onSubmit={submitNeed} role="search" aria-label="Search health services">
          <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-2.5 py-1.5 focus-within:border-emerald-500 focus-within:ring-2 focus-within:ring-emerald-500/20">
            <Search className="h-4 w-4 shrink-0 text-slate-400" aria-hidden />
            <input
              value={needDraft}
              onChange={(e) => setNeedDraft(e.target.value)}
              type="search"
              maxLength={120}
              aria-label="Search services, providers or facilities"
              placeholder="Search services, providers or facilities"
              className="min-h-9 min-w-0 flex-1 bg-transparent text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none"
            />
            <button
              type="submit"
              disabled={loading}
              className="inline-flex min-h-9 shrink-0 items-center rounded-lg bg-emerald-700 px-3 py-1.5 text-sm font-semibold text-white hover:bg-emerald-800 disabled:opacity-50"
            >
              Search
            </button>
          </div>
        </form>

        <div className="flex flex-wrap items-center gap-1.5">
          {locationShared ? (
            <span className="inline-flex min-h-8 items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-900">
              <Navigation className="h-3.5 w-3.5" aria-hidden />
              {location?.label ?? "Near me"}
              <button
                type="button"
                aria-label="Clear location"
                onClick={() => clearLocation()}
                className="rounded-full p-0.5 hover:bg-emerald-200/70"
              >
                <X className="h-3 w-3" aria-hidden />
              </button>
            </span>
          ) : (
            <button
              type="button"
              onClick={requestLocation}
              disabled={geoStatus === "requesting"}
              className="inline-flex min-h-8 items-center gap-1.5 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 hover:border-emerald-300 hover:bg-emerald-50 disabled:opacity-60"
            >
              {geoStatus === "requesting" ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden />
              ) : (
                <Navigation className="h-3.5 w-3.5" aria-hidden />
              )}
              Near me
            </button>
          )}
          {HERO_CHIPS.map((chip) => (
            <button
              key={chip.token}
              type="button"
              onClick={() => pickService(chip.token, chip.label)}
              aria-pressed={serviceToken === chip.token}
              className={`min-h-8 rounded-full border px-2.5 py-1 text-xs font-medium ${
                serviceToken === chip.token
                  ? "border-emerald-500 bg-emerald-600 text-white"
                  : "border-slate-200 bg-slate-50 text-slate-700 hover:border-emerald-300 hover:bg-emerald-50"
              }`}
            >
              {chip.label}
            </button>
          ))}
        </div>

        {geoStatus === "denied" && (
          <p className="text-xs text-slate-500">
            Location is off, so we can't sort by distance. Search a service, or pick a province in the full
            search.
          </p>
        )}
        {error && (
          <p role="alert" className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
            {error}
          </p>
        )}
      </div>

      {/* Body: map or list */}
      <div className="relative mt-3 min-h-0 flex-1 px-4 pb-3 sm:px-5">
        {loading && (
          <div className="absolute inset-x-4 top-0 z-10 flex items-center justify-center gap-2 rounded-lg bg-white/80 py-2 text-xs font-medium text-emerald-800 backdrop-blur-sm sm:inset-x-5">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Searching real facilities…
          </div>
        )}

        {view === "map" ? (
          <div className="space-y-3">
            <div className="overflow-hidden rounded-xl border border-slate-200">
              <FindCareMap
                results={rows}
                origin={location ? { lat: location.lat, lng: location.lng } : null}
                selectedFacilityId={selectedFacilityId}
                height={featured ? 300 : 360}
                hideCaption
              />
            </div>
            {featured ? (
              <FindCareResultCard
                result={featured}
                locationShared={locationShared}
                virtualCareAvailable={results?.virtualCareAvailable}
                onOpen={setSelectedFacility}
                active
              />
            ) : (
              <p className="px-1 text-xs text-slate-500">
                {hydrated
                  ? "Search a service or share your location to see facilities that actually offer the care you need — no sign-in needed."
                  : "Loading your last search…"}
              </p>
            )}
          </div>
        ) : (
          <div className="max-h-[26rem] space-y-3 overflow-y-auto pr-1">
            {rows.length > 0 ? (
              rows.map((r) => (
                <FindCareResultCard
                  key={r.facilityId}
                  result={r}
                  locationShared={locationShared}
                  virtualCareAvailable={results?.virtualCareAvailable}
                  onOpen={setSelectedFacility}
                  active={r.facilityId === selectedFacilityId}
                />
              ))
            ) : (
              <p className="px-1 text-sm text-slate-500">
                Search a service or share your location to list facilities near you.
              </p>
            )}
          </div>
        )}
      </div>

      {/* Footer: honest continuity + full experience */}
      <div className="flex items-center justify-between gap-2 border-t border-slate-100 px-4 py-2.5 text-xs sm:px-5">
        <span className="text-slate-500">
          {results ? `${results.total} result${results.total === 1 ? "" : "s"} · real facility data` : "Real Tuso & Ndila data"}
        </span>
        <Link href={fullHref} className="font-semibold text-emerald-800 hover:text-emerald-950">
          Open full search →
        </Link>
      </div>
    </section>
  );
}
