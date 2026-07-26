"use client";

/**
 * Hero "Get Health Services" — unified public discovery surface.
 *
 * One anonymous search across the real per-domain public lanes via the BFF
 * discovery orchestrator (GET /internal/v1/public/gateway/discovery/search):
 * care/facilities, pharmacies, diagnostics, virtual services, marketplace goods,
 * coverage plans and wellness screening. The internal services stay hidden; each
 * result is labelled by category and carries only fields the source truly gives —
 * no fabricated stock, open-now, ratings or online status.
 *
 * Categories with no real public source yet (a browseable provider directory)
 * are shown honestly: they route to what IS real (registration-number verify)
 * rather than faking a listing.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  BadgeCheck,
  List,
  Loader2,
  Map as MapIcon,
  Navigation,
  Search,
  X,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";
import type { NdilaGeoMarker } from "@/components/ndila/NdilaMapLibre";
import { FindCareMap } from "./find-care/FindCareMap";
import { ResumeJourneyCard } from "./ResumeJourneyCard";

export interface DiscoveryResult {
  category: string;
  type: string;
  id: string | null;
  title: string;
  subtitle: string | null;
  meta: string[];
  distanceMeters: number | null;
  etaMinutes: number | null;
  latitude: number | null;
  longitude: number | null;
  availability: string | null;
  href: string | null;
  actionLabel: string | null;
}

export interface DiscoveryResponse {
  query: string | null;
  category: string;
  results: DiscoveryResult[];
  categoryCounts: Record<string, number>;
  notes: string[];
}

type GeoStatus = "idle" | "requesting" | "denied" | "unsupported" | "error";

interface Place {
  label: string;
  lat: number;
  lng: number;
}

/**
 * Committed Zimbabwe localities (public/geo/zimbabwe-places.geojson). Map-first only
 * means something if the map opens somewhere useful: a national view of Zimbabwe with
 * no pins is a decorative map, not a discovery surface. So discovery starts on a city
 * fallback — labelled as a fallback, never presented as the person's real location —
 * and one tap swaps it for their actual position.
 */
const PLACES: Place[] = [
  { label: "Harare", lat: -17.829, lng: 31.053 },
  { label: "Bulawayo", lat: -20.157, lng: 28.583 },
  { label: "Mutare", lat: -18.973, lng: 32.671 },
  { label: "Gweru", lat: -19.455, lng: 29.825 },
  { label: "Kwekwe", lat: -18.928, lng: 29.815 },
  { label: "Kadoma", lat: -18.333, lng: 29.915 },
  { label: "Masvingo", lat: -20.074, lng: 30.83 },
  { label: "Chinhoyi", lat: -17.354, lng: 30.2 },
  { label: "Victoria Falls", lat: -17.932, lng: 25.857 },
  { label: "Marondera", lat: -18.185, lng: 31.552 },
  { label: "Rusape", lat: -18.527, lng: 32.128 },
  { label: "Bindura", lat: -17.301, lng: 31.093 },
];
const DEFAULT_PLACE = PLACES[0];

/** Display chips → backend category value. "providers" is handled honestly client-side. */
const CATEGORIES: { label: string; value: string }[] = [
  { label: "All", value: "all" },
  { label: "Care", value: "care" },
  { label: "Pharmacy", value: "pharmacy" },
  { label: "Diagnostics", value: "diagnostics" },
  { label: "Virtual care", value: "virtual" },
  { label: "Medicines", value: "marketplace" },
  { label: "Wellness", value: "wellness" },
  { label: "Coverage", value: "coverage" },
  { label: "Providers", value: "providers" },
];

const CATEGORY_BADGE: Record<string, string> = {
  care: "bg-emerald-100 text-emerald-800",
  pharmacy: "bg-teal-100 text-teal-800",
  diagnostics: "bg-indigo-100 text-indigo-800",
  virtual: "bg-violet-100 text-violet-800",
  marketplace: "bg-amber-100 text-amber-900",
  coverage: "bg-sky-100 text-sky-800",
  wellness: "bg-green-100 text-green-800",
};

