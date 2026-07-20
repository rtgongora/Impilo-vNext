import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/components/AuthLayout", () => ({
  AuthLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

const { post, setAuth, routerReplace, searchParams, deriveFromRoles, hydrate, authState } = vi.hoisted(() => ({
  post: vi.fn(),
  setAuth: vi.fn(),
  routerReplace: vi.fn(),
  searchParams: new Map<string, string>(),
  deriveFromRoles: vi.fn(),
  hydrate: vi.fn(),
  authState: { isAuthenticated: false },
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { post },
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ isAuthenticated: authState.isAuthenticated, setAuth }),
}));

vi.mock("@/hooks/useWorkModeStore", () => ({
  useWorkModeStore: { getState: () => ({ deriveFromRoles }) },
}));

vi.mock("@/hooks/useConsentStore", () => ({
  useConsentStore: { getState: () => ({ hydrate }) },
}));

vi.mock("@/lib/resolve-post-login-destination", () => ({
  buildPostLoginResolvingPath: () => "/auth/resolving",
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: routerReplace, push: routerReplace }),
  useSearchParams: () => ({ get: (k: string) => searchParams.get(k) ?? null }),
}));

import BiometricLoginPage from "../../login/biometric/page";
import PasskeyCallbackPage from "./callback/page";

