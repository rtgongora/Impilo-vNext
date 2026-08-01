import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { beginOidcLogin, beginKeycloakAction, authState } = vi.hoisted(() => ({
  beginOidcLogin: vi.fn(),
  beginKeycloakAction: vi.fn(),
  authState: { isAuthenticated: false },
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => <a href={href} {...props}>{children}</a>,
}));
vi.mock("@/components/AuthLayout", () => ({ AuthLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/lib/auth/web-session", () => ({ beginOidcLogin, beginKeycloakAction }));
vi.mock("@/hooks/useAuthStore", () => ({ useAuthStore: () => ({ isAuthenticated: authState.isAuthenticated }) }));

import BiometricLoginPage from "../../login/biometric/page";
import PasskeyCallbackPage from "./callback/page";

beforeEach(() => {
  vi.clearAllMocks();
  authState.isAuthenticated = false;
});

describe("native Keycloak passkey journeys", () => {
  it("starts passkey sign-in through the BFF OIDC flow", async () => {
    render(<BiometricLoginPage />);
    await userEvent.click(screen.getByTestId("passkey-signin"));
    expect(beginOidcLogin).toHaveBeenCalledWith({
      requiredAcr: "urn:impilo:aal2",
      returnTo: "/home",
    });
    expect(beginKeycloakAction).not.toHaveBeenCalled();
  });

  it("uses an authenticated native required action for passkey enrollment", async () => {
    authState.isAuthenticated = true;
    beginKeycloakAction.mockResolvedValueOnce(undefined);
    render(<BiometricLoginPage />);
    await userEvent.click(screen.getByTestId("passkey-register"));
    await waitFor(() => expect(beginKeycloakAction).toHaveBeenCalledWith(
      "webauthn-register-passwordless", "/settings/security",
    ));
  });

  it("retires the duplicate callback without exchanging its browser code", () => {
    render(<PasskeyCallbackPage />);
    expect(beginOidcLogin).toHaveBeenCalledWith({
      requiredAcr: "urn:impilo:aal2",
      returnTo: "/home",
    });
    expect(beginKeycloakAction).not.toHaveBeenCalled();
  });
});
