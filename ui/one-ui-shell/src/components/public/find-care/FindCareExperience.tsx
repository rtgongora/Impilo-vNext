"use client";

/**
 * Citizen "Find Health Services" — need-first, service-aware access-to-care search.
 *
 * A person says what they need in plain language (or taps a service chip), can
 * optionally share their location (never blocked on permission), and gets unified
 * result cards ranked by service-match then nearness. A persistent, editable
 * journey summary means the search never restarts; the journey store keeps the
 * need/location/filters/selection across sign-in and browser back.
 *
 * No facility or provider truth is hard-coded here — every result comes from the
 * anonymous find-care lane (TUSO registry + Ndila routing via the BFF).
 */

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import {
  Search,
  Loader2,
  MapPin,
  Navigation,
  List,
  Map as MapIcon,
  X,
  Pencil,
  AlertTriangle,
  Info,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";
import type { CareResult, CareSearchResponse } from "@/lib/find-care/types";
import { SERVICE_CHIPS, PROVINCES, serviceTokenLabel } from "@/lib/find-care/service-chips";
import { useFindCareJourneyStore } from "@/hooks/useFindCareJourneyStore";
import { FindCareResultCard } from "./FindCareResultCard";
import { FindCareMap } from "./FindCareMap";

const PAGE_SIZE = 10;

type GeoStatus = "idle" | "requesting" | "denied" | "unsupported" | "error";

interface SearchError {
  code: "RATE_LIMITED" | "VALIDATION" | "UNAVAILABLE";
  message: string;
}

export function FindCareExperience() {
  const {
    need,
    serviceToken,
    serviceLabel,
    location,
    filters,
    view,
    results,
    selectedFacilityId,
    hydrated,
    setNeed,
    setService,
    setLocation,
    clearLocation,
    setFilters,
    setView,
    setResults,
    setSelectedFacility,
    reset,
    hydrate,
  } = useFindCareJourneyStore();

  const [needDraft, setNeedDraft] = useState(need);
  const [page, setPage] = useState(results?.page ?? 0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<SearchError | null>(null);
  const [geoStatus, setGeoStatus] = useState<GeoStatus>("idle");
  const autoRan = useRef(false);

  // Hydrate the persisted journey once on mount.
  useEffect(() => {
    hydrate();
  }, [hydrate]);

  // Keep the free-text draft in sync when the store hydrates or a chip clears it.
  useEffect(() => {
    setNeedDraft(need);
  }, [need]);

  const runSearch = useCallback(
    async (nextPage: number) => {
      const activeService = useFindCareJourneyStore.getState().serviceToken;
      const activeNeed = useFindCareJourneyStore.getState().need.trim();
      const activeLocation = useFindCareJourneyStore.getState().location;
      const activeFilters = useFindCareJourneyStore.getState().filters;

      if (!activeService && !activeNeed && !activeFilters.province && !activeFilters.district) {
        setError({ code: "VALIDATION", message: "Tell us what care you need, or choose a province, to search." });
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
        params.set("page", String(nextPage));
        params.set("size", String(PAGE_SIZE));

        const res = await apiClient.get<CareSearchResponse>(
          `/internal/v1/public/gateway/find-care/search?${params.toString()}`,
        );
        setResults(res);
        setPage(res.page ?? nextPage);
      } catch (err) {
        const code = (err as { error?: { code?: string }; status?: number })?.error?.code;
        const status = (err as { status?: number })?.status;
        if (code === "RATE_LIMITED" || status === 429) {
          setError({
            code: "RATE_LIMITED",
            message: "You've searched a lot in a short time. Please wait a moment and try again.",
          });
        } else if (code === "VALIDATION" || status === 400) {
          setError({
            code: "VALIDATION",
            message: "That search couldn't be understood. Try a service like \"maternity\", \"x-ray\" or \"pharmacy\".",
          });
        } else {
          setError({
            code: "UNAVAILABLE",
            message: "We couldn't search for care just now. Please try again shortly.",
          });
        }
      } finally {
        setLoading(false);
      }
    },
    [setResults],
  );

  // After hydration, if there's an active query but no cached results, run once so a
  // fresh reload resumes the journey instead of showing an empty page.
  useEffect(() => {
    if (!hydrated || autoRan.current) return;
    autoRan.current = true;
    const hasQuery = Boolean(serviceToken) || need.trim().length > 0 || Boolean(filters.province);
    if (hasQuery && !results) {
      void runSearch(0);
    }
  }, [hydrated, serviceToken, need, filters.province, results, runSearch]);

  function submitFreeText(e: React.FormEvent) {
    e.preventDefault();
    setNeed(needDraft.trim());
    // setNeed clears serviceToken; run on the next tick with the fresh store value.
    setTimeout(() => void runSearch(0), 0);
  }

  function chooseChip(token: string, label: string) {
    if (serviceToken === token) {
      setService(null, null);
      return;
    }
    setService(token, label);
    setTimeout(() => void runSearch(0), 0);
  }

  function requestLocation() {
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
        setTimeout(() => void runSearch(0), 0);
      },
      (err) => {
        setGeoStatus(err.code === err.PERMISSION_DENIED ? "denied" : "error");
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }

  const activeQueryLabel =
    serviceLabel ?? serviceTokenLabel(serviceToken) ?? (need.trim() || null);
  const hasActiveJourney = Boolean(activeQueryLabel || filters.province || location);
  const total = results?.total ?? 0;
  const hasNext = results ? (results.page + 1) * results.size < results.total : false;

  return (
    <div className="space-y-6">
      {/* ── Need-first search ─────────────────────────────────────────── */}
      <section className="rounded-2xl border border-slate-200 bg-white p-5 sm:p-6" aria-labelledby="find-care-heading">
        <h2 id="find-care-heading" className="text-lg font-semibold text-slate-900">
          What care or service are you looking for?
        </h2>
        <p className="mt-1 text-sm text-slate-600">
          Search in your own words, or tap a service below. No sign-in needed.
        </p>

        <form className="mt-4 flex flex-wrap gap-2" onSubmit={submitFreeText} role="search">
          <label htmlFor="find-care-need" className="sr-only">
            What care or service are you looking for?
          </label>
          <div className="relative min-w-56 flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <input
              id="find-care-need"
              type="search"
              value={needDraft}
              onChange={(e) => setNeedDraft(e.target.value)}
              placeholder="e.g. maternity, X-ray, HIV medicines, dialysis…"
              className="w-full rounded-lg border border-slate-300 py-2.5 pl-9 pr-3 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-60"
          >
            {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Search className="h-4 w-4" aria-hidden />}
            Search
          </button>
        </form>

        {/* Quick service chips */}
        <div className="mt-4">
          <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Common services</p>
          <div className="mt-2 flex flex-wrap gap-2" role="group" aria-label="Quick service filters">
            {SERVICE_CHIPS.map((chip) => {
              const selected = serviceToken === chip.token;
              return (
                <button
                  key={chip.token}
                  type="button"
                  aria-pressed={selected}
                  onClick={() => chooseChip(chip.token, chip.label)}
                  className={`rounded-full border px-3 py-1.5 text-sm transition-colors ${
                    selected
                      ? "border-emerald-600 bg-emerald-600 text-white"
                      : "border-slate-300 bg-white text-slate-700 hover:border-emerald-400 hover:bg-emerald-50"
                  }`}
                >
                  {chip.hint && <span aria-hidden className="mr-1">{chip.hint}</span>}
                  {chip.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Location + area — optional, never blocking */}
        <div className="mt-5 rounded-xl bg-slate-50 p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex items-start gap-2">
              <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" aria-hidden />
              <div>
                <p className="text-sm font-medium text-slate-800">Where are you?</p>
                <p className="text-xs text-slate-500">
                  Sharing your location sorts results by how near they are. It stays on your device and
                  is only used for this search — you can search without it.
                </p>
              </div>
            </div>
            {location ? (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-3 py-1 text-xs font-medium text-emerald-800">
                <Navigation className="h-3.5 w-3.5" aria-hidden />
                {location.label ?? "Location shared"}
                <button
                  type="button"
                  onClick={() => {
                    clearLocation();
                    setTimeout(() => void runSearch(0), 0);
                  }}
                  className="ml-1 rounded-full p-0.5 hover:bg-emerald-200"
                  aria-label="Remove shared location"
                >
                  <X className="h-3 w-3" aria-hidden />
                </button>
              </span>
            ) : (
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={requestLocation}
                  disabled={geoStatus === "requesting"}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-60"
                >
                  {geoStatus === "requesting" ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden />
                  ) : (
                    <Navigation className="h-3.5 w-3.5" aria-hidden />
                  )}
                  Use my location
                </button>
              </div>
            )}
          </div>

          {(geoStatus === "denied" || geoStatus === "unsupported" || geoStatus === "error") && (
            <p className="mt-2 text-xs text-slate-500">
              {geoStatus === "denied"
                ? "Location permission was declined — that's fine. Choose a province and district below instead."
                : "Your device didn't share a location. Choose a province and district below instead."}
            </p>
          )}

          {/* Manual area selection — always available, never depends on permission. */}
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            <div>
              <label htmlFor="find-care-province" className="text-xs font-medium text-slate-600">
                Province
              </label>
              <select
                id="find-care-province"
                value={filters.province}
                onChange={(e) => {
                  setFilters({ province: e.target.value });
                  setTimeout(() => void runSearch(0), 0);
                }}
                className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
              >
                <option value="">All provinces</option>
                {PROVINCES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="find-care-district" className="text-xs font-medium text-slate-600">
                District (optional)
              </label>
              <input
                id="find-care-district"
                type="text"
                value={filters.district}
                onChange={(e) => setFilters({ district: e.target.value })}
                onBlur={() => void runSearch(0)}
                placeholder="e.g. Gweru"
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
              />
            </div>
          </div>
        </div>
      </section>

      {/* ── Persistent, editable journey summary ──────────────────────── */}
      {hasActiveJourney && (
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
          <Info className="h-4 w-4 shrink-0 text-emerald-600" aria-hidden />
          <span className="font-medium">Looking for:</span>
          <span>{activeQueryLabel ?? "Any service"}</span>
          {(location || filters.province) && (
            <>
              <span aria-hidden className="text-emerald-400">·</span>
              <span className="font-medium">Near:</span>
              <span>{location?.label ?? filters.province}</span>
            </>
          )}
          <span aria-hidden className="text-emerald-400">·</span>
          <span className="text-emerald-700">Opening hours shown as unverified</span>
          <button
            type="button"
            onClick={() => document.getElementById("find-care-need")?.focus()}
            className="ml-auto inline-flex items-center gap-1 rounded-md px-2 py-1 font-medium text-emerald-700 hover:bg-emerald-100"
          >
            <Pencil className="h-3.5 w-3.5" aria-hidden />
            Edit
          </button>
          <button
            type="button"
            onClick={() => {
              reset();
              setNeedDraft("");
              autoRan.current = true;
            }}
            className="inline-flex items-center gap-1 rounded-md px-2 py-1 font-medium text-emerald-700 hover:bg-emerald-100"
          >
            Start over
          </button>
        </div>
      )}

      {/* ── Errors ────────────────────────────────────────────────────── */}
      {error && (
        <div
          className={`flex items-start gap-2 rounded-xl border px-4 py-3 text-sm ${
            error.code === "UNAVAILABLE"
              ? "border-red-200 bg-red-50 text-red-800"
              : "border-amber-200 bg-amber-50 text-amber-800"
          }`}
          role="alert"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <span>{error.message}</span>
        </div>
      )}

      {/* ── Results ───────────────────────────────────────────────────── */}
      {results && (
        <section aria-live="polite" aria-label="Search results" className="space-y-4">
          {/* Honest orchestration notes (no location, stale hours, straight-line distance…). */}
          {results.notes.length > 0 && (
            <ul className="space-y-1.5 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
              {results.notes.map((note, i) => (
                <li key={i} className="flex items-start gap-2">
                  <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-400" aria-hidden />
                  <span>{note}</span>
                </li>
              ))}
            </ul>
          )}

          {results.emergencyIntent && (
            <div className="flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800" role="alert">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <span>
                This looks like an emergency. If someone needs urgent help now, go to the nearest
                hospital or{" "}
                <Link href="/welcome/emergency" className="font-semibold underline">
                  see emergency information
                </Link>
                .
              </span>
            </div>
          )}

          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-slate-600">
              {total === 0
                ? "No facilities matched. Try a different service, a shorter word, or another province."
                : `${total} facilit${total === 1 ? "y" : "ies"} found${
                    results.distanceAvailable ? " · sorted by nearest" : " · sorted by best match"
                  }`}
            </p>
            {total > 0 && (
              <div className="inline-flex overflow-hidden rounded-lg border border-slate-300" role="group" aria-label="View mode">
                <button
                  type="button"
                  aria-pressed={view === "list"}
                  onClick={() => setView("list")}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium ${
                    view === "list" ? "bg-slate-900 text-white" : "bg-white text-slate-700 hover:bg-slate-50"
                  }`}
                >
                  <List className="h-4 w-4" aria-hidden />
                  List
                </button>
                <button
                  type="button"
                  aria-pressed={view === "map"}
                  onClick={() => setView("map")}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium ${
                    view === "map" ? "bg-slate-900 text-white" : "bg-white text-slate-700 hover:bg-slate-50"
                  }`}
                >
                  <MapIcon className="h-4 w-4" aria-hidden />
                  Map
                </button>
              </div>
            )}
          </div>

          {/* Map view = spatial overview on top; full action-parity cards render below regardless. */}
          {view === "map" && total > 0 && (
            <FindCareMap
              results={results.results}
              origin={location ? { lat: location.lat, lng: location.lng } : null}
              selectedFacilityId={selectedFacilityId}
            />
          )}

          <div className="space-y-3">
            {results.results.map((r: CareResult, i: number) => (
              <FindCareResultCard
                key={r.facilityId ?? `${r.name}-${i}`}
                result={r}
                locationShared={Boolean(location)}
                onOpen={setSelectedFacility}
                active={selectedFacilityId != null && r.facilityId === selectedFacilityId}
              />
            ))}
          </div>

          {total > 0 && (page > 0 || hasNext) && (
            <div className="flex items-center justify-center gap-2">
              <button
                type="button"
                onClick={() => void runSearch(page - 1)}
                disabled={loading || page === 0}
                className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium disabled:opacity-40"
              >
                Previous
              </button>
              <span className="text-sm text-slate-500">Page {page + 1}</span>
              <button
                type="button"
                onClick={() => void runSearch(page + 1)}
                disabled={loading || !hasNext}
                className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium disabled:opacity-40"
              >
                Next
              </button>
            </div>
          )}

          <p className="text-xs text-slate-500">
            To save a facility or book an appointment,{" "}
            <Link href="/auth/login" className="font-medium text-emerald-700 hover:text-emerald-800">
              sign in
            </Link>{" "}
            or{" "}
            <Link href="/auth/register/contact" className="font-medium text-emerald-700 hover:text-emerald-800">
              create an account
            </Link>
            . Your search is kept so you can pick up where you left off.
          </p>
        </section>
      )}
    </div>
  );
}
