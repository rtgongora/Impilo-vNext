import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import PharmacyCollectWorkspace from "./page";

const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { post: (...a: unknown[]) => post(...a) } }));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe("Pharmacy collect (pickup-claim) page", () => {
  beforeEach(() => post.mockReset());

  it("claims a pickup with a token and threads a captured biometric probe", async () => {
    // First post = biometric extract (from BiometricVerifyField), second = claim.
    post.mockResolvedValueOnce({ ok: true, templateBase64: "CPROBE==" });
    post.mockResolvedValueOnce({ data: { status: "CLAIMED" } });

    wrap(<PharmacyCollectWorkspace />);
    fireEvent.change(screen.getByTestId("pickup-token"), { target: { value: "TKN-1" } });
    fireEvent.change(screen.getByTestId("collector-ref"), { target: { value: "cpid-9" } });
    const file = new File(["x"], "finger.png", { type: "image/png" });
    fireEvent.change(screen.getByTestId("biometric-probe-file"), { target: { files: [file] } });
    await waitFor(() => expect(screen.getByTestId("biometric-captured")).toBeInTheDocument());

    fireEvent.click(screen.getByTestId("claim-btn"));
    await waitFor(() => expect(screen.getByTestId("claim-result")).toBeInTheDocument());

    const claimCall = post.mock.calls.find((c) => c[0] === "/internal/v1/pharmacy/pickup/claim");
    expect(claimCall?.[1]).toMatchObject({
      token: "TKN-1",
      biometricSubjectRef: "cpid-9",
      biometricModality: "FINGERPRINT",
      biometricProbeBase64: "CPROBE==",
    });
  });

  it("surfaces a rejected collection cleanly (nothing handed over)", async () => {
    post.mockRejectedValueOnce({ status: 422 });
    wrap(<PharmacyCollectWorkspace />);
    fireEvent.change(screen.getByTestId("pickup-token"), { target: { value: "BAD" } });
    fireEvent.click(screen.getByTestId("claim-btn"));
    const result = await screen.findByTestId("claim-result");
    expect(result.textContent).toMatch(/rejected/i);
  });
});