function formatKm(m: number | null): string | null {
  if (m == null) return null;
  return m >= 1000 ? `${(m / 1000).toFixed(m < 10000 ? 1 : 0)} km` : `${Math.round(m)} m`;
}

function resultKey(r: DiscoveryResult, i: number): string {
  return `${r.category}-${r.id ?? i}`;
}

/** One discovery result. Selecting it drives the enlarged card over the map. */
function ResultCard({
  result: r,
  selected,
  onSelect,
}: {
  result: DiscoveryResult;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <div
      className={`rounded-xl border bg-white p-3 transition-colors ${
        selected ? "border-emerald-500 ring-1 ring-emerald-500/30" : "border-slate-200"
      }`}
    >
      <button
        type="button"
        onClick={onSelect}
        aria-pressed={selected}
        className="block w-full text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500"
      >
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <span
              className={`inline-block rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${
                CATEGORY_BADGE[r.category] ?? "bg-slate-100 text-slate-700"
              }`}
            >
              {r.type}
            </span>
            <p className="mt-1 truncate font-semibold text-slate-900">{r.title}</p>
            {r.subtitle && <p className="truncate text-xs text-slate-500">{r.subtitle}</p>}
          </div>
          {(formatKm(r.distanceMeters) || r.etaMinutes != null) && (
            <span className="shrink-0 text-right text-xs font-medium text-slate-600">
              {formatKm(r.distanceMeters)}
              {r.etaMinutes != null && (
                <span className="block text-[11px] font-normal text-slate-500">
                  ~{r.etaMinutes} min away
                </span>
              )}
            </span>
          )}
        </div>
        {(r.availability || r.meta.length > 0) && (
          <div className="mt-1.5 flex flex-wrap gap-1">
            {r.availability && (
              <span className="rounded-md bg-emerald-50 px-1.5 py-0.5 text-[11px] font-medium text-emerald-800">
                {r.availability}
              </span>
            )}
            {r.meta.map((m) => (
              <span key={m} className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-600">
                {m}
              </span>
            ))}
          </div>
        )}
      </button>
      {r.href && r.actionLabel && (
        <Link
          href={r.href}
          className="mt-2 inline-flex min-h-8 items-center rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-100"
        >
          {r.actionLabel}
        </Link>
      )}
    </div>
  );
}

/**
 * What the surface shows when a lane returned nothing. It reports every note the
 * orchestrator gave (they fail independently, so several can be true at once) and
 * offers the one action that actually unblocks the common case — sharing location,
 * which the find-care lane requires before it will return anything at all.
 */
function DiscoveryEmptyState({
  notes,
  hasLocation,
  geoStatus,
  onRequestLocation,
  compact = false,
}: {
  notes: string[];
  hasLocation: boolean;
  geoStatus: GeoStatus;
  onRequestLocation: () => void;
  compact?: boolean;
}) {
  return (
    <div
      className={`rounded-xl border border-slate-200 p-4 ${
        compact ? "bg-white/95 shadow-lg backdrop-blur-sm" : "bg-slate-50/70"
      }`}
    >
      <p className="text-sm font-semibold text-slate-900">Nothing to show here yet</p>
      {notes.length > 0 ? (
        <ul className="mt-1.5 space-y-1 text-sm text-slate-600">
          {notes.map((n) => (
            <li key={n}>{n}</li>
          ))}
        </ul>
      ) : (
        <p className="mt-1.5 text-sm text-slate-600">
          Search a service, medicine or wellness need to see results.
        </p>
      )}
      {!hasLocation && (
        <button
          type="button"
          onClick={onRequestLocation}
          disabled={geoStatus === "requesting"}
          className="mt-3 inline-flex min-h-9 items-center gap-1.5 rounded-lg bg-emerald-700 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-800 disabled:opacity-60"
        >
          {geoStatus === "requesting" ? (
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          ) : (
            <Navigation className="h-4 w-4" aria-hidden />
          )}
          Share my location
        </button>
      )}
      {geoStatus === "denied" && (
        <p className="mt-2 text-xs text-slate-500">
          Location is blocked for this site. You can still search by name — try a clinic, a
          medicine or a service.
        </p>
      )}
      {(geoStatus === "unsupported" || geoStatus === "error") && (
        <p className="mt-2 text-xs text-slate-500">
          We couldn&apos;t read your location. Searching by name works just as well.
        </p>
      )}
    </div>
  );
}

