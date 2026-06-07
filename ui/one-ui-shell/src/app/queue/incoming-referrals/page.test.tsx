import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import IncomingReferralsPage from "./page";

const { acceptMutateAsync } = vi.hoisted(() => ({
  acceptMutateAsync: vi.fn(),
}));
const facility = { id: "facility-1", name: "Harare Central" };

const incomingReferrals = [
  {
    id: "ref-1",
    type: "referral",
    attributes: {
      patient_id: "patient-1",
      referral_type: "SPECIALIST",
      specialty: "Cardiology",
      referred_to: "Cardiology Team",
      referred_to_facility: "Harare Central",
      reason: "Review persistent chest pain",
      urgency: "URGENT",
      status: "PENDING",
      clinical_summary: "Troponin elevated",
      referred_by: "provider-9",
      referred_by_name: "Dr. Ncube",
      response_notes: null,
      responded_at: null,
      accepted_at: null,
      created_at: "2026-04-08T08:00:00.000Z",
    },
  },
];

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility }),
}));

vi.mock("@/hooks/queries/useReferrals", () => ({
  useIncomingReferrals: () => ({ data: { data: incomingReferrals }, isLoading: false }),
  useAcceptReferral: () => ({ mutateAsync: acceptMutateAsync, isPending: false }),
  useRespondReferral: () => ({ mutateAsync: vi.fn(), isPending: false }),
  buildReferralConsultHandoffRoute: (patientId: string, referralId: string) =>
    `/ehr/${patientId}/consults?tab=referrals&referral_id=${referralId}`,
}));

describe("IncomingReferralsPage", () => {
  beforeEach(() => {
    acceptMutateAsync.mockReset();
    acceptMutateAsync.mockResolvedValue({ data: incomingReferrals[0] });
  });

  it("shows the receiving orchestration summary and in-place handoff action", async () => {
    const user = userEvent.setup();

    render(<IncomingReferralsPage />);

    expect(await screen.findByRole("button", { name: "Accept Handoff" })).toBeInTheDocument();
    expect(screen.getAllByText("Needs action now")).not.toHaveLength(0);
    expect(screen.getByText("Ready for receiving handoff")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Accept Handoff" }));

    expect(screen.getByText("Receive Referral Handoff")).toBeInTheDocument();

    await user.type(
      screen.getByPlaceholderText("Triage note, expected specialist, or preparation steps..."),
      "Cardiology registrar notified.",
    );
    const acceptButtons = screen.getAllByRole("button", { name: "Accept Handoff" });
    await user.click(acceptButtons[acceptButtons.length - 1]);

    await waitFor(() =>
      expect(acceptMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          id: "ref-1",
          receiving_facility_id: "facility-1",
          receiving_facility_name: "Harare Central",
          notes: "Cardiology registrar notified.",
        }),
      ),
    );
  }, 30_000);
});
