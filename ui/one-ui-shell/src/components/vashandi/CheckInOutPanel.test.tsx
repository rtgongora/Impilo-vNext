import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CheckInOutPanel } from "./CheckInOutPanel";

const checkInMutateAsync = vi.fn();
const adhocMutateAsync = vi.fn();
const post = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiClient: { post: (...a: unknown[]) => post(...a) },
}));

vi.mock("@/hooks/useVashandi", () => ({
  useCheckIn: () => ({ mutateAsync: checkInMutateAsync, isPending: false, data: undefined }),
  useAdhocCheckIn: () => ({ mutateAsync: adhocMutateAsync, isPending: false, data: undefined }),
  useCheckOut: () => ({ mutateAsync: vi.fn(), isPending: false, data: undefined }),
}));

vi.mock("@/hooks/useShiftStore", () => ({
  useShiftStore: (sel: (s: unknown) => unknown) => sel({ startShift: vi.fn(), endShift: vi.fn() }),
}));
vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (sel: (s: unknown) => unknown) => sel({ facility: { id: "fac-1", name: "Facility 1" } }),
}));
vi.mock("@/hooks/useWorkspaceStore", () => ({
  useWorkspaceStore: (sel: (s: unknown) => unknown) => sel({ workspace: { id: "ws-1" } }),
}));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

const ROSTERED_SHIFT = "11111111-1111-1111-1111-111111111111";

describe("CheckInOutPanel biometric check-in", () => {
  beforeEach(() => {
    checkInMutateAsync.mockReset();
    adhocMutateAsync.mockReset();
    post.mockReset();
    checkInMutateAsync.mockResolvedValue({ success: true, data: { eventId: "e-1" } });
  });

  it("threads a captured probe into the rostered check-in request", async () => {
    post.mockResolvedValue({ ok: true, templateBase64: "WPROBE==" });

    wrap(<CheckInOutPanel shiftId={ROSTERED_SHIFT} workforceProfileId="wp-42" />);

    const file = new File(["x"], "finger.png", { type: "image/png" });
    fireEvent.change(screen.getByTestId("biometric-probe-file"), { target: { files: [file] } });
    await waitFor(() => expect(screen.getByTestId("biometric-captured")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /check in/i }));

    await waitFor(() => expect(checkInMutateAsync).toHaveBeenCalled());
    expect(checkInMutateAsync.mock.calls[0][0]).toMatchObject({
      shiftId: ROSTERED_SHIFT,
      workforceProfileId: "wp-42",
      biometricSubjectRef: "wp-42",
      biometricModality: "FINGERPRINT",
      biometricProbeBase64: "WPROBE==",
    });
  });

  it("does not render the probe capture on the self-service (adhoc) path", () => {
    wrap(<CheckInOutPanel shiftId="self-service" workforceProfileId="wp-42" />);
    expect(screen.queryByTestId("biometric-verify-field")).not.toBeInTheDocument();
  });
});