export function HeroDiscoverySurface() {
  const [draft, setDraft] = useState("");
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("all");
  const [location, setLocation] = useState<{ lat: number; lng: number; label: string }>({
    lat: DEFAULT_PLACE.lat,
    lng: DEFAULT_PLACE.lng,
    label: DEFAULT_PLACE.label,
  });
  /** False while we are showing a city fallback rather than the person's real position. */
  const [preciseLocation, setPreciseLocation] = useState(false);
  // Map is the default discovery view: the brief treats spatial context as the primary
  // way people find care. List stays one click away for low-bandwidth and screen readers.
  const [view, setView] = useState<"map" | "list">("map");
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [data, setData] = useState<DiscoveryResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [geoStatus, setGeoStatus] = useState<GeoStatus>("idle");

  const runSearch = useCallback(
    async (q: string, cat: string, loc: { lat: number; lng: number } | null) => {
      if (cat === "providers") {
        // No public provider directory exists yet — show the honest verify path instead.
        setData(null);
        setError(null);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const params = new URLSearchParams();
        if (q.trim()) params.set("q", q.trim());
        params.set("category", cat);
        if (loc) {
          params.set("lat", String(loc.lat));
          params.set("lng", String(loc.lng));
        }
        const res = await apiClient.get<DiscoveryResponse>(
          `/internal/v1/public/gateway/discovery/search?${params.toString()}`,
        );
        setData(res);
      } catch (err) {
        const status = (err as { status?: number })?.status;
        setError(
          status === 429
            ? "You've searched a lot in a short time. Please wait a moment and try again."
            : "Search is temporarily unavailable. Please try again shortly.",
        );
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  // Initial load searches at the city fallback rather than nowhere, so the blend leads
  // with real facilities that have coordinates instead of unmappable commodities.
  useEffect(() => {
    void runSearch("", "all", { lat: DEFAULT_PLACE.lat, lng: DEFAULT_PLACE.lng });
  }, [runSearch]);

  const requestLocation = useCallback(() => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setGeoStatus("unsupported");
      return;
    }
    setGeoStatus("requesting");
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setGeoStatus("idle");
        setPreciseLocation(true);
        const loc = {
          lat: Number(pos.coords.latitude.toFixed(5)),
          lng: Number(pos.coords.longitude.toFixed(5)),
          label: "Your current location",
        };
        setLocation(loc);
        setView("map");
        void runSearch(query, category === "providers" ? "care" : category, loc);
      },
      (err) => setGeoStatus(err.code === err.PERMISSION_DENIED ? "denied" : "error"),
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }, [query, category, runSearch]);

  function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setQuery(draft);
    void runSearch(draft, category, location);
  }

  function pickCategory(value: string) {
    setCategory(value);
    void runSearch(query, value, location);
  }

  const results = data?.results ?? [];
  const geoMarkers = useMemo<NdilaGeoMarker[]>(
    () =>
      results
        .filter((r) => r.latitude != null && r.longitude != null)
        .map((r, i) => ({
          id: r.id ?? `r${i}`,
          label: r.title,
          latitude: r.latitude as number,
          longitude: r.longitude as number,
          markerType: "facility",
        })),
    [results],
  );

  /**
   * Per-category counts the BFF already returns. Only meaningful while the blend is
   * unfiltered: once a category is selected the response contains that lane alone, so
   * every other chip would read 0 and imply "nothing there" when nothing was asked for.
   * Providers has no public listing at all, so it never carries a count.
   */
  const countFor = useCallback(
    (value: string): number | null => {
      if (category !== "all" || !data || value === "providers") return null;
      if (value === "all") return results.length || null;
      return data.categoryCounts?.[value] ?? null;
    },
    [category, data, results.length],
  );

  const notes = data?.notes ?? [];

  /**
   * Feature the nearest mapped result on arrival, so the map carries an actionable card
   * rather than waiting for a click. Only ever a result that has a pin — featuring one
   * without coordinates would put a card on a map that cannot show where it is.
   */
  useEffect(() => {
    if (selectedKey || results.length === 0) return;
    let bestKey: string | null = null;
    let bestDistance = Number.POSITIVE_INFINITY;
    results.forEach((r, i) => {
      if (r.latitude == null || r.longitude == null) return;
      const d = r.distanceMeters ?? Number.MAX_SAFE_INTEGER;
      if (d < bestDistance) {
        bestDistance = d;
        bestKey = resultKey(r, i);
      }
    });
    if (bestKey) setSelectedKey(bestKey);
  }, [results, selectedKey]);

  /**
   * Kilometres from the chosen locality to the closest result that actually has a pin.
   * The find-care lane does a registry search and annotates distance afterwards — it does
   * not do a proximity query — so a locality can legitimately have no mapped care anywhere
   * near it. Centring tight on that locality would then render a confidently empty map,
   * which reads as "no care here" rather than "no coordinates here".
   */
  const nearestMappedKm = useMemo(() => {
    const withDistance = results
      .filter((r) => r.latitude != null && r.distanceMeters != null)
      .map((r) => (r.distanceMeters as number) / 1000);
    return withDistance.length ? Math.min(...withDistance) : null;
  }, [results]);
  const mappedCareIsFar = nearestMappedKm != null && nearestMappedKm > 30;

  // Resolve by key rather than holding the object: a new search replaces every result,
  // and a stale selection would keep an old facility on screen over a fresh map.
  const selectedResult = useMemo(
    () => (selectedKey ? results.find((r, i) => resultKey(r, i) === selectedKey) ?? null : null),
    [results, selectedKey],
  );

  return (
    <section
      aria-label="Get health services"
      className="flex h-full min-h-[32rem] flex-col overflow-hidden rounded-[1.6rem] xl:min-h-0 border border-white/40 bg-white/85 shadow-[0_30px_80px_-28px_rgba(0,0,0,.65)] ring-1 ring-inset ring-white/50 backdrop-blur-glass-strong supports-[not(backdrop-filter:blur(0px))]:bg-white [.low-blur_&]:bg-white [.low-blur_&]:backdrop-blur-none"
    >
      {/* Header + Map/List toggle */}
      <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-4 pt-4 pb-3 sm:px-5">
        <div>
          <h2 className="text-lg font-bold text-slate-900 sm:text-xl">Get Health Services</h2>
          <p className="text-xs text-slate-500">Search care, providers, medicines and wellness support</p>
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

      {/* Unified search + Near me */}
      <div className="space-y-2.5 px-4 pt-3 sm:px-5">
        <form onSubmit={submit} role="search" aria-label="Search health services">
          <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-2.5 py-1.5 focus-within:border-emerald-500 focus-within:ring-2 focus-within:ring-emerald-500/20">
            <Search className="h-4 w-4 shrink-0 text-slate-400" aria-hidden />
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              type="search"
              maxLength={120}
              aria-label="Search care, providers, medicines, wellness"
              placeholder="Search care, providers, medicines, wellness…"
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

        {/* Location + categories. The row wraps: a horizontal scrollbar under the chips
            reads as a browser artefact and clipped "Wellness" mid-word. */}
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="inline-flex min-h-8 shrink-0 items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-900">
            <Navigation className="h-3.5 w-3.5" aria-hidden />
            {preciseLocation ? location.label : `Showing ${location.label}`}
          </span>
          {!preciseLocation && (
            <>
              <button
                type="button"
                onClick={requestLocation}
                disabled={geoStatus === "requesting"}
                className="inline-flex min-h-8 shrink-0 items-center gap-1.5 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 hover:border-emerald-300 hover:bg-emerald-50 disabled:opacity-60"
              >
                {geoStatus === "requesting" ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden />
                ) : (
                  <Navigation className="h-3.5 w-3.5" aria-hidden />
                )}
                Use my location
              </button>
              <label className="sr-only" htmlFor="discovery-place">
                Choose a locality
              </label>
              <select
                id="discovery-place"
                value={location.label}
                onChange={(e) => {
                  const place = PLACES.find((pl) => pl.label === e.target.value) ?? DEFAULT_PLACE;
                  const loc = { lat: place.lat, lng: place.lng, label: place.label };
                  setLocation(loc);
                  setSelectedKey(null);
                  void runSearch(query, category === "providers" ? "care" : category, loc);
                }}
                className="min-h-8 shrink-0 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 hover:border-emerald-300"
              >
                {PLACES.map((pl) => (
                  <option key={pl.label} value={pl.label}>
                    {pl.label}
                  </option>
                ))}
              </select>
            </>
          )}
        </div>

        <div className="flex flex-wrap gap-1.5" role="tablist" aria-label="Discovery categories">
          {CATEGORIES.map((c) => {
            const count = countFor(c.value);
            return (
              <button
                key={c.value}
                type="button"
                role="tab"
                aria-selected={category === c.value}
                onClick={() => pickCategory(c.value)}
                className={`inline-flex min-h-8 shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium ${
                  category === c.value
                    ? "border-emerald-500 bg-emerald-600 text-white"
                    : "border-slate-200 bg-slate-50 text-slate-700 hover:border-emerald-300 hover:bg-emerald-50"
                }`}
              >
                {c.label}
                {count != null && (
                  <span
                    className={`rounded-full px-1.5 text-[10px] font-bold tabular-nums ${
                      category === c.value ? "bg-white/25 text-white" : "bg-white text-slate-600"
                    }`}
                  >
                    {count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {error && (
          <p role="alert" className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
            {error}
          </p>
        )}
      </div>

      {/* Body */}
      <div className="relative mt-3 min-h-0 flex-1 px-4 pb-3 sm:px-5">
        {loading && (
          <div className="absolute inset-x-4 top-0 z-10 flex items-center justify-center gap-2 rounded-lg bg-white/85 py-2 text-xs font-medium text-emerald-800 backdrop-blur-sm sm:inset-x-5">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Searching real health data…
          </div>
        )}

        {category === "providers" ? (
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm">
            <p className="font-semibold text-slate-900">Provider directory is coming soon</p>
            <p className="mt-1 text-xs text-slate-600">
              A searchable directory of individual providers isn&apos;t open to the public yet. You can already
              verify that a specific practitioner is registered and licensed.
            </p>
            <Link
              href="/verify/practitioner"
              className="mt-3 inline-flex min-h-9 items-center gap-1.5 rounded-lg bg-emerald-700 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-800"
            >
              <BadgeCheck className="h-4 w-4" aria-hidden /> Verify a registered provider
            </Link>
          </div>
        ) : view === "map" ? (
          <div className="flex h-full min-h-0 flex-col gap-2">
            {/*
              min-h floor matters as much as flex-1 here. Stacked below lg the surface
              sits at its own min-h-[30rem] and the chrome above/below eats nearly all
              of it — the map resolved to 28px at 390 and 105px at 768, a strip of
              nothing. The floor makes the surface grow instead of crushing the map.
            */}
            <div className="relative min-h-[16rem] flex-1 overflow-hidden rounded-xl border border-slate-200 lg:min-h-[14rem] xl:min-h-[13rem]">
              <FindCareMap
                results={[]}
                geoMarkers={geoMarkers}
                origin={preciseLocation ? { lat: location.lat, lng: location.lng } : null}
                height="100%"
                hideCaption
                compactChrome
                view={
                  geoMarkers.length === 0 || mappedCareIsFar
                    ? null
                    : { latitude: location.lat, longitude: location.lng, zoom: 11 }
                }
              />
              {/* Selected-result card, over the map so the map stays the dominant visual. */}
              {selectedResult && (
                <div className="absolute inset-x-2 bottom-2 z-[5]">
                  <div className="rounded-xl border border-emerald-300 bg-white/95 p-3 shadow-lg backdrop-blur-sm">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <span
                          className={`inline-block rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${
                            CATEGORY_BADGE[selectedResult.category] ?? "bg-slate-100 text-slate-700"
                          }`}
                        >
                          {selectedResult.type}
                        </span>
                        <p className="mt-1 truncate text-sm font-bold text-slate-900">
                          {selectedResult.title}
                        </p>
                        {selectedResult.subtitle && (
                          <p className="truncate text-xs text-slate-500">{selectedResult.subtitle}</p>
                        )}
                      </div>
                      <button
                        type="button"
                        aria-label="Close selected result"
                        onClick={() => setSelectedKey(null)}
                        className="shrink-0 rounded-full p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                      >
                        <X className="h-4 w-4" aria-hidden />
                      </button>
                    </div>
                    <div className="mt-1.5 flex flex-wrap items-center gap-1">
                      {selectedResult.availability && (
                        <span className="rounded-md bg-emerald-50 px-1.5 py-0.5 text-[11px] font-medium text-emerald-800">
                          {selectedResult.availability}
                        </span>
                      )}
                      {formatKm(selectedResult.distanceMeters) && (
                        <span className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-600">
                          {formatKm(selectedResult.distanceMeters)}
                          {selectedResult.etaMinutes != null && ` · ~${selectedResult.etaMinutes} min`}
                        </span>
                      )}
                      {selectedResult.meta.map((m) => (
                        <span key={m} className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-600">
                          {m}
                        </span>
                      ))}
                    </div>
                    {selectedResult.href && selectedResult.actionLabel && (
                      <Link
                        href={selectedResult.href}
                        className="mt-2 inline-flex min-h-9 items-center rounded-lg bg-emerald-700 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-800"
                      >
                        {selectedResult.actionLabel}
                      </Link>
                    )}
                  </div>
                </div>
              )}
              {/* Nothing to pin: say why and offer the one action that changes it. */}
              {!loading && geoMarkers.length === 0 && !selectedResult && (
                <div className="absolute inset-x-2 bottom-2 z-[5]">
                  {results.length === 0 ? (
                    <DiscoveryEmptyState
                      notes={notes}
                      hasLocation={preciseLocation}
                      geoStatus={geoStatus}
                      onRequestLocation={requestLocation}
                      compact
                    />
                  ) : (
                    <p className="rounded-lg bg-white/95 px-3 py-2 text-xs text-slate-600 shadow-lg backdrop-blur-sm">
                      None of these results carry map coordinates yet. They are all in the list —
                      switch to List to see them.
                    </p>
                  )}
                </div>
              )}
            </div>

            {/*
              The coordinate gap, stated plainly. HPA's register recorded almost no
              coordinates, so a city can have plenty of care and no pins near it. Saying
              "nearest mapped facility is 150 km away" is the difference between a data gap
              and an apparent absence of care.
            */}
            {mappedCareIsFar && !preciseLocation && (
              <p className="shrink-0 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] leading-4 text-amber-900">
                Nearest mapped facility is {Math.round(nearestMappedKm as number)}&nbsp;km from{" "}
                {location.label}, so the map shows a wider area. Closer facilities may be listed
                without map coordinates.
              </p>
            )}

            {/* Nearby-results strip: uses the lower right area with the same real results. */}
            {results.length > 0 && (
              <div className="shrink-0">
                <p className="px-1 pb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                  {preciseLocation ? "Nearby results" : `Around ${location.label}`}
                </p>
                <ul className="flex gap-2 overflow-x-auto pb-1">
                  {results.slice(0, 12).map((r, i) => {
                    const key = resultKey(r, i);
                    return (
                      <li key={key} className="shrink-0">
                        <button
                          type="button"
                          onClick={() => setSelectedKey(key)}
                          aria-pressed={selectedKey === key}
                          className={`w-44 rounded-lg border px-2.5 py-2 text-left ${
                            selectedKey === key
                              ? "border-emerald-500 bg-emerald-50"
                              : "border-slate-200 bg-white hover:border-emerald-300"
                          }`}
                        >
                          <span className="block truncate text-xs font-semibold text-slate-900">
                            {r.title}
                          </span>
                          <span className="block truncate text-[11px] text-slate-500">
                            {formatKm(r.distanceMeters) ?? r.type}
                          </span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              </div>
            )}
          </div>
        ) : (
          <div className="h-full min-h-0 space-y-2.5 overflow-y-auto pr-1">
            {results.length > 0 ? (
              results.map((r, i) => {
                const key = resultKey(r, i);
                return (
                  <ResultCard
                    key={key}
                    result={r}
                    selected={selectedKey === key}
                    onSelect={() => setSelectedKey(selectedKey === key ? null : key)}
                  />
                );
              })
            ) : (
              !loading && (
                <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-4">
                  <p className="text-sm font-semibold text-slate-900">
                    Nothing to show here yet
                  </p>
                  {/*
                    Every note the orchestrator returned, not just the first. The lanes fail
                    independently, so "medicines unavailable" and "share your location" can
                    both be true at once — dropping all but one reports a tidier picture than
                    the one the backend actually described.
                  */}
                  {notes.length > 0 ? (
                    <ul className="mt-1.5 space-y-1 text-sm text-slate-600">
                      {notes.map((n) => (
                        <li key={n}>{n}</li>
                      ))}
                    </ul>
                  ) : (
                    <p className="mt-1.5 text-sm text-slate-600">
                      Search a service, medicine or wellness need to see results.
                    </p>
                  )}
                  {!preciseLocation && (
                    <button
                      type="button"
                      onClick={requestLocation}
                      disabled={geoStatus === "requesting"}
                      className="mt-3 inline-flex min-h-9 items-center gap-1.5 rounded-lg bg-emerald-700 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-800 disabled:opacity-60"
                    >
                      {geoStatus === "requesting" ? (
                        <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                      ) : (
                        <Navigation className="h-4 w-4" aria-hidden />
                      )}
                      Share my location
                    </button>
                  )}
                  {geoStatus === "denied" && (
                    <p className="mt-2 text-xs text-slate-500">
                      Location is blocked for this site. You can still search by name — try a
                      clinic, a medicine or a service.
                    </p>
                  )}
                  {(geoStatus === "unsupported" || geoStatus === "error") && (
                    <p className="mt-2 text-xs text-slate-500">
                      We couldn&apos;t read your location. Searching by name works just as well.
                    </p>
                  )}
                </div>
              )
            )}
          </div>
        )}
      </div>

      {/*
        Continuation strip. Both children gate themselves on real state, so this whole
        band collapses to nothing when there is nothing true to say — it exists to use
        the space the fill above recovered, not to fill it regardless.
      */}
      <div className="shrink-0 space-y-2 px-4 pb-3 empty:hidden sm:px-5">
        {results.length > 0 && notes.length > 0 && (
          <ul className="space-y-1 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] text-amber-900">
            {notes.map((n) => (
              <li key={n}>{n}</li>
            ))}
          </ul>
        )}
        <ResumeJourneyCard tone="light" />
      </div>

      {/* Footer */}
      <div className="flex items-center justify-between gap-2 border-t border-slate-100 px-4 py-2.5 text-xs sm:px-5">
        <span className="text-slate-500">
          {data
            ? `${results.length} result${results.length === 1 ? "" : "s"} · ${geoMarkers.length} on the map · verified public data`
            : "Live national health data"}
        </span>
        <Link href="/welcome/find-care" className="font-semibold text-emerald-800 hover:text-emerald-950">
          Open full search →
        </Link>
      </div>
    </section>
  );
}
