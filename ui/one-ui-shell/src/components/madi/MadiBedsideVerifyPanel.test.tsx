import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MadiBedsideVerifyPanel } from "./MadiBedsideVerifyPanel";

const preVerifyMutate = vi.fn();
const post = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiClient: { post: (...a: unknown[]) => post(...a) },
}));

vi.mock("@/hooks/queries/useVitoBiometric", () => ({
  useBiometricProfile: () => ({ data: undefined }),
  useBiometricVerify: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/queries/useMadi", () => ({
  usePreVerifyTransfusion: () => ({ mutate: preVerifyMutate, isPending: false, isSuccess: false }),
}));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe("MadiBedsideVerifyPanel biometric pre-verify", () => {
  beforeEach(() => {
    preVerifyMutate.mockReset();
    post.mockReset();
  });

  it("threads a captured live probe into the pre-verify request", async () => {
    // BiometricVerifyField extracts a template via apiClient.post(.../extract)
    post.mockResolvedValue({ ok: true, templateBase64: "PROBE==" });

    wrap(
      <MadiBedsideVerifyPanel episodeId="ep-1" defaultPatientCpid="CPID-123" verifiedBy="nurse-1" />,
    );

    // Blood unit is required alongside the patient.
    fireEvent.change(screen.getByPlaceholderText("Blood unit UUID"), {
      target: { value: "unit-uuid-9" },
    });

    // Capture a biometric probe (patient method defaults to BIOMETRIC).
    const file = new File(["x"], "finger.png", { type: "image/png" });
    fireEvent.change(screen.getByTestId("biometric-probe-file"), { target: { files: [file] } });
    await waitFor(() => expect(screen.getByTestId("biometric-captured")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /record bedside verification/i }));

    await waitFor(() => expect(preVerifyMutate).toHaveBeenCalled());
    const body = preVerifyMutate.mock.calls[0][0];
    expect(body).toMatchObject({
      patient_cpid: "CPID-123",
      blood_unit_id: "unit-uuid-9",
      biometric_subject_ref: "CPID-123",
      biometric_modality: "FINGERPRINT",
      biometric_probe_base64: "PROBE==",
    });
  });

  it("surfaces a 4xx NO_MATCH as a blocking, honest error", async () => {
    post.mockResolvedValue({ ok: true, templateBase64: "PROBE==" });
    preVerifyMutate.mockImplementation((_body, opts) => {
      opts.onError({ status: 422, error: { message: "Biometric did not match the enrolled patient." } });
    });

    wrap(<MadiBedsideVerifyPanel episodeId="ep-1" defaultPatientCpid="CPID-123" />);
    fireEvent.change(screen.getByPlaceholderText("Blood unit UUID"), {
      target: { value: "unit-uuid-9" },
    });
    const file = new File(["x"], "finger.png", { type: "image/png" });
    fireEvent.change(screen.getByTestId("biometric-probe-file"), { target: { files: [file] } });
    await waitFor(() => expect(screen.getByTestId("biometric-captured")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /record bedside verification/i }));

    await waitFor(() =>
      expect(screen.getByText(/did not match the enrolled patient/i)).toBeInTheDocument(),
    );
  });
});
