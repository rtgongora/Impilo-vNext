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

const { post, setAuth, routerPush, searchParams, deriveFromRoles, hydrate } = vi.hoisted(() => ({
  post: vi.fn(),
  setAuth: vi.fn(),
  routerPush: vi.fn(),
  searchParams: new Map<string, string>(),
  deriveFromRoles: vi.fn(),
  hydrate: vi.fn(),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { post },
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ setAuth }),
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
  useRouter: () => ({ push: routerPush }),
  useSearchParams: () => ({ get: (k: string) => searchParams.get(k) ?? null }),
}));

import BiometricScanLoginPage from "./page";

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <BiometricScanLoginPage />
    </QueryClientProvider>,
  );
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
          healthId: "person-1",
          email: "grace@example.com",
          displayName: "Grace Mavenge",
          roles: ["CITIZEN"],
          actorType: "CITIZEN",
        },
      },
    },
  };
}

/** Upload an image and wait for the FileReader to prime the probe (button enabled). */
async function captureImage() {
  const input = screen.getByTestId("scan-capture-input") as HTMLInputElement;
  const file = new File(["fingerprint-bytes"], "fp.png", { type: "image/png" });
  await userEvent.upload(input, file);
  await waitFor(() =>
    expect((screen.getByTestId("scan-signin") as HTMLButtonElement).disabled).toBe(false),
  );
}

beforeEach(() => {
  post.mockReset();
  setAuth.mockReset();
  routerPush.mockReset();
  deriveFromRoles.mockReset();
  hydrate.mockReset();
  searchParams.clear();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("Biometric scan-to-login page", () => {
  it("mints a session on a single strong match and continues to resolving", async () => {
    post.mockResolvedValueOnce(sessionOk());
    renderPage();

    await captureImage();
    await userEvent.click(screen.getByTestId("scan-signin"));

    await waitFor(() => expect(setAuth).toHaveBeenCalledTimes(1));

    // The capture is posted to the biometric identify-login endpoint with the modality.
    expect(post).toHaveBeenCalledTimes(1);
    const [url, body] = post.mock.calls[0];
    expect(url).toBe("/internal/v1/auth/biometric/identify-login");
    expect(body.modality).toBe("FINGERPRINT");
    expect(typeof body.sampleBase64).toBe("string");
    expect(body.sampleBase64.length).toBeGreaterThan(0);

    const [userArg, tokenArg] = setAuth.mock.calls[0];
    expect(tokenArg).toBe("access-token-1");
    expect(userArg.loginMethod).toBe("biometric");
    expect(routerPush).toHaveBeenCalledWith("/auth/resolving");
  });

  it("shows an honest no-match message and mints no session on a 401 deny", async () => {
    // Plain-object rejection mirrors api-client's throw shape for a non-2xx response.
    post.mockRejectedValueOnce({ status: 401, error: { code: "BIOMETRIC_NO_MATCH" } });
    renderPage();

    await captureImage();
    await userEvent.click(screen.getByTestId("scan-signin"));

    expect(await screen.findByTestId("scan-error")).toBeInTheDocument();
    expect(setAuth).not.toHaveBeenCalled();
    expect(routerPush).not.toHaveBeenCalled();
  });

  it("keeps the honest not-enabled state when the flag is off (501)", async () => {
    post.mockRejectedValueOnce({ status: 501, error: { code: "BIOMETRIC_LOGIN_NOT_ENABLED" } });
    renderPage();

    await captureImage();
    await userEvent.click(screen.getByTestId("scan-signin"));

    expect(await screen.findByTestId("scan-not-enabled")).toBeInTheDocument();
    expect(setAuth).not.toHaveBeenCalled();
  });

  it("surfaces a temporary-unavailable message and mints no session on 503", async () => {
    post.mockRejectedValueOnce({ status: 503, error: { code: "BIOMETRIC_UNAVAILABLE" } });
    renderPage();

    await captureImage();
    await userEvent.click(screen.getByTestId("scan-signin"));

    expect(await screen.findByTestId("scan-error")).toBeInTheDocument();
    expect(setAuth).not.toHaveBeenCalled();
  });
});
