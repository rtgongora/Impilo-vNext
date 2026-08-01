import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  FIND_CARE_JOURNEY_TTL_MS,
  FIND_CARE_JOURNEY_VERSION,
  useFindCareJourneyStore,
} from "./useFindCareJourneyStore";

const STORAGE_KEY = "exp:find_care_journey";
const VERSION_KEY = "exp:find_care_journey_version";

describe("useFindCareJourneyStore continuity", () => {
  beforeEach(() => {
    localStorage.clear();
    useFindCareJourneyStore.getState().reset();
    vi.restoreAllMocks();
  });

  it("defaults new journeys to the map", () => {
    expect(useFindCareJourneyStore.getState().view).toBe("map");
  });

  it("persists only a coarse, expiring continuity envelope", () => {
    useFindCareJourneyStore.getState().setNeed("I need help for a sensitive condition");
    useFindCareJourneyStore.getState().setLocation({
      lat: -17.829123,
      lng: 31.052987,
      source: "device",
      label: "Your current location",
    });
    useFindCareJourneyStore.getState().setReturnTo("/welcome/find-care/12");

    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}") as Record<string, unknown>;
    expect(localStorage.getItem(VERSION_KEY)).toBe(FIND_CARE_JOURNEY_VERSION);
    expect(stored).not.toHaveProperty("need");
    expect(stored).not.toHaveProperty("results");
    expect(stored.location).toMatchObject({ lat: -17.83, lng: 31.05 });
    expect(stored.returnTo).toBe("/welcome/find-care/12");
    expect(typeof stored.savedAt).toBe("number");
  });

  it("discards expired continuity and unsafe return targets", () => {
    localStorage.setItem(VERSION_KEY, FIND_CARE_JOURNEY_VERSION);
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        savedAt: Date.now() - FIND_CARE_JOURNEY_TTL_MS - 1,
        serviceToken: "general_outpatient",
        serviceLabel: "General / outpatient",
        location: null,
        filters: {},
        selectedFacilityId: 12,
        view: "list",
        returnTo: "//malicious.example",
      }),
    );

    useFindCareJourneyStore.setState({ hydrated: false });
    useFindCareJourneyStore.getState().hydrate();

    expect(useFindCareJourneyStore.getState()).toMatchObject({
      hydrated: true,
      serviceToken: null,
      selectedFacilityId: null,
      view: "map",
      returnTo: null,
    });
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});
