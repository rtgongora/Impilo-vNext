import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ProviderActivatePage from "./page";

const { activateProvider, replace, post, get, authUser } = vi.hoisted(() => ({
  activateProvider: vi.fn(),
  replace: vi.fn(),
  post: vi.fn(),
  get: vi.fn(),
  // Stable reference — the page keys its provider-load effect on `user`.
  authUser: { id: "health-123", displayName: "Dr Test", linkedIds: {} },
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) },
}));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("@/components/auth/ProviderRoleActivationRail", () => ({ ProviderRoleActivationRail: () => null }));
vi.mock("@/components/biometric/BiometricVerifyField", () => ({
  BiometricVerifyField: ({ onProbe }: { onProbe: (p: { modality: string; probeBase64: string } | null) => void }) => (
    <button data-testid="capture-probe" onClick={() => onProbe({ modality: "FINGERPRINT", probeBase64: "QUJD" })}>
      capture
    </button>
  ),
}));
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: authUser, activateProvider }),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  useSearchParams: () => ({ get: () => null }),
}));

describe("Provider activation — biometric step-up (L2)", () => {
  beforeEach(() => {
    activateProvider.mockReset();
    replace.mockReset();
    post.mockReset();
    get.mockReset();
    sessionStorage.clear();
    get.mockResolvedValue({
      data: { providerId: "VAR-1", givenName: "Dr", familyName: "Test", cadre: "DOCTOR", status: "ACTIVE" },
    });
  });

  it("no probe captured → activation behaves exactly as today (activates + redirects, no verify call)", async () => {
    render(<ProviderActivatePage />);
    const activate = await screen.findByTestId("provider-activate-btn");
    fireEvent.click(activate);

    await waitFor(() => expect(activateProvider).toHaveBeenCalledWith("VAR-1"));
    expect(post).not.toHaveBeenCalled();
    expect(replace).toHaveBeenCalledWith("/clinical");
    expect(sessionStorage.getItem("exp:provider_biometric_verified")).toBeNull();
  });

  it("MATCH → activation proceeds and is marked biometric-verified", async () => {
    post.mockResolvedValue({ result: "MATCH", confidence: 0.94 });
    render(<ProviderActivatePage />);
    fireEvent.click(await screen.findByTestId("capture-probe"));
    fireEvent.click(screen.getByTestId("provider-activate-btn"));

    await waitFor(() => expect(activateProvider).toHaveBeenCalledWith("VAR-1"));
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/identity/providers/VAR-1/biometric-verify",
      { modality: "FINGERPRINT", probeBase64: "QUJD" },
    );
    expect(sessionStorage.getItem("exp:provider_biometric_verified")).toBe("true");
    expect(replace).toHaveBeenCalledWith("/clinical");
  });

  it("NO_MATCH → activation is BLOCKED with an honest message", async () => {
    post.mockResolvedValue({ result: "NO_MATCH", confidence: 0.1 });
    render(<ProviderActivatePage />);
    fireEvent.click(await screen.findByTestId("capture-probe"));
    fireEvent.click(screen.getByTestId("provider-activate-btn"));

    const err = await screen.findByTestId("provider-stepup-error");
    expect(err.textContent).toContain("did not match");
    expect(activateProvider).not.toHaveBeenCalled();
    expect(replace).not.toHaveBeenCalled();
  });

  it("UNAVAILABLE (biometric outage) → never blocks; falls back to the existing factor", async () => {
    post.mockRejectedValue({ code: "BIOMETRIC_SERVICE_UNAVAILABLE" });
    render(<ProviderActivatePage />);
    fireEvent.click(await screen.findByTestId("capture-probe"));
    fireEvent.click(screen.getByTestId("provider-activate-btn"));

    await waitFor(() => expect(activateProvider).toHaveBeenCalledWith("VAR-1"));
    expect(replace).toHaveBeenCalledWith("/clinical");
    // Fell back — not marked biometric-verified.
    expect(sessionStorage.getItem("exp:provider_biometric_verified")).toBeNull();
    expect(screen.queryByTestId("provider-stepup-error")).toBeNull();
  });

  it("server-reported UNAVAILABLE result → proceeds on the existing factor", async () => {
    post.mockResolvedValue({ result: "UNAVAILABLE", reason: "BIOMETRIC_VERIFY_UNAVAILABLE" });
    render(<ProviderActivatePage />);
    fireEvent.click(await screen.findByTestId("capture-probe"));
    fireEvent.click(screen.getByTestId("provider-activate-btn"));

    await waitFor(() => expect(activateProvider).toHaveBeenCalledWith("VAR-1"));
    expect(sessionStorage.getItem("exp:provider_biometric_verified")).toBeNull();
  });
});