function renderPage(node: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

const AUTHORIZE_URL =
  "http://kc.test/realms/impilo/protocol/openid-connect/auth?client_id=impilo-passkey&response_type=code&code_challenge=abc&code_challenge_method=S256&state=st-1";

function initiateOk(register = false) {
  return {
    data: {
      type: "passkey-initiate",
      attributes: {
        authorizeUrl: AUTHORIZE_URL,
        state: "st-1",
        nonce: "nonce-1",
        codeVerifier: "verifier-1",
        register,
      },
    },
  };
}

function sessionOk() {
  return {
    data: {
      id: "person-1",
      type: "auth_token",
      attributes: {
        token: "access-token-1",
        expiresAt: "2026-07-20T12:00:00Z",
        user: {
          id: "person-1",
          email: "grace@example.com",
          displayName: "Grace Mavenge",
          roles: ["CITIZEN"],
          actorType: "CITIZEN",
        },
      },
    },
  };
}

let assignMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  post.mockReset();
  setAuth.mockReset();
  routerReplace.mockReset();
  deriveFromRoles.mockReset();
  hydrate.mockReset();
  searchParams.clear();
  authState.isAuthenticated = false;
  sessionStorage.clear();

  assignMock = vi.fn();
  Object.defineProperty(window, "location", {
    value: { assign: assignMock, href: "http://app.test/auth/login/biometric" },
    writable: true,
    configurable: true,
  });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("Passkey biometric (initiate) page", () => {
  it("stashes PKCE handoff and redirects the browser to the Keycloak authorize URL", async () => {
    post.mockResolvedValueOnce(initiateOk());
    renderPage(<BiometricLoginPage />);

    await userEvent.click(await screen.findByTestId("passkey-signin"));

    await waitFor(() => expect(assignMock).toHaveBeenCalledWith(AUTHORIZE_URL));
    expect(post).toHaveBeenCalledWith("/internal/v1/auth/passkey/initiate", { register: false });
    expect(sessionStorage.getItem("exp:passkey_state")).toBe("st-1");
    expect(sessionStorage.getItem("exp:passkey_code_verifier")).toBe("verifier-1");
  });

  it("keeps the honest not-enabled state when the flag is off (501)", async () => {
    // Plain-object rejection mirrors api-client's throw shape for a non-2xx response.
    post.mockRejectedValueOnce({ status: 501, error: { code: "PASSKEY_NOT_ENABLED" } });
    renderPage(<BiometricLoginPage />);

    await userEvent.click(await screen.findByTestId("passkey-signin"));

    expect(await screen.findByTestId("passkey-not-enabled")).toBeInTheDocument();
    expect(assignMock).not.toHaveBeenCalled();
  });

  it("surfaces a generic error on an unexpected initiate failure", async () => {
    post.mockRejectedValueOnce({ status: 500, error: { code: "INTERNAL" } });
    renderPage(<BiometricLoginPage />);

    await userEvent.click(await screen.findByTestId("passkey-signin"));

    expect(await screen.findByTestId("passkey-error")).toBeInTheDocument();
    expect(assignMock).not.toHaveBeenCalled();
  });

  it("offers device registration only to an authenticated user", async () => {
    authState.isAuthenticated = true;
    post.mockResolvedValueOnce(initiateOk(true));
    renderPage(<BiometricLoginPage />);

    await userEvent.click(await screen.findByTestId("passkey-register"));

    await waitFor(() => expect(assignMock).toHaveBeenCalledWith(AUTHORIZE_URL));
    expect(post).toHaveBeenCalledWith("/internal/v1/auth/passkey/initiate", { register: true });
  });

  it("hides the register affordance for an anonymous visitor", async () => {
    renderPage(<BiometricLoginPage />);
    await screen.findByTestId("passkey-signin");
    expect(screen.queryByTestId("passkey-register")).not.toBeInTheDocument();
  });
});

describe("Passkey callback page", () => {
  it("exchanges the code and establishes the session on success", async () => {
    sessionStorage.setItem("exp:passkey_state", "st-1");
    sessionStorage.setItem("exp:passkey_code_verifier", "verifier-1");
    searchParams.set("code", "kc-code-1");
    searchParams.set("state", "st-1");
    post.mockResolvedValueOnce(sessionOk());

    renderPage(<PasskeyCallbackPage />);

    await waitFor(() => expect(setAuth).toHaveBeenCalledTimes(1));
    expect(post).toHaveBeenCalledWith("/internal/v1/auth/passkey/callback", {
      code: "kc-code-1",
      codeVerifier: "verifier-1",
    });
    const [userArg, tokenArg] = setAuth.mock.calls[0];
    expect(tokenArg).toBe("access-token-1");
    expect(userArg.email).toBe("grace@example.com");
    expect(userArg.loginMethod).toBe("biometric");
    expect(routerReplace).toHaveBeenCalledWith("/auth/resolving");
    // Single-use PKCE material is cleared after exchange.
    expect(sessionStorage.getItem("exp:passkey_code_verifier")).toBeNull();
  });

  it("never exchanges a code when no initiate handoff exists (CSRF/expiry guard)", async () => {
    searchParams.set("code", "kc-code-1");
    searchParams.set("state", "st-1");
    // No sessionStorage handoff.
    renderPage(<PasskeyCallbackPage />);

    expect(await screen.findByTestId("passkey-callback-error")).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
    expect(setAuth).not.toHaveBeenCalled();
  });

  it("refuses to exchange when the returned state does not match (CSRF)", async () => {
    sessionStorage.setItem("exp:passkey_state", "st-1");
    sessionStorage.setItem("exp:passkey_code_verifier", "verifier-1");
    searchParams.set("code", "kc-code-1");
    searchParams.set("state", "st-TAMPERED");

    renderPage(<PasskeyCallbackPage />);

    expect(await screen.findByTestId("passkey-callback-error")).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
    expect(setAuth).not.toHaveBeenCalled();
  });

  it("shows an honest error and mints no session when the exchange is rejected", async () => {
    sessionStorage.setItem("exp:passkey_state", "st-1");
    sessionStorage.setItem("exp:passkey_code_verifier", "verifier-1");
    searchParams.set("code", "bad-code");
    searchParams.set("state", "st-1");
    post.mockRejectedValueOnce({ status: 401, error: { code: "INVALID_CREDENTIALS" } });

    renderPage(<PasskeyCallbackPage />);

    expect(await screen.findByTestId("passkey-callback-error")).toBeInTheDocument();
    expect(setAuth).not.toHaveBeenCalled();
    expect(routerReplace).not.toHaveBeenCalled();
  });

  it("errors without exchanging when no code is present", async () => {
    sessionStorage.setItem("exp:passkey_state", "st-1");
    sessionStorage.setItem("exp:passkey_code_verifier", "verifier-1");
    // no code param
    renderPage(<PasskeyCallbackPage />);

    expect(await screen.findByTestId("passkey-callback-error")).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });
});
