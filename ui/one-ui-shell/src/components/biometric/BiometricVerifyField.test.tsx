import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BiometricVerifyField } from "./BiometricVerifyField";

const post = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: { post: (...a: unknown[]) => post(...a) },
}));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe("BiometricVerifyField", () => {
  beforeEach(() => post.mockReset());

  it("extracts a probe on capture and hands {modality, probeBase64} to the host", async () => {
    post.mockResolvedValue({ ok: true, templateBase64: "TPL==" });
    const onProbe = vi.fn();
    wrap(<BiometricVerifyField onProbe={onProbe} />);
    const file = new File(["x"], "print.png", { type: "image/png" });
    fireEvent.change(screen.getByTestId("biometric-probe-file"), { target: { files: [file] } });
    await waitFor(() => expect(screen.getByTestId("biometric-captured")).toBeInTheDocument());
    expect(onProbe).toHaveBeenLastCalledWith({ modality: "FINGERPRINT", probeBase64: "TPL==" });
  });

  it("surfaces a soft error (proceed without) when no biometric can be read", async () => {
    post.mockResolvedValue({ ok: false, detail: "no finger" });
    const onProbe = vi.fn();
    wrap(<BiometricVerifyField onProbe={onProbe} />);
    const file = new File(["x"], "blurry.png", { type: "image/png" });
    fireEvent.change(screen.getByTestId("biometric-probe-file"), { target: { files: [file] } });
    await waitFor(() => expect(screen.getByTestId("biometric-error")).toBeInTheDocument());
    // Never hands a fabricated probe up.
    expect(onProbe).not.toHaveBeenCalledWith(expect.objectContaining({ probeBase64: expect.any(String) }));
  });
});
