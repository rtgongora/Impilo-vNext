import type { ReactNode } from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import LoginPage from "./page";

const push = vi.fn();
const replace = vi.fn();
const refresh = vi.fn();
const { beginOidcLogin, buildOidcLoginUrl } = vi.hoisted(() => ({
  beginOidcLogin: vi.fn(),
  buildOidcLoginUrl: vi.fn(() => "/internal/v1/auth/oidc/authorize?stub=1"),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace, refresh }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/components/AuthLayout", () => ({
  AuthLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/lib/auth/web-session", () => ({ beginOidcLogin, buildOidcLoginUrl }));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    isAuthenticated: false,
    setAuth: vi.fn(),
  }),
}));

vi.mock("@/hooks/useConsentStore", () => ({
  useConsentStore: () => ({
    acceptConsent: vi.fn(),
  }),
  CURRENT_CONSENT_VERSION: "2026-04-11",
}));

vi.mock("@/hooks/useWorkModeStore", () => ({
  useWorkModeStore: Object.assign(vi.fn(), {
    getState: () => ({ deriveFromRoles: vi.fn() }),
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({ data: [] }),
  },
  // SignInModal hydrates the auth store from the BFF session BEFORE navigating — without
  // this, the destination's route guard read a stale "signed out" store and bounced the
  // freshly signed-in person straight back to /auth/login.
  restoreSessionIntoStore: vi.fn().mockResolvedValue(true),
}));

describe("LoginPage — Progressive Auth Scene", () => {
  it("renders Step 1 identifier input and Express Entry Intent Selector", () => {
    render(<LoginPage />);

    expect(screen.getByText("Sign in to Impilo")).toBeInTheDocument();
    expect(screen.getByTestId("express-intent-selector")).toBeInTheDocument();
    expect(screen.getByLabelText(/Email, phone number, or Impilo ID/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Continue/i })).toBeInTheDocument();
  });

  it("hands credentials to Keycloak without rendering a password input", () => {
    const open = vi.fn(() => ({ focus: vi.fn(), close: vi.fn(), closed: false }));
    vi.stubGlobal("open", open);

    render(<LoginPage />);

    const input = screen.getByLabelText(/Email, phone number, or Impilo ID/i);
    fireEvent.change(input, { target: { value: "mapfumo@mohcc.gov.zw" } });
    fireEvent.click(screen.getByRole("button", { name: /Continue/i }));

    // The shell must never own a password field: the credential step belongs to Keycloak.
    expect(screen.queryByLabelText("Password")).not.toBeInTheDocument();
    expect(buildOidcLoginUrl).toHaveBeenCalledWith({
      returnTo: "/auth/complete?to=%2Fhome",
      loginHint: "mapfumo@mohcc.gov.zw",
      requiredAcr: null,
    });

    // A separate WINDOW, not a frame — so the address bar stays visible while a password is
    // typed. If this ever regresses to an iframe the origin becomes uncheckable to the user.
    expect(open).toHaveBeenCalledWith(
      "/internal/v1/auth/oidc/authorize?stub=1",
      "impilo-sign-in",
      expect.stringContaining("popup=yes"),
    );

    vi.unstubAllGlobals();
  });

  it("ignores a sign-in completion claimed by any other origin", async () => {
    vi.stubGlobal("open", vi.fn(() => ({ focus: vi.fn(), close: vi.fn(), closed: false })));
    render(<LoginPage />);

    fireEvent.change(screen.getByLabelText(/Email, phone number, or Impilo ID/i), {
      target: { value: "mapfumo@mohcc.gov.zw" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Continue/i }));
    replace.mockClear();

    // Without the origin check any document could assert that a sign-in succeeded and steer
    // this shell wherever it liked. Same payload, foreign origin — must be ignored.
    fireEvent(window, new MessageEvent("message", {
      origin: "https://not-impilo.example",
      data: { type: "impilo:sign-in-complete", destination: "/organization-admin" },
    }));
    expect(replace).not.toHaveBeenCalled();

    fireEvent(window, new MessageEvent("message", {
      origin: window.location.origin,
      data: { type: "impilo:sign-in-complete", destination: "/home" },
    }));
    // Navigation now happens only after the session has been hydrated into the store, so the
    // replace is one microtask away rather than synchronous.
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/home"));

    vi.unstubAllGlobals();
  });

  it("shows registration and guest continuation options", () => {
    render(<LoginPage />);

    expect(screen.getByRole("link", { name: /Create an account/i })).toHaveAttribute(
      "href",
      "/auth/register/contact",
    );
    // Guest continuation must land on a public path (never the authed /home) and,
    // with no returnTo, defaults to the public landing so the guest keeps a public journey.
    expect(screen.getByRole("link", { name: /Continue as guest/i })).toHaveAttribute(
      "href",
      "/",
    );
  });
});
