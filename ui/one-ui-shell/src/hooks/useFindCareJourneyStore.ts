/**
 * Experience UI — Find Care Journey Store (Zustand)
 *
 * Preserves the citizen's access-to-care journey so it never restarts: the
 * need/service they asked for, the location they shared (or chose), the filters
 * they set, the facility they were looking at, the list/map view, and a
 * `returnTo` path for sign-in continuity ("save this facility" → sign in → come
 * back here). Search results and free-text health needs remain session memory only.
 *
 * A deliberately minimal, expiring continuity envelope is persisted so a full
 * reload can resume a chosen public service without leaving sensitive health text,
 * precise coordinates, or full API responses indefinitely on a shared device.
 */

import { create } from "zustand";
import type { CareSearchResponse } from "@/lib/find-care/types";

/** Bumped when the persisted shape changes; a mismatch discards stale state. */
export const FIND_CARE_JOURNEY_VERSION = "2026-08-01";
export const FIND_CARE_JOURNEY_TTL_MS = 30 * 60 * 1000;

const STORAGE_KEY = "exp:find_care_journey";
const STORAGE_VERSION_KEY = "exp:find_care_journey_version";

export interface FindCareLocation {
  lat: number;
  lng: number;
  /** How the person supplied it — drives the on-screen wording. */
  source: "device" | "manual";
  /** Human label, e.g. "Your current location" or a province/district. */
  label?: string;
}

export interface FindCareFilters {
  province: string;
  district: string;
  facilityType: string;
  /** Max distance in km ("" = any). Client-side refinement — only bites when a location is shared. */
  radiusKm: string;
}

export type FindCareView = "list" | "map";

/** The persisted slice (everything except transient hydration flags). */
interface FindCareJourneySnapshot {
  need: string;
  serviceToken: string | null;
  serviceLabel: string | null;
  location: FindCareLocation | null;
  filters: FindCareFilters;
  selectedFacilityId: number | null;
  view: FindCareView;
  returnTo: string | null;
  results: CareSearchResponse | null;
}

interface PersistedFindCareJourney {
  savedAt: number;
  serviceToken: string | null;
  serviceLabel: string | null;
  location: FindCareLocation | null;
  filters: FindCareFilters;
  selectedFacilityId: number | null;
  view: FindCareView;
  returnTo: string | null;
}

interface FindCareJourneyState extends FindCareJourneySnapshot {
  /** True once hydrate() has run (avoids SSR/client flash before localStorage read). */
  hydrated: boolean;

  setNeed: (need: string) => void;
  /** Set an explicit service chip (token + human label). Clears free-text need. */
  setService: (token: string | null, label: string | null) => void;
  setLocation: (location: FindCareLocation | null) => void;
  clearLocation: () => void;
  setFilters: (filters: Partial<FindCareFilters>) => void;
  setSelectedFacility: (facilityId: number | null) => void;
  setView: (view: FindCareView) => void;
  setReturnTo: (returnTo: string | null) => void;
  setResults: (results: CareSearchResponse | null) => void;
  /** Clear the whole journey (used by "start over"). */
  reset: () => void;
  /** Hydrate from localStorage. Safe to call repeatedly. */
  hydrate: () => void;
}

const EMPTY_FILTERS: FindCareFilters = { province: "", district: "", facilityType: "", radiusKm: "" };

const INITIAL_SNAPSHOT: FindCareJourneySnapshot = {
  need: "",
  serviceToken: null,
  serviceLabel: null,
  location: null,
  filters: EMPTY_FILTERS,
  selectedFacilityId: null,
  view: "map",
  returnTo: null,
  results: null,
};

