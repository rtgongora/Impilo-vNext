import { describe, it, expect, beforeEach, vi } from "vitest";
import { useFacilityStore, type FacilityContext } from "../useFacilityStore";

// Mock sessionStorage
const sessionStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => { store[key] = value; }),
    removeItem: vi.fn((key: string) => { delete store[key]; }),
    clear: vi.fn(() => { store = {}; }),
    get length() { return Object.keys(store).length; },
    key: vi.fn((index: number) => Object.keys(store)[index] ?? null),
  };
})();

Object.defineProperty(global, "sessionStorage", { value: sessionStorageMock });

const testFacility: FacilityContext = {
  id: "FAC-001",
  name: "Harare Central Hospital",
  code: "HCH",
  facilityType: "HOSPITAL",
  capabilities: ["INPATIENT", "OUTPATIENT", "EMERGENCY"],
};

describe("useFacilityStore", () => {
  beforeEach(() => {
    useFacilityStore.setState({
      facility: null,
      hasFacility: false,
    });
    sessionStorageMock.clear();
    vi.clearAllMocks();
  });

  it("has no facility in initial state", () => {
    const state = useFacilityStore.getState();
    expect(state.facility).toBeNull();
    expect(state.hasFacility).toBe(false);
  });

  it("setFacility updates facility and hasFacility", () => {
    useFacilityStore.getState().setFacility(testFacility);

    const state = useFacilityStore.getState();
    expect(state.facility).toEqual(testFacility);
    expect(state.hasFacility).toBe(true);
  });

  it("setFacility persists to sessionStorage", () => {
    useFacilityStore.getState().setFacility(testFacility);

    expect(sessionStorageMock.setItem).toHaveBeenCalledWith(
      "exp:facility",
      JSON.stringify(testFacility)
    );
  });

  it("setFacility stores all facility fields correctly", () => {
    useFacilityStore.getState().setFacility(testFacility);

    const { facility } = useFacilityStore.getState();
    expect(facility?.id).toBe("FAC-001");
    expect(facility?.name).toBe("Harare Central Hospital");
    expect(facility?.code).toBe("HCH");
    expect(facility?.facilityType).toBe("HOSPITAL");
    expect(facility?.capabilities).toEqual(["INPATIENT", "OUTPATIENT", "EMERGENCY"]);
  });

  it("clearFacility resets facility state", () => {
    useFacilityStore.getState().setFacility(testFacility);
    expect(useFacilityStore.getState().hasFacility).toBe(true);

    useFacilityStore.getState().clearFacility();

    const state = useFacilityStore.getState();
    expect(state.facility).toBeNull();
    expect(state.hasFacility).toBe(false);
  });

  it("clearFacility removes from sessionStorage", () => {
    useFacilityStore.getState().setFacility(testFacility);
    vi.clearAllMocks();

    useFacilityStore.getState().clearFacility();

    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:facility");
  });

  it("can replace facility with a different one", () => {
    useFacilityStore.getState().setFacility(testFacility);

    const otherFacility: FacilityContext = {
      id: "FAC-002",
      name: "Bulawayo Clinic",
      code: "BUL",
      facilityType: "CLINIC",
      capabilities: ["OUTPATIENT"],
    };

    useFacilityStore.getState().setFacility(otherFacility);

    const state = useFacilityStore.getState();
    expect(state.facility?.id).toBe("FAC-002");
    expect(state.facility?.name).toBe("Bulawayo Clinic");
    expect(state.hasFacility).toBe(true);
  });
});
