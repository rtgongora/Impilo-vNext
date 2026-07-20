import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { SignOffPanel } from "./SignOffPanel";

const proofMutate = vi.fn().mockResolvedValue({});
vi.mock("@/hooks/useNhume", () => ({
  useRecordProof: () => ({ mutateAsync: proofMutate, isPending: false }),
}));

// BiometricVerifyField (rendered on the drop-off stage) extracts a probe via apiClient.post.
const post = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: { post: (...a: unknown[]) => post(...a) },
}));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  proofMutate.mockClear();
  post.mockReset();
});

describe("SignOffPanel — collection & drop-off sign-off", () => {
  it("collection (PICKUP) records proof without marking delivered", async () => {
    wrap(<SignOffPanel deliveryId="d-1" stage="PICKUP" />);
    fireEvent.change(screen.getByPlaceholderText(/name \/ role/i), { target: { value: "Sr Dube (origin)" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm collection/i }));

    await waitFor(() => expect(proofMutate).toHaveBeenCalledTimes(1));
    const p = proofMutate.mock.calls[0][0];
    expect(p.proof_stage).toBe("PICKUP");
    expect(p.mark_delivered).toBe(false);
    expect(p.captured_by).toBe("Sr Dube (origin)");
    expect(p.method).toBe("RECIPIENT_SIGNATURE");
    // Collection never renders recipient biometric-verify.
    expect(screen.queryByTestId("biometric-verify-field")).not.toBeInTheDocument();
  });

  it("drop-off (DELIVERY) records proof and marks delivered", async () => {
    wrap(<SignOffPanel deliveryId="d-1" stage="DELIVERY" />);
    fireEvent.change(screen.getByPlaceholderText(/name \/ role/i), { target: { value: "Dr Moyo (receiving)" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm drop-off & mark delivered/i }));

    await waitFor(() => expect(proofMutate).toHaveBeenCalledTimes(1));
    const p = proofMutate.mock.calls[0][0];
    expect(p.proof_stage).toBe("DELIVERY");
    expect(p.mark_delivered).toBe(true);
    expect(p.captured_by).toBe("Dr Moyo (receiving)");
    // No probe captured → no biometric fields threaded.
    expect(p.biometric_probe_base64).toBeUndefined();
  });

  it("drop-off threads recipient biometric fields when a probe is captured", async () => {
    post.mockResolvedValue({ ok: true, templateBase64: "TPL==" });
    wrap(<SignOffPanel deliveryId="d-1" stage="DELIVERY" recipientRef="HID-777" />);

    fireEvent.change(screen.getByPlaceholderText(/name \/ role/i), { target: { value: "Dr Moyo (receiving)" } });
    // Capture a fingerprint probe through the shared field.
    const file = new File(["x"], "print.png", { type: "image/png" });
    fireEvent.change(screen.getByTestId("biometric-probe-file"), { target: { files: [file] } });
    await waitFor(() => expect(screen.getByTestId("biometric-captured")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /confirm drop-off & mark delivered/i }));
    await waitFor(() => expect(proofMutate).toHaveBeenCalledTimes(1));
    const p = proofMutate.mock.calls[0][0];
    expect(p.biometric_subject_ref).toBe("HID-777");
    expect(p.biometric_modality).toBe("FINGERPRINT");
    expect(p.biometric_probe_base64).toBe("TPL==");
  });

  it("blocks sign-off without recording who — no API call", async () => {
    wrap(<SignOffPanel deliveryId="d-1" stage="PICKUP" />);
    fireEvent.click(screen.getByRole("button", { name: /confirm collection/i }));
    expect(await screen.findByText(/who released the cargo/i)).toBeInTheDocument();
    expect(proofMutate).not.toHaveBeenCalled();
  });
});
