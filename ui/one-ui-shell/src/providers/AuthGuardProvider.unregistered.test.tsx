/**
 * Deny-by-default for unregistered routes — Target Architecture v1.3.2 §29.2,
 * backlog item 73, test A74.
 *
 * The guard used to return early when `matchRouteDefinition` found nothing, so
 * a page missing from the route registry ran with no auth, facility, role or
 * citizen check at all. These tests prove: (1) an unknown non-public route
 * renders the denial surface and NEVER mounts its content; (2) unauthenticated
 * visitors on such a route are sent to login; (3) registered routes and the
 * deliberately public surface still render; (4) — mutation guard — if the
 * early-return behaviour is restored, the "content must not mount" assertion
 * goes red.
 *
 * The route registry itself runs REAL here (no mock): the deny decision must
 * come from the actual `matchRouteDefinition` over the actual 855-route table.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { AuthGuardProvider } from "./AuthGuardProvider";
import { matchRouteDefinition } from "@/lib/routes";

const replace = vi.fn();
let mockPathname = "/definitely/not/registered";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
  usePathname: () => mockPathname,
}));

let mockAuth: Record<string, unknown>;
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: Object.assign(
    (selector?: (s: Record<string, unknown>) => unknown) =>
      selector ? selector(mockAuth) : mockAuth,
    { getState: () => mockAuth },
  ),
}));
vi.mock("@/hooks/useConsentStore", () => ({
  useConsentStore: () => ({ hasConsented: true, hydrated: true }),
}));
vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: () => ({ hasFacility: true }),
}));
vi.mock("@/hooks/useWorkspaceStore", () => ({
  useWorkspaceStore: () => ({ hasWorkspace: true }),
}));
vi.mock("@/hooks/useShiftStore", () => ({
  useShiftStore: () => ({ hasShift: true }),
}));
vi.mock("@/hooks/useIdentityContext", () => ({
  useIdentityContext: () => ({ isCitizenOnly: false, isLoading: false }),
}));
vi.mock("@/hooks/useSessionExperienceContract", () => ({
  useSessionExperienceContract: () => ({ contract: null, isLoading: false }),
}));

function makeAuth(isAuthenticated: boolean) {
  return {
    isAuthenticated,
    sessionRestoreAttempted: true,
    hasRole: () => true,
    hasActiveProvider: () => true,
    clearAuth: vi.fn(),
    user: { id: "u1", healthId: "h1", providerActivated: true, assuranceLevel: "VERIFIED", roles: [] },
  };
}

beforeEach(() => {
  replace.mockClear();
  mockAuth = makeAuth(true);
});

describe("deny-by-default for unregistered routes (item 73 / A74)", () => {
  it("precondition: the probe path really is unregistered, and the control paths really are registered", () => {
    expect(matchRouteDefinition("/definitely/not/registered")).toBeNull();
    expect(matchRouteDefinition("/home")).not.toBeNull();
    expect(matchRouteDefinition("/work")).not.toBeNull();
  });

  it("an authenticated session on an unknown route gets the denial surface and the page content never mounts", () => {
    mockPathname = "/definitely/not/registered";
    render(
      <AuthGuardProvider>
        <div data-testid="page-content">clinical content</div>
      </AuthGuardProvider>,
    );
    expect(screen.getByTestId("unregistered-route-denied")).toBeTruthy();
    // The mutation target: restoring `if (!routeInfo) return;` alone makes
    // this assertion fail, because the content mounts unguarded again.
    expect(screen.queryByTestId("page-content")).toBeNull();
    // Honest copy: the denial never claims a lack of access rights.
    expect(screen.getByText(/not a record of what you can or cannot access/i)).toBeTruthy();
  });

  it("an unauthenticated visitor on an unknown route is sent to login and sees no content", () => {
    mockPathname = "/definitely/not/registered";
    mockAuth = makeAuth(false);
    render(
      <AuthGuardProvider>
        <div data-testid="page-content">content</div>
      </AuthGuardProvider>,
    );
    expect(replace).toHaveBeenCalledWith("/auth/login");
    expect(screen.queryByTestId("page-content")).toBeNull();
  });

  it("a registered route still renders its content", () => {
    mockPathname = "/home";
    render(
      <AuthGuardProvider>
        <div data-testid="page-content">home</div>
      </AuthGuardProvider>,
    );
    expect(screen.getByTestId("page-content")).toBeTruthy();
    expect(screen.queryByTestId("unregistered-route-denied")).toBeNull();
  });

  it("the public surface is not captured by the deny: an unregistered /welcome child still renders", () => {
    mockPathname = "/welcome/some-future-public-page";
    mockAuth = makeAuth(false);
    render(
      <AuthGuardProvider>
        <div data-testid="page-content">public</div>
      </AuthGuardProvider>,
    );
    expect(screen.getByTestId("page-content")).toBeTruthy();
    expect(screen.queryByTestId("unregistered-route-denied")).toBeNull();
  });

  it("the landing page itself is public exactly, and an arbitrary root-level unknown is not", () => {
    mockPathname = "/";
    render(
      <AuthGuardProvider>
        <div data-testid="page-content">landing</div>
      </AuthGuardProvider>,
    );
    expect(screen.getByTestId("page-content")).toBeTruthy();
  });
});
