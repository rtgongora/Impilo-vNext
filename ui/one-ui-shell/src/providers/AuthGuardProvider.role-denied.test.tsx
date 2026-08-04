/**
 * DENIED is a distinct product state for a failed role guard
 * (CLAUDE_GOVERNANCE.md #19).
 *
 * A failed `role` guard used to call `router.replace("/home")`: the page
 * mounted, the guard fired, and the person was bounced with no message. That is
 * why a citizen offered `/coverage` (the ADMIN payer console) reported "the
 * Coverage page doesn't open" instead of a permissions problem.
 *
 * These tests prove: (1) the refusal is stated and the page content never
 * mounts; (2) it does NOT redirect any more; (3) it fires only where the guard
 * chain really would have reached the role case — hydration, login, consent,
 * assurance, boundary and contract-visibility all outrank it, and none of them
 * may be rendered as a refusal; (4) an admitted role still renders.
 *
 * The route registry and role groups run REAL here (no mock): the decision must
 * come from the actual `matchRouteDefinition` and the actual `ROLE_GROUPS`.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { AuthGuardProvider } from "./AuthGuardProvider";
import { matchRouteDefinition } from "@/lib/routes";
import { evaluateRoleDenial } from "@/lib/auth/role-denial";

const replace = vi.fn();
let mockPathname = "/coverage";

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

let mockConsent = { hasConsented: true, hydrated: true };
vi.mock("@/hooks/useConsentStore", () => ({
  useConsentStore: () => mockConsent,
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

let mockIdentity = { isCitizenOnly: false, isLoading: false };
vi.mock("@/hooks/useIdentityContext", () => ({
  useIdentityContext: () => mockIdentity,
}));
vi.mock("@/hooks/useSessionExperienceContract", () => ({
  useSessionExperienceContract: () => ({ contract: null, isLoading: false }),
}));

/** A citizen: authenticated, consented, verified — and holding no admin role. */
function citizen(overrides: Record<string, unknown> = {}) {
  return {
    isAuthenticated: true,
    sessionRestoreAttempted: true,
    hasRole: (r: string) => r === "CITIZEN",
    hasActiveProvider: () => false,
    clearAuth: vi.fn(),
    user: {
      id: "u1",
      healthId: "h1",
      providerActivated: false,
      assuranceLevel: "VERIFIED",
      roles: ["CITIZEN"],
    },
    ...overrides,
  };
}

function admin() {
  return {
    ...citizen(),
    hasRole: (r: string) => r === "SYSTEM_ADMIN",
    user: {
      id: "u2",
      healthId: "h2",
      providerActivated: true,
      assuranceLevel: "VERIFIED",
      roles: ["SYSTEM_ADMIN"],
    },
  };
}

function renderGuard() {
  return render(
    <AuthGuardProvider>
      <div data-testid="page-content">payer console</div>
    </AuthGuardProvider>,
  );
}

beforeEach(() => {
  replace.mockClear();
  mockAuth = citizen();
  mockPathname = "/coverage";
  mockConsent = { hasConsented: true, hydrated: true };
  mockIdentity = { isCitizenOnly: false, isLoading: false };
});

