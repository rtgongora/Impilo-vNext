/**
 * InactivityLockProvider — the lock must actually fire.
 *
 * Target Architecture v1.3.2 §29.3 / backlog item 82 / test A88: the shipped
 * exemption list contained a bare "/" matched with `startsWith`, so every
 * pathname was exempt and the privacy lock had NEVER executed on any route.
 * These tests prove (1) the exemption function classifies correctly, (2) the
 * lock and logout timers genuinely run on an authenticated non-exempt route,
 * and (3) — the mutation-style negative — the exact broken idiom would fail
 * this suite, so reintroducing it goes red.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act } from "@testing-library/react";
import {
  InactivityLockProvider,
  isExemptFromInactivityLock,
} from "./InactivityLockProvider";

const replace = vi.fn();
let mockPathname = "/work/queue";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
  usePathname: () => mockPathname,
}));

const clearAuth = vi.fn();
let mockIsAuthenticated = true;

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ isAuthenticated: mockIsAuthenticated, clearAuth }),
}));

const setLevel = vi.fn();
vi.mock("@/hooks/usePrivacyDisplayStore", () => ({
  usePrivacyDisplayStore: {
    getState: () => ({ level: "FULL", setLevel }),
  },
}));

describe("isExemptFromInactivityLock", () => {
  it("exempts the public landing page exactly, not every path that starts with /", () => {
    expect(isExemptFromInactivityLock("/")).toBe(true);
    // The defect made ALL of these exempt. They must not be.
    expect(isExemptFromInactivityLock("/work")).toBe(false);
    expect(isExemptFromInactivityLock("/work/queue")).toBe(false);
    expect(isExemptFromInactivityLock("/ehr/patient-1")).toBe(false);
    expect(isExemptFromInactivityLock("/home")).toBe(false);
  });

  it("keeps the genuinely public and auth surfaces exempt", () => {
    for (const p of [
      "/welcome",
      "/welcome/find-care",
      "/auth/login",
      "/privacy",
      "/terms",
      "/consent",
      "/account-deletion",
      "/kiosk",
    ]) {
      expect(isExemptFromInactivityLock(p), p).toBe(true);
    }
  });

  it("mutation guard: the broken bare-slash idiom misclassifies a clinical route — this pins the fix", () => {
    // This is the exact expression that shipped. If someone reverts
    // isExemptFromInactivityLock to it, the assertions above go red; this
    // test documents the failure mode so the diff reviewer sees WHY.
    const EXEMPT_PREFIXES = ["/", "/welcome", "/auth"];
    const broken = (pathname: string) =>
      EXEMPT_PREFIXES.some((p) => pathname.startsWith(p));
    expect(broken("/work/queue")).toBe(true); // the defect: everything exempt
    expect(isExemptFromInactivityLock("/work/queue")).toBe(false); // the fix
  });
});

describe("InactivityLockProvider timers", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockPathname = "/work/queue";
    mockIsAuthenticated = true;
    replace.mockClear();
    clearAuth.mockClear();
    setLevel.mockClear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("locks after the inactivity threshold on an authenticated non-exempt route", async () => {
    render(
      <InactivityLockProvider>
        <div data-testid="content">sensitive</div>
      </InactivityLockProvider>,
    );
    expect(screen.queryByText("Session Locked")).toBeNull();

    // 5 minutes idle + one 10s poll tick
    await act(async () => {
      vi.advanceTimersByTime(5 * 60 * 1000 + 10_000);
    });

    expect(screen.getByText("Session Locked")).toBeTruthy();
    expect(setLevel).toHaveBeenCalledWith("MINIMAL"); // PII hidden while locked
  });

  it("signs out after the logout countdown while locked", async () => {
    render(
      <InactivityLockProvider>
        <div>sensitive</div>
      </InactivityLockProvider>,
    );
    await act(async () => {
      vi.advanceTimersByTime(5 * 60 * 1000 + 10_000); // reach lock
    });
    expect(screen.getByText("Session Locked")).toBeTruthy();

    await act(async () => {
      vi.advanceTimersByTime(15 * 60 * 1000 + 1_000); // exhaust countdown
    });

    expect(clearAuth).toHaveBeenCalled();
    expect(replace).toHaveBeenCalledWith("/auth/login?reason=inactivity");
  });

  it("never locks on an exempt public route", async () => {
    mockPathname = "/welcome/find-care";
    render(
      <InactivityLockProvider>
        <div>public</div>
      </InactivityLockProvider>,
    );
    await act(async () => {
      vi.advanceTimersByTime(30 * 60 * 1000);
    });
    expect(screen.queryByText("Session Locked")).toBeNull();
  });

  it("never locks when unauthenticated", async () => {
    mockIsAuthenticated = false;
    render(
      <InactivityLockProvider>
        <div>anon</div>
      </InactivityLockProvider>,
    );
    await act(async () => {
      vi.advanceTimersByTime(30 * 60 * 1000);
    });
    expect(screen.queryByText("Session Locked")).toBeNull();
  });
});