function persist(snapshot: FindCareJourneySnapshot): void {
  if (typeof window === "undefined") return;
  try {
    const location = snapshot.location
      ? {
          ...snapshot.location,
          lat: Number(snapshot.location.lat.toFixed(2)),
          lng: Number(snapshot.location.lng.toFixed(2)),
        }
      : null;
    const envelope: PersistedFindCareJourney = {
      savedAt: Date.now(),
      serviceToken: snapshot.serviceToken,
      serviceLabel: snapshot.serviceLabel,
      location,
      filters: snapshot.filters,
      selectedFacilityId: snapshot.selectedFacilityId,
      view: snapshot.view,
      returnTo:
        snapshot.returnTo?.startsWith("/") && !snapshot.returnTo.startsWith("//")
          ? snapshot.returnTo
          : null,
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(envelope));
    localStorage.setItem(STORAGE_VERSION_KEY, FIND_CARE_JOURNEY_VERSION);
  } catch {
    // Storage may be full or blocked (private mode). The in-memory store still
    // carries the journey for this session — persistence is best-effort.
  }
}

function snapshotOf(state: FindCareJourneyState): FindCareJourneySnapshot {
  return {
    need: state.need,
    serviceToken: state.serviceToken,
    serviceLabel: state.serviceLabel,
    location: state.location,
    filters: state.filters,
    selectedFacilityId: state.selectedFacilityId,
    view: state.view,
    returnTo: state.returnTo,
    results: state.results,
  };
}

export const useFindCareJourneyStore = create<FindCareJourneyState>((set, get) => {
  /** Apply a partial update, then persist the resulting snapshot. */
  const update = (partial: Partial<FindCareJourneySnapshot>) => {
    set(partial);
    persist(snapshotOf(get()));
  };

  return {
    ...INITIAL_SNAPSHOT,
    hydrated: false,

    setNeed: (need) => update({ need, serviceToken: null, serviceLabel: null }),

    setService: (serviceToken, serviceLabel) =>
      update({ serviceToken, serviceLabel, need: "" }),

    setLocation: (location) => update({ location }),

    clearLocation: () => update({ location: null }),

    setFilters: (filters) => update({ filters: { ...get().filters, ...filters } }),

    setSelectedFacility: (selectedFacilityId) => update({ selectedFacilityId }),

    setView: (view) => update({ view }),

    setReturnTo: (returnTo) => update({ returnTo }),

    setResults: (results) => update({ results }),

    reset: () => {
      if (typeof window !== "undefined") {
        try {
          localStorage.removeItem(STORAGE_KEY);
          localStorage.removeItem(STORAGE_VERSION_KEY);
        } catch {
          /* best-effort */
        }
      }
      set({ ...INITIAL_SNAPSHOT });
    },

    hydrate: () => {
      if (typeof window === "undefined") return;
      try {
        const version = localStorage.getItem(STORAGE_VERSION_KEY);
        const stored = localStorage.getItem(STORAGE_KEY);
        if (!stored || version !== FIND_CARE_JOURNEY_VERSION) {
          if (stored) {
            localStorage.removeItem(STORAGE_KEY);
            localStorage.removeItem(STORAGE_VERSION_KEY);
          }
          set({ hydrated: true });
          return;
        }
        const parsed = JSON.parse(stored) as Partial<PersistedFindCareJourney>;
        if (
          typeof parsed.savedAt !== "number" ||
          Date.now() - parsed.savedAt > FIND_CARE_JOURNEY_TTL_MS
        ) {
          localStorage.removeItem(STORAGE_KEY);
          localStorage.removeItem(STORAGE_VERSION_KEY);
          set({ ...INITIAL_SNAPSHOT, hydrated: true });
          return;
        }
        set({
          need: "",
          serviceToken: parsed.serviceToken ?? null,
          serviceLabel: parsed.serviceLabel ?? null,
          location: parsed.location ?? null,
          filters: { ...EMPTY_FILTERS, ...(parsed.filters ?? {}) },
          selectedFacilityId: parsed.selectedFacilityId ?? null,
          view: parsed.view === "list" ? "list" : "map",
          returnTo:
            parsed.returnTo?.startsWith("/") && !parsed.returnTo.startsWith("//")
              ? parsed.returnTo
              : null,
          results: null,
          hydrated: true,
        });
      } catch {
        set({ hydrated: true });
      }
    },
  };
});
