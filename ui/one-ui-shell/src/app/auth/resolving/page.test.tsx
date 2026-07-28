import { act, render, cleanup } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ResolvingPage from "./page";

const { linkedIdsState, affiliationsState, workAssignmentsState, contractState } = vi.hoisted(() => ({
  linkedIdsState: { data: undefined as unknown, isLoading: true, isSuccess: false, isError: false },
  affiliationsState: { data: [] as unknown[], isSuccess: false },
  workAssignmentsState: { data: [] as unknown[], isSuccess: false },
  contractState: { contract: undefined as unknown },
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/components/brand/ImpiloBrandLogo", () => ({ ImpiloBrandLogo: () => null }));
vi.mock("@/components/intelligent/NompiloHint", () => ({ NompiloHint: () => null }));

vi.mock("@/hooks/queries/useAffiliations", () => ({
  useAffiliations: () => affiliationsState,
}));
vi.mock("@/hooks/queries/useWorkAssignments", () => ({
  useWorkAssignments: () => workAssignmentsState,
}));
vi.mock("@/hooks/queries/useLinkedIds", () => ({
  useLinkedIds: () => linkedIdsState,
}));
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "hid-1" }, activateProvider: vi.fn() }),
}));
vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: { getState: () => ({ hasFacility: false }) },
}));
vi.mock("@/hooks/useOperationalContextStore", () => ({
  useOperationalContextStore: { getState: () => ({ setOperationalMode: vi.fn() }) },
}));
vi.mock("@/hooks/useSessionExperienceContract", () => ({
  useSessionExperienceContract: () => contractState,
}));
vi.mock("@/lib/trust", () => ({
  sessionContractAllowsRoute: () => true,
}));
vi.mock("@/lib/resolve-post-login-destination", () => ({
  isSafeReturnTo: () => false,
  resolvePostLoginDestination: () => ({ href: "/home", restoredIntent: false }),
}));
vi.mock("@/lib/gateway-intent", () => ({
  consumeIntent: vi.fn(),
  peekIntent: () => null,
  resolveIntentDestination: () => null,
}));

describe("ResolvingPage (Phase F10 — deterministic timer)", () => {
  const originalAssign = window.location.assign;

  beforeEach(() => {
    vi.useFakeTimers();
    linkedIdsState.data = undefined;
    linkedIdsState.isLoading = true;
    linkedIdsState.isSuccess = false;
    linkedIdsState.isError = false;
    affiliationsState.data = [];
    affiliationsState.isSuccess = false;
    workAssignmentsState.data = [];
    workAssignmentsState.isSuccess = false;
    contractState.contract = undefined;
    // jsdom does not implement navigation; stub it so we can assert on it too.
    Object.defineProperty(window, "location", {
      writable: true,
      value: { ...window.location, assign: vi.fn() },
    });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    window.location.assign = originalAssign;
  });

  it("does not navigate before the 8s soft deadline while data is still resolving", async () => {
    render(<ResolvingPage />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(7999);
    });

    expect(window.location.assign).not.toHaveBeenCalled();
  });

  it("navigates at the 8s soft deadline even though re-renders happened along the way (the F10 regression)", async () => {
    const { rerender } = render(<ResolvingPage />);

    // Simulate the real login sequence: identity/affiliations/assignments/contract each arrive
    // at a different moment, each one forcing a re-render (and, before the fix, resetting the
    // fallback timer via `navigate`'s changing identity).
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    linkedIdsState.isLoading = false;
    linkedIdsState.isSuccess = true;
    linkedIdsState.data = { data: { attributes: {} } };
    rerender(<ResolvingPage />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    affiliationsState.isSuccess = true;
    affiliationsState.data = [{ id: "aff-1" }];
    rerender(<ResolvingPage />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    workAssignmentsState.isSuccess = true;
    rerender(<ResolvingPage />);

    // Total elapsed so far: 6000ms. The old bug re-armed a 5s timer on every re-render above,
    // so at this point it would still be ~2s from firing (or further reset). The fixed timer
    // was armed once at mount and needs only 2000ms more to reach the 8000ms soft deadline —
    // contract is deliberately left unresolved so this exercises the deadline, not the
    // data-resolved landing path.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    expect(window.location.assign).toHaveBeenCalledTimes(1);
  });

  it("has a hard 12s ceiling independent of the soft deadline having already fired", async () => {
    render(<ResolvingPage />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(8000);
    });
    expect(window.location.assign).toHaveBeenCalledTimes(1);

    // navigate() is idempotent (hasNavigated guard) — the hard timer firing afterwards must
    // not cause a second navigation.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000);
    });
    expect(window.location.assign).toHaveBeenCalledTimes(1);
  });

  it("navigates immediately once all data (including the contract) resolves, without waiting for either deadline", async () => {
    linkedIdsState.isLoading = false;
    linkedIdsState.isSuccess = true;
    affiliationsState.isSuccess = true;
    workAssignmentsState.isSuccess = true;
    contractState.contract = { authenticated: true, defaultRoute: "/home" };

    await act(async () => {
      render(<ResolvingPage />);
    });

    expect(window.location.assign).toHaveBeenCalledWith("/home");
  });
});
