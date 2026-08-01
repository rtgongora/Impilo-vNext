import { type ReactNode, useEffect } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Providers } from "./Providers";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";

const { getSession } = vi.hoisted(() => ({ getSession: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get: getSession, flushOfflineQueue: vi.fn() } }));

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

const FUTURE_EXPIRY = new Date(Date.now() + 60 * 60 * 1000).toISOString();
const PAST_EXPIRY = new Date(Date.now() - 60 * 60 * 1000).toISOString();

const authUser = {
  id: "user-1",
  email: "clinician@impilo.health",
  displayName: "Clinician",
  roles: ["CLINICIAN"],
  actorType: "PROVIDER" as const,
};

function activeSession(expiresAt = FUTURE_EXPIRY) {
  return {
    data: {
      authenticated: true as const,
      user: { ...authUser, identityAssuranceLevel: "VERIFIED" },
      acr: "urn:impilo:aal2",
      amr: ["pwd", "otp"],
      expiresAt,
    },
  };
}

/**
 * Expiry is computed relative to now, not hardcoded. Providers only hydrates a session whose
 * access token is still valid, so an absolute date silently turns the "authenticated" case into
 * the "expired" case once it passes — which is how this test began asserting that a valid session
 * hydrates while actually exercising the rejection path.
 */
function futureExpiry(): string {
  return new Date(Date.now() + 60 * 60 * 1000).toISOString();
}

/** Records what the store looked like on this child's first effect pass. */
function RestoreProbe({ seen }: { seen: boolean[] }) {
  useEffect(() => {
    seen.push(useAuthStore.getState().sessionRestoreAttempted);
  }, [seen]);
  return <div>probe</div>;
}

describe("Providers", () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth();
    useAuthStore.setState({ sessionRestoreAttempted: false });
    sessionStorageMock.clear();
    vi.clearAllMocks();
    getSession.mockRejectedValue(new Error("NO_ACTIVE_SESSION"));
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
    // Relative, not a literal date. This test previously pinned 2026-04-10 and began
    // failing the moment that passed — a green suite that quietly rots into a red one.
    sessionStorageMock.setItem("exp:auth_token", "token-1");
    sessionStorageMock.setItem("exp:expires_at", FUTURE_EXPIRY);
    sessionStorageMock.setItem("exp:auth_user", JSON.stringify(authUser));
    getSession.mockResolvedValueOnce(activeSession());
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
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().refreshToken).toBeNull();
    expect(useAuthStore.getState().expiresAt).toBe(FUTURE_EXPIRY);
    expect(useWorkspaceStore.getState().workspace).toBeNull();
    expect(useShiftStore.getState().shift).toBeNull();
    expect(sessionStorageMock.getItem("exp:workspace")).toBeNull();
    expect(sessionStorageMock.getItem("exp:shift")).toBeNull();
  });

  // The two properties a2a2e75de introduced when it replaced
  // `userStr && (token || hasSessionCookie)` with `userStr && token && !isExpired`.
  // document.cookie is mocked to "exp_has_session=1" for every test in this file, so
  // the first case is exactly the stale-cookie resurrection the change set out to stop.

  it("ignores browser session metadata when the BFF has no active session", async () => {
    sessionStorageMock.setItem("exp:auth_user", JSON.stringify(authUser));
    sessionStorageMock.setItem("exp:expires_at", FUTURE_EXPIRY);
    sessionStorageMock.setItem("exp:facility", JSON.stringify({
      id: "facility-1",
      name: "Central Hospital",
      code: "CH",
      facilityType: "Hospital",
      capabilities: ["queue"],
    }));

    render(
      <Providers>
        <div>ready</div>
      </Providers>,
    );
    await screen.findByText("ready");

    expect(useAuthStore.getState().token).toBeNull();
    expect(useFacilityStore.getState().facility).toBeNull();
  });

  it("ignores expired browser token metadata when the BFF has no active session", async () => {
    sessionStorageMock.setItem("exp:auth_token", "token-1");
    sessionStorageMock.setItem("exp:auth_user", JSON.stringify(authUser));
    sessionStorageMock.setItem("exp:expires_at", PAST_EXPIRY);
    sessionStorageMock.setItem("exp:facility", JSON.stringify({
      id: "facility-1",
      name: "Central Hospital",
      code: "CH",
      facilityType: "Hospital",
      capabilities: ["queue"],
    }));

    render(
      <Providers>
        <div>ready</div>
      </Providers>,
    );
    await screen.findByText("ready");

    expect(useAuthStore.getState().token).toBeNull();
    expect(useFacilityStore.getState().facility).toBeNull();
  });

  // The session restore happens in an effect on StoreHydrator, which is a *parent* of the
  // route guard, so React runs the guard's effect against an empty store first. The guard
  // used to read that emptiness as "signed out, citizen only" and redirect, which is why a
  // full page load of a guarded deep link never reached its page.

  it("children see the session as unrestored on their first pass, before the hydrator announces it", async () => {
    sessionStorageMock.setItem("exp:auth_token", "token-1");
    sessionStorageMock.setItem("exp:expires_at", futureExpiry());
    sessionStorageMock.setItem("exp:auth_user", JSON.stringify(authUser));
    getSession.mockResolvedValueOnce(activeSession(futureExpiry()));

    const seen: boolean[] = [];
    render(
      <Providers>
        <RestoreProbe seen={seen} />
      </Providers>,
    );
    await screen.findByText("probe");

    expect(seen[0]).toBe(false);
    await waitFor(() => {
      expect(useAuthStore.getState().sessionRestoreAttempted).toBe(true);
    });
    expect(useAuthStore.getState().isAuthenticated).toBe(true);
  });

  it("announces the restore even when the stored session cannot be read", async () => {
    sessionStorageMock.setItem("exp:auth_token", "token-1");
    sessionStorageMock.setItem("exp:expires_at", futureExpiry());
    sessionStorageMock.setItem("exp:auth_user", "{not json");

    render(
      <Providers>
        <div>ready</div>
      </Providers>,
    );
    await screen.findByText("ready");

    // Silence here would leave every guarded route waiting forever, i.e. ungated.
    await waitFor(() => {
      expect(useAuthStore.getState().sessionRestoreAttempted).toBe(true);
    });
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });
});
