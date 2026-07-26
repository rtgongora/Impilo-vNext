"use client";

/**
 * Below-the-fold discovery sections for the public landing.
 *
 * Both read the same anonymous discovery lane the hero surface uses
 * (GET /internal/v1/public/gateway/discovery/search) rather than inventing a second
 * contract, and both render only what the lane actually returned: no placeholder
 * facilities, no invented "open now", no provider count that nothing produced. When a
 * lane has nothing to give, the section says so and offers the action that changes it.
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, Navigation, Video } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import type { DiscoveryResponse, DiscoveryResult } from "./HeroDiscoverySurface";

function formatKm(m: number | null): string | null {
  if (m == null) return null;
  return m >= 1000 ? `${(m / 1000).toFixed(m < 10000 ? 1 : 0)} km` : `${Math.round(m)} m`;
}

async function searchDiscovery(params: Record<string, string>): Promise<DiscoveryResponse> {
  const qs = new URLSearchParams(params).toString();
  return apiClient.get<DiscoveryResponse>(
    `/internal/v1/public/gateway/discovery/search?${qs}`,
  );
}

function DiscoveryRow({ result }: { result: DiscoveryResult }) {
  return (
    <li className="rounded-xl border border-slate-200 bg-white p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700">
        {result.type}
      </p>
      <p className="mt-1 font-semibold text-slate-900">{result.title}</p>
      {result.subtitle && <p className="mt-0.5 text-sm text-slate-600">{result.subtitle}</p>}
      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        {formatKm(result.distanceMeters) && (
          <span className="rounded-md bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
            {formatKm(result.distanceMeters)}
            {result.etaMinutes != null && ` · ~${result.etaMinutes} min`}
          </span>
        )}
        {result.availability && (
          <span className="rounded-md bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-800">
            {result.availability}
          </span>
        )}
        {result.meta.slice(0, 3).map((m) => (
          <span key={m} className="rounded-md bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
            {m}
          </span>
        ))}
      </div>
      {result.href && result.actionLabel && (
        <Link
          href={result.href}
          className="mt-3 inline-flex min-h-10 items-center rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-800 hover:bg-emerald-100"
        >
          {result.actionLabel}
        </Link>
      )}
    </li>
  );
}

/**
 * Find care near you. Deliberately does NOT geolocate on load: the find-care lane
 * needs a location before it returns anything, and taking one unprompted would be a
 * permission grab on a page the person is still deciding whether to trust.
 */
export function PublicNearYouSection() {
  const [state, setState] = useState<"idle" | "locating" | "loading" | "done" | "blocked" | "error">(
    "idle",
  );
  const [results, setResults] = useState<DiscoveryResult[]>([]);
  const [notes, setNotes] = useState<string[]>([]);

  const findNearby = useCallback(() => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setState("blocked");
      return;
    }
    setState("locating");
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        setState("loading");
        try {
          const res = await searchDiscovery({
            category: "care",
            lat: pos.coords.latitude.toFixed(5),
            lng: pos.coords.longitude.toFixed(5),
            size: "6",
          });
          setResults(res.results ?? []);
          setNotes(res.notes ?? []);
          setState("done");
        } catch {
          setState("error");
        }
      },
      (err) => setState(err.code === err.PERMISSION_DENIED ? "blocked" : "error"),
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }, []);

  return (
    <div>
      {state === "idle" && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <p className="text-base text-slate-700">
            Share your location once and Impilo will show the facilities, clinics and pharmacies
            closest to you, with distance and travel time where the register holds them.
          </p>
          <button
            type="button"
            onClick={findNearby}
            className="mt-4 inline-flex min-h-11 items-center gap-2 rounded-xl bg-emerald-700 px-4 py-2.5 text-sm font-bold text-white hover:bg-emerald-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-700 focus-visible:ring-offset-2"
          >
            <Navigation className="h-4 w-4" aria-hidden />
            Show care near me
          </button>
        </div>
      )}

      {(state === "locating" || state === "loading") && (
        <p className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          {state === "locating" ? "Waiting for your location…" : "Finding care near you…"}
        </p>
      )}

      {state === "blocked" && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <p className="text-base text-slate-700">
            Location isn&apos;t available on this device or is blocked for this site. Searching by
            name works just as well.
          </p>
          <Link
            href="/welcome/find-care"
            className="mt-4 inline-flex min-h-11 items-center rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-800 hover:border-emerald-300 hover:bg-emerald-50"
          >
            Search care by name
          </Link>
        </div>
      )}

      {state === "error" && (
        <p
          role="alert"
          className="rounded-2xl border border-amber-200 bg-amber-50 p-6 text-sm text-amber-900"
        >
          Care search is temporarily unavailable. Please try again shortly.
        </p>
      )}

      {state === "done" && (
        <>
          {results.length > 0 ? (
            <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {results.map((r, i) => (
                <DiscoveryRow key={`${r.category}-${r.id ?? i}`} result={r} />
              ))}
            </ul>
          ) : (
            <div className="rounded-2xl border border-slate-200 bg-white p-6">
              <p className="text-base text-slate-700">
                {notes[0] ?? "No facilities were returned for your area."}
              </p>
            </div>
          )}
          <Link
            href="/welcome/find-care"
            className="mt-4 inline-flex min-h-11 items-center rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-800 hover:border-emerald-300 hover:bg-emerald-50"
          >
            View all nearby care
          </Link>
        </>
      )}
    </div>
  );
}

/**
 * Virtual care. Lists the ACTIVE virtual services the registry actually publishes —
 * it needs no query or location, so unlike the near-you section it can load on mount.
 * Starting a consultation requires identity, so the action is an honest sign-in route.
 */
export function PublicVirtualCareSection() {
  const [results, setResults] = useState<DiscoveryResult[]>([]);
  const [notes, setNotes] = useState<string[]>([]);
  const [state, setState] = useState<"loading" | "done" | "error">("loading");

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const res = await searchDiscovery({ category: "virtual", size: "6" });
        if (cancelled) return;
        setResults(res.results ?? []);
        setNotes(res.notes ?? []);
        setState("done");
      } catch {
        if (!cancelled) setState("error");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (state === "loading") {
    return (
      <p className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-600">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
        Checking which virtual services are live…
      </p>
    );
  }

  if (state === "error") {
    return (
      <p
        role="alert"
        className="rounded-2xl border border-amber-200 bg-amber-50 p-6 text-sm text-amber-900"
      >
        Virtual care listings are temporarily unavailable. Please try again shortly.
      </p>
    );
  }

  if (results.length === 0) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6">
        <p className="text-base text-slate-700">
          {notes[0] ??
            "No virtual services are published yet. When a virtual clinic goes live it will appear here."}
        </p>
      </div>
    );
  }

  return (
    <>
      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {results.map((r, i) => (
          <DiscoveryRow key={`${r.category}-${r.id ?? i}`} result={r} />
        ))}
      </ul>
      <p className="mt-3 flex items-center gap-2 text-sm text-slate-600">
        <Video className="h-4 w-4 text-violet-600" aria-hidden />
        Starting a consultation needs a signed-in account so your clinician can see who they are
        treating.
      </p>
    </>
  );
}