describe("role denial is an explicit state, not a silent bounce", () => {
  it("precondition: /coverage really is a role-guarded ADMIN route, and the citizen really fails it", () => {
    const route = matchRouteDefinition("/coverage");
    expect(route).not.toBeNull();
    expect(route!.guard).toBe("role");
    expect(route!.requiredRole).toBe("ADMIN");
    // The instrument itself: a CITIZEN must not satisfy ADMIN, an admin must.
    expect(
      evaluateRoleDenial({
        pathname: "/coverage",
        sessionRestoreAttempted: true,
        isAuthenticated: true,
        consentHydrated: true,
        hasConsented: true,
        assuranceAllows: true,
        boundaryAllows: true,
        visibility: "allow",
        hasRole: (r) => r === "CITIZEN",
      }).denied,
    ).toBe(true);
    expect(
      evaluateRoleDenial({
        pathname: "/coverage",
        sessionRestoreAttempted: true,
        isAuthenticated: true,
        consentHydrated: true,
        hasConsented: true,
        assuranceAllows: true,
        boundaryAllows: true,
        visibility: "allow",
        hasRole: (r) => r === "SYSTEM_ADMIN",
      }).denied,
    ).toBe(false);
  });

  it("states the refusal, and the page content never mounts", () => {
    renderGuard();
    expect(screen.getByTestId("role-denied")).toBeTruthy();
    // The mutation target: restoring `router.replace("/home")` in the role case
    // makes this assertion fail, because nothing is rendered in its place.
    expect(screen.queryByTestId("page-content")).toBeNull();
  });

  it("does not redirect — the person stays on the page that refused them", () => {
    renderGuard();
    expect(replace).not.toHaveBeenCalled();
  });

  it("names the page and pre-empts the wrong remedy, without naming the role", () => {
    renderGuard();
    // The refusal is attributed to permissions, not to a fault.
    expect(screen.getByText(/permissions decision, not a fault/i)).toBeTruthy();
    // Step-up is the remedy people try first, and it cannot work for a role.
    expect(screen.getByText(/verifying your identity, will not change this/i)).toBeTruthy();
    // The page is identified by its product title, from the registry.
    expect(screen.getByText(/Coverage Operations/)).toBeTruthy();
    // The Keycloak role token is not printed.
    expect(document.body.textContent).not.toMatch(/\bADMIN\b/);
    expect(document.body.textContent).not.toMatch(/SYSTEM_ADMIN/);
  });

  it("an admitted role still renders the page", () => {
    mockAuth = admin();
    renderGuard();
    expect(screen.getByTestId("page-content")).toBeTruthy();
    expect(screen.queryByTestId("role-denied")).toBeNull();
  });

  it("a non-role-guarded route is untouched", () => {
    mockPathname = "/home";
    renderGuard();
    expect(screen.getByTestId("page-content")).toBeTruthy();
    expect(screen.queryByTestId("role-denied")).toBeNull();
  });
});

describe("LOADING and the earlier gates must never be rendered as DENIED", () => {
  it("before the session is restored, nothing is refused", () => {
    mockAuth = citizen({ sessionRestoreAttempted: false });
    renderGuard();
    expect(screen.queryByTestId("role-denied")).toBeNull();
  });

  it("an unauthenticated visitor goes to login and is not told they lack a role", () => {
    mockAuth = citizen({ isAuthenticated: false });
    renderGuard();
    expect(replace).toHaveBeenCalledWith("/auth/login");
    expect(screen.queryByTestId("role-denied")).toBeNull();
  });

  it("an unconsented session goes to consent and is not told they lack a role", () => {
    mockConsent = { hasConsented: false, hydrated: true };
    renderGuard();
    expect(replace).toHaveBeenCalledWith(expect.stringContaining("/consent"));
    expect(screen.queryByTestId("role-denied")).toBeNull();
  });

  it("while consent is still hydrating, nothing is refused", () => {
    mockConsent = { hasConsented: false, hydrated: false };
    renderGuard();
    expect(screen.queryByTestId("role-denied")).toBeNull();
  });

  it("while the identity read is in flight on a citizen-blocked route, nothing is refused", () => {
    mockIdentity = { isCitizenOnly: true, isLoading: true };
    renderGuard();
    expect(screen.queryByTestId("role-denied")).toBeNull();
  });
});

describe("evaluateRoleDenial precedence (unit)", () => {
  const base = {
    pathname: "/coverage",
    sessionRestoreAttempted: true,
    isAuthenticated: true,
    consentHydrated: true,
    hasConsented: true,
    assuranceAllows: true,
    boundaryAllows: true,
    visibility: "allow" as const,
    hasRole: (r: string) => r === "CITIZEN",
  };

  it("denies at the baseline", () => {
    expect(evaluateRoleDenial(base).denied).toBe(true);
  });

  it.each([
    ["session not restored", { sessionRestoreAttempted: false }],
    ["not authenticated", { isAuthenticated: false }],
    ["consent not hydrated", { consentHydrated: false }],
    ["not consented", { hasConsented: false }],
    ["assurance gate would redirect", { assuranceAllows: false }],
    ["boundary gate would redirect", { boundaryAllows: false }],
    ["visibility is wait", { visibility: "wait" as const }],
    ["visibility is block", { visibility: "block" as const }],
  ])("yields to an earlier gate: %s", (_label, override) => {
    expect(evaluateRoleDenial({ ...base, ...override }).denied).toBe(false);
  });

  it("an unregistered route is not a role denial — that is the registry's surface", () => {
    expect(evaluateRoleDenial({ ...base, pathname: "/definitely/not/registered" }).denied).toBe(false);
  });
});
