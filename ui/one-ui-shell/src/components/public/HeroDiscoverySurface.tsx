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

interface DiscoveryResult {
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

interface DiscoveryResponse {
  query: string | null;
  category: string;
  results: DiscoveryResult[];
  categoryCounts: Record<string, number>;
  notes: string[];
}

type GeoStatus = "idle" | "requesting" | "denied" | "unsupported" | "error";

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

export function HeroDiscoverySurface() {
  const [draft, setDraft] = useState("");
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("all");
  const [location, setLocation] = useState<{ lat: number; lng: number; label: string } | null>(null);
  const [view, setView] = useState<"map" | "list">("list");
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

  // Initial load: a broad blend (real products, plans, screening) before any input.
  useEffect(() => {
    void runSearch("", "all", null);
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

  return (
    <section
      aria-label="Get health services"
      className="flex h-full min-h-[30rem] flex-col overflow-hidden rounded-[1.6rem] border border-glass-border bg-glass-fill shadow-impilo-floating backdrop-blur-glass supports-[not(backdrop-filter:blur(0px))]:bg-glass-fallback [.low-blur_&]:bg-glass-fallback [.low-blur_&]:backdrop-blur-none"
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

        {/* Category chips */}
        <div className="flex gap-1.5 overflow-x-auto pb-1" role="tablist" aria-label="Discovery categories">
          {location ? (
            <span className="inline-flex min-h-8 shrink-0 items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-900">
              <Navigation className="h-3.5 w-3.5" aria-hidden />
              {location.label}
              <button type="button" aria-label="Clear location" onClick={() => setLocation(null)} className="rounded-full p-0.5 hover:bg-emerald-200/70">
                <X className="h-3 w-3" aria-hidden />
              </button>
            </span>
          ) : (
            <button
              type="button"
              onClick={requestLocation}
              disabled={geoStatus === "requesting"}
              className="inline-flex min-h-8 shrink-0 items-center gap-1.5 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 hover:border-emerald-300 hover:bg-emerald-50 disabled:opacity-60"
            >
              {geoStatus === "requesting" ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden /> : <Navigation className="h-3.5 w-3.5" aria-hidden />}
              Near me
            </button>
          )}
          {CATEGORIES.map((c) => (
            <button
              key={c.value}
              type="button"
              role="tab"
              aria-selected={category === c.value}
              onClick={() => pickCategory(c.value)}
              className={`min-h-8 shrink-0 rounded-full border px-2.5 py-1 text-xs font-medium ${
                category === c.value
                  ? "border-emerald-500 bg-emerald-600 text-white"
                  : "border-slate-200 bg-slate-50 text-slate-700 hover:border-emerald-300 hover:bg-emerald-50"
              }`}
            >
              {c.label}
            </button>
          ))}
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
          <div className="space-y-3">
            <div className="overflow-hidden rounded-xl border border-slate-200">
              <FindCareMap
                results={[]}
                geoMarkers={geoMarkers}
                origin={location ? { lat: location.lat, lng: location.lng } : null}
                height={300}
                hideCaption
              />
            </div>
            {geoMarkers.length === 0 && (
              <p className="px-1 text-xs text-slate-500">
                These results don&apos;t have map locations. Switch to List, or search care/pharmacies to see pins.
              </p>
            )}
          </div>
        ) : (
          <div className="max-h-[26rem] space-y-2.5 overflow-y-auto pr-1">
            {results.length > 0 ? (
              results.map((r, i) => (
                <div key={`${r.category}-${r.id ?? i}`} className="rounded-xl border border-slate-200 bg-white p-3">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <span className={`inline-block rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${CATEGORY_BADGE[r.category] ?? "bg-slate-100 text-slate-700"}`}>
                        {r.type}
                      </span>
                      <p className="mt-1 truncate font-semibold text-slate-900">{r.title}</p>
                      {r.subtitle && <p className="truncate text-xs text-slate-500">{r.subtitle}</p>}
                    </div>
                    {formatKm(r.distanceMeters) && (
                      <span className="shrink-0 text-xs font-medium text-slate-600">{formatKm(r.distanceMeters)}</span>
                    )}
                  </div>
                  {r.meta.length > 0 && (
                    <div className="mt-1.5 flex flex-wrap gap-1">
                      {r.meta.map((m) => (
                        <span key={m} className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-600">{m}</span>
                      ))}
                    </div>
                  )}
                  {r.href && r.actionLabel && (
                    <Link
                      href={r.href}
                      className="mt-2 inline-flex min-h-8 items-center rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-100"
                    >
                      {r.actionLabel}
                    </Link>
                  )}
                </div>
              ))
            ) : (
              !loading && (
                <p className="px-1 text-sm text-slate-500">
                  {data?.notes?.[0] ?? "Search a service, medicine or wellness need to see results."}
                </p>
              )
            )}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="flex items-center justify-between gap-2 border-t border-slate-100 px-4 py-2.5 text-xs sm:px-5">
        <span className="text-slate-500">
          {data ? `${results.length} result${results.length === 1 ? "" : "s"} · verified public data` : "Live national health data"}
        </span>
        <Link href="/welcome/find-care" className="font-semibold text-emerald-800 hover:text-emerald-950">
          Open full search →
        </Link>
      </div>
    </section>
  );
}
