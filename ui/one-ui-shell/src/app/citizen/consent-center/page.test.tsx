import { render, screen, fireEvent } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ConsentCenterPage from "./page";

// The page composes two data sources through one hook; mock the hook module.
const useConsentCenter = vi.fn();
const mutateAsync = vi.fn();

vi.mock("@/hooks/queries/useConsentCenter", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useConsentCenter")>(
    "@/hooks/queries/useConsentCenter",
  );
  return {
    ...actual, // keep the real itemsOf() normaliser
    useConsentCenter: () => useConsentCenter(),
    useRevokeConsent: () => ({ mutateAsync, isPending: false }),
  };
});

vi.mock("@/components/citizen/StepUpPrompt", () => ({
  StepUpPrompt: () => <div data-testid="stepup" />,
}));

const readStepUp = vi.fn(() => ({ required: false, methods: [] as string[] }));
vi.mock("@/lib/stepUp", () => ({
  readStepUp: (err: unknown) => readStepUp(),
}));

function ok(data: unknown) {
  return { data: { data, meta: { request_id: "r", correlation_id: "c" } }, isLoading: false, isError: false, refetch: vi.fn() };
}

describe("ConsentCenterPage (CJ13)", () => {
  beforeEach(() => {
    useConsentCenter.mockReset();
    mutateAsync.mockReset();
    readStepUp.mockReturnValue({ required: false, methods: [] });
  });

  it("renders granted consents and the access log, with a revoke on an active consent", () => {
    useConsentCenter.mockReturnValue(
      ok({
        consents: { items: [{ id: "c1", status: "ACTIVE", granteeRef: "Dr Banda", purpose: "TREATMENT" }] },
        accessLog: { items: [{ actorType: "PROVIDER", action: "READ", purposeOfUse: "TREATMENT", outcome: "PERMIT", timestamp: "2026-07-19T10:00:00Z" }] },
      }),
    );
    render(<ConsentCenterPage />);

    expect(screen.getByText("Dr Banda")).toBeInTheDocument();
    // Access-log renders the actor KIND (privacy-preserving — no individual named).
    expect(screen.getByText(/A healthcare provider/)).toBeInTheDocument();
    expect(screen.getByText(/viewed your record/)).toBeInTheDocument();

    fireEvent.click(screen.getByText("Revoke"));
    expect(mutateAsync).toHaveBeenCalledWith("c1");
  });

  it("shows the degraded message when the access log is unavailable (no CPID)", () => {
    useConsentCenter.mockReturnValue(ok({ consents: { items: [] }, accessLog: null }));
    render(<ConsentCenterPage />);
    expect(screen.getByText(/access history isn.?t available yet/i)).toBeInTheDocument();
    expect(screen.getByText(/haven.?t granted any consents yet/i)).toBeInTheDocument();
  });

  it("tolerates a bare-array consents shape (not just PagedResponse)", () => {
    useConsentCenter.mockReturnValue(
      ok({ consents: [{ id: "c9", status: "GRANTED", granteeRef: "Clinic X" }], accessLog: { items: [] } }),
    );
    render(<ConsentCenterPage />);
    expect(screen.getByText("Clinic X")).toBeInTheDocument();
    expect(screen.getByText(/No one has accessed your record yet/i)).toBeInTheDocument();
  });

  it("shows the step-up prompt when a revoke requires verification (retry target retained)", async () => {
    useConsentCenter.mockReturnValue(
      ok({ consents: { items: [{ id: "c1", status: "ACTIVE", granteeRef: "Dr Banda" }] }, accessLog: null }),
    );
    mutateAsync.mockRejectedValueOnce(new Error("step up"));
    readStepUp.mockReturnValue({ required: true, methods: ["MFA"] });

    render(<ConsentCenterPage />);
    fireEvent.click(screen.getByText("Revoke"));

    // The prompt appears (pendingRevokeId was NOT cleared by the catch path — the bug this guards).
    expect(await screen.findByTestId("stepup")).toBeInTheDocument();
  });

  it("surfaces a load error with retry", () => {
    useConsentCenter.mockReturnValue({ data: undefined, isLoading: false, isError: true, refetch: vi.fn() });
    render(<ConsentCenterPage />);
    expect(screen.getByText(/couldn.?t load your consents/i)).toBeInTheDocument();
  });
});
