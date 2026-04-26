import { describe, it, expect, beforeEach, vi } from "vitest";
import { useOperationalContextStore } from "../useOperationalContextStore";

const sessionStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
    get length() {
      return Object.keys(store).length;
    },
    key: vi.fn((index: number) => Object.keys(store)[index] ?? null),
  };
})();

Object.defineProperty(global, "sessionStorage", { value: sessionStorageMock });

describe("useOperationalContextStore", () => {
  beforeEach(() => {
    sessionStorageMock.clear();
    vi.clearAllMocks();
    useOperationalContextStore.setState({
      operationalMode: "my_life",
      facilityWorkSubcontext: null,
      registryAdminSubtype: null,
      organizationAdminSurface: null,
    });
  });

  it("setOperationalMode persists to sessionStorage", () => {
    useOperationalContextStore.getState().setOperationalMode("facility_work");
    expect(sessionStorageMock.setItem).toHaveBeenCalledWith("exp:operational_mode", "facility_work");
    expect(useOperationalContextStore.getState().operationalMode).toBe("facility_work");
  });

  it("setFacilityWorkSubcontext persists", () => {
    useOperationalContextStore.getState().setFacilityWorkSubcontext("triage");
    expect(sessionStorageMock.setItem).toHaveBeenCalledWith("exp:facility_work_subcontext", "triage");
    expect(useOperationalContextStore.getState().facilityWorkSubcontext).toBe("triage");
  });

  it("setRegistryAdminSubtype persists", () => {
    useOperationalContextStore.getState().setRegistryAdminSubtype("client_registry");
    expect(sessionStorageMock.setItem).toHaveBeenCalledWith(
      "exp:registry_admin_subtype",
      "client_registry",
    );
  });

  it("setOrganizationAdminSurface persists", () => {
    useOperationalContextStore.getState().setOrganizationAdminSurface("facility_admin");
    expect(sessionStorageMock.setItem).toHaveBeenCalledWith(
      "exp:organization_admin_surface",
      "facility_admin",
    );
  });

  it("ensureDefaultFromUser respects remembered mode", () => {
    sessionStorageMock.setItem("exp:operational_mode", "registry_admin");
    useOperationalContextStore.getState().ensureDefaultFromUser({
      roles: ["SYSTEM_ADMIN"],
      actorType: "OPERATOR",
    });
    expect(useOperationalContextStore.getState().operationalMode).toBe("registry_admin");
  });

  it("ensureDefaultFromUser applies suggestion when no mode stored", () => {
    useOperationalContextStore.getState().ensureDefaultFromUser({
      roles: ["CLINICIAN"],
      actorType: "PROVIDER",
    });
    expect(useOperationalContextStore.getState().operationalMode).toBe("facility_work");
    expect(sessionStorageMock.setItem).toHaveBeenCalledWith("exp:operational_mode", "facility_work");
  });

  it("reset clears session keys and state", () => {
    useOperationalContextStore.getState().setOperationalMode("organization_admin");
    useOperationalContextStore.getState().setFacilityWorkSubcontext("pharmacy");
    vi.clearAllMocks();

    useOperationalContextStore.getState().reset();

    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:operational_mode");
    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:organization_admin_surface");
    expect(useOperationalContextStore.getState().operationalMode).toBe("my_life");
    expect(useOperationalContextStore.getState().facilityWorkSubcontext).toBeNull();
  });
});
