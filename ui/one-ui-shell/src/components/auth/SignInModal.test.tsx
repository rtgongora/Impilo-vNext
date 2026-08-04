import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act, cleanup, waitFor } from "@testing-library/react";
import { SignInModal } from "./SignInModal";
import { SIGN_IN_COMPLETE_MESSAGE } from "@/lib/auth/sign-in-window";

/**
 * The popup → opener handoff is where a provider sign-in silently died in the preview
 * estate (2026-08-04). The server trace was flawless — authorize, callback, session, work
 * fan-out — but the main window navigated on a stale auth store, and the route guard bounced
 * it back to /auth/login. From the person's side: OTP accepted, popup closed, sign-in form
 * again, no error anywhere.
 *
 * The property under test: the completion message must HYDRATE THE STORE FIRST and only then
 * navigate; and if hydration fails, fall back to a full page load (which re-runs
 * StoreHydrator) rather than a client-side replace that would bounce.
 */

const routerReplace = vi.fn();
const routerRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: routerReplace, refresh: routerRefresh, push: vi.fn() }),
}));

const restoreSessionIntoStore = vi.fn();
vi.mock("@/lib/api-client", () => ({
  restoreSessionIntoStore: () => restoreSessionIntoStore(),
}));

function fakePopup(): Window {
  return { closed: false, close: vi.fn(), focus: vi.fn() } as unknown as Window;
}

async function completeSignIn(destination = "/home", origin = window.location.origin) {
  await act(async () => {
    window.dispatchEvent(new MessageEvent("message", {
      data: { type: SIGN_IN_COMPLETE_MESSAGE, destination },
      origin,
    }));
  });
}

describe("SignInModal completion handoff", () => {
  const originalOpen = window.open;
  const originalLocation = window.location;
  const locationAssign = vi.fn();

  beforeEach(() => {
    window.open = vi.fn(() => fakePopup());
    // jsdom's location.assign is non-writable, so swap the whole object — keeping origin
    // identical, because the component's postMessage origin check depends on it.
    Object.defineProperty(window, "location", {
      value: { ...originalLocation, origin: originalLocation.origin, assign: locationAssign },
      writable: true,
      configurable: true,
    });
    locationAssign.mockReset();
    restoreSessionIntoStore.mockReset();
    routerReplace.mockReset();
    routerRefresh.mockReset();
  });

  afterEach(() => {
    cleanup();
    window.open = originalOpen;
    Object.defineProperty(window, "location", {
      value: originalLocation,
      writable: true,
      configurable: true,
    });
  });

  it("hydrates the auth store BEFORE navigating, so the route guard sees the session", async () => {
    let resolveRestore = (_ok: boolean) => {};
    restoreSessionIntoStore.mockReturnValue(new Promise((r) => { resolveRestore = r; }));
    render(<SignInModal authorizeUrl="/internal/v1/auth/oidc/authorize?x=1" onClose={vi.fn()} />);

    await completeSignIn("/work");

    // Restore is in flight: navigating now is exactly the bug — the guard would read the
    // stale store and bounce to /auth/login.
    expect(restoreSessionIntoStore).toHaveBeenCalledTimes(1);
    expect(routerReplace).not.toHaveBeenCalled();

    await act(async () => resolveRestore(true));

    expect(routerReplace).toHaveBeenCalledWith("/work");
    expect(routerRefresh).toHaveBeenCalled();
    expect(locationAssign).not.toHaveBeenCalled();
  });

  it("falls back to a full page load when hydration fails — StoreHydrator re-runs there", async () => {
    restoreSessionIntoStore.mockResolvedValue(false);
    render(<SignInModal authorizeUrl="/internal/v1/auth/oidc/authorize?x=1" onClose={vi.fn()} />);

    await completeSignIn("/work");

    await waitFor(() => expect(locationAssign).toHaveBeenCalledWith("/work"));
    expect(routerReplace).not.toHaveBeenCalled();
  });

  it("ignores completion messages from a foreign origin", async () => {
    restoreSessionIntoStore.mockResolvedValue(true);
    render(<SignInModal authorizeUrl="/internal/v1/auth/oidc/authorize?x=1" onClose={vi.fn()} />);

    await completeSignIn("/work", "https://attacker.example");

    expect(restoreSessionIntoStore).not.toHaveBeenCalled();
    expect(routerReplace).not.toHaveBeenCalled();
    expect(locationAssign).not.toHaveBeenCalled();
  });

  it("refuses an absolute-URL destination and lands on /home instead", async () => {
    restoreSessionIntoStore.mockResolvedValue(true);
    render(<SignInModal authorizeUrl="/internal/v1/auth/oidc/authorize?x=1" onClose={vi.fn()} />);

    await completeSignIn("https://attacker.example/phish");

    await waitFor(() => expect(routerReplace).toHaveBeenCalledWith("/home"));
  });

  it("still renders the blocked-popup fallback when window.open returns null", () => {
    window.open = vi.fn(() => null);
    render(<SignInModal authorizeUrl="/internal/v1/auth/oidc/authorize?x=1" onClose={vi.fn()} />);
    expect(screen.getByText(/blocked the sign-in window/i)).toBeInTheDocument();
  });
});
