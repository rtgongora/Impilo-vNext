import { type ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Providers } from "./Providers";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";

vi.mock("next/navigation", () => ({
  usePathname: () => "/",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("./AuthGuardProvider", () => ({
  AuthGuardProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

vi.mock("./ExperienceEntryProvider", () => ({
  ExperienceEntryProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
  useExperienceEntry: () => ({
    facility: null,
    workspace: null,
    stage: "ready",
  }),
}));

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
    key: vi.fn((index: number) => Object.keys(store)[index] ?? null),
    get length() {
      return Object.keys(store).length;
    },
  };
})();

Object.defineProperty(global, "sessionStorage", { value: sessionStorageMock });
Object.defineProperty(document, "cookie", {
  configurable: true,
  get: vi.fn(() => "exp_has_session=1"),
  set: vi.fn(),
});

const authUser = {
  id: "user-1",
  email: "clinician@impilo.health",
  displayName: "Clinician",
  roles: ["CLINICIAN"],
  actorType: "PROVIDER" as const,
};

/**
 * Expiry is computed relative to now, not hardcoded. Providers only hydrates a session whose
 * access token is still valid, so an absolute date silently turns the "authenticated" case into
 * the "expired" case once it passes — which is how this test began asserting that a valid session
 * hydrates while actually exercising the rejection path.
 */
function futureExpiry(): string {
  return new Date(Date.now() + 60 * 60 * 1000).toISOString();
}

describe("Providers", () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth();
    sessionStorageMock.clear();
    vi.clearAllMocks();
    window.history.pushState({}, "", "/");
  });

  it("clears stale facility continuity when no authenticated session is present", async () => {
    sessionStorageMock.setItem("exp:facility", JSON.stringify({
      id: "facility-1",
      name: "Central Hospital",
      code: "CH",
      facilityType: "Hospital",
      capabilities: ["queue"],
    }));
    sessionStorageMock.setItem("exp:workspace", JSON.stringify({
      id: "workspace-1",
      name: "OPD",
      workspaceType: "CONSULT",
      facilityId: "facility-1",
    }));
    sessionStorageMock.setItem("exp:shift", JSON.stringify({
      id: "shift-1",
      startedAt: "2026-04-09T08:00:00Z",
      workspaceId: "workspace-1",
      facilityId: "facility-1",
    }));

    render(
      <Providers>
        <div>ready</div>
      </Providers>,
    );

    await screen.findByText("ready");

    expect(useFacilityStore.getState().facility).toBeNull();
    expect(useWorkspaceStore.getState().workspace).toBeNull();
    expect(useShiftStore.getState().shift).toBeNull();
    expect(sessionStorageMock.getItem("exp:facility")).toBeNull();
    expect(sessionStorageMock.getItem("exp:workspace")).toBeNull();
    expect(sessionStorageMock.getItem("exp:shift")).toBeNull();
  });

  it("hydrates only valid facility, workspace, and shift continuity for an authenticated session", async () => {
    // Hydration requires a non-expired access token. The cookie-only path this test used to rely
    // on was removed deliberately in a2a2e75de because it could resurrect a stale identity, so
    // "authenticated" now means a real token in session storage.
    const expiresAt = futureExpiry();
    sessionStorageMock.setItem("exp:auth_token", "token-1");
    sessionStorageMock.setItem("exp:expires_at", expiresAt);
    sessionStorageMock.setItem("exp:auth_user", JSON.stringify(authUser));
    sessionStorageMock.setItem("exp:facility", JSON.stringify({
      id: "facility-1",
      name: "Central Hospital",
      code: "CH",
      facilityType: "Hospital",
      capabilities: ["queue"],
    }));
    sessionStorageMock.setItem("exp:workspace", JSON.stringify({
      id: "workspace-1",
      name: "OPD",
      workspaceType: "CONSULT",
      facilityId: "facility-2",
    }));
    sessionStorageMock.setItem("exp:shift", JSON.stringify({
      id: "shift-1",
      startedAt: "2026-04-09T08:00:00Z",
      workspaceId: "workspace-1",
      facilityId: "facility-1",
    }));

    render(
      <Providers>
        <div>ready</div>
      </Providers>,
    );

    await screen.findByText("ready");

    await waitFor(() => {
      expect(useFacilityStore.getState().facility?.id).toBe("facility-1");
    });
    expect(useAuthStore.getState().token).toBe("token-1");
    expect(useAuthStore.getState().refreshToken).toBeNull();
    expect(useAuthStore.getState().expiresAt).toBe(expiresAt);
    expect(useWorkspaceStore.getState().workspace).toBeNull();
    expect(useShiftStore.getState().shift).toBeNull();
    expect(sessionStorageMock.getItem("exp:workspace")).toBeNull();
    expect(sessionStorageMock.getItem("exp:shift")).toBeNull();
  });
});
