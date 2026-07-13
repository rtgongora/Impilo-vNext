import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import IncomingReferralsPage from "./page";

const { acceptMutateAsync, teleconsultAcceptMutateAsync, fixture } = vi.hoisted(() => ({
  acceptMutateAsync: vi.fn(),
  teleconsultAcceptMutateAsync: vi.fn(),
  fixture: { referrals: [] as unknown[] },
}));
const facility = { id: "facility-1", name: "Harare Central" };

const specialistReferral = {
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
};

const teleconsultReferral = {
  id: "tc-1",
  type: "referral",
  attributes: {
    ...specialistReferral.attributes,
    patient_id: "patient-2",
    specialty: "Dermatology",
    modality: "virtual",
  },
};

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
  useIncomingReferrals: () => ({ data: { data: fixture.referrals }, isLoading: false }),
  useAcceptReferral: () => ({ mutateAsync: acceptMutateAsync, isPending: false }),
  useRespondReferral: () => ({ mutateAsync: vi.fn(), isPending: false }),
  buildReferralConsultHandoffRoute: (patientId: string, referralId: string) =>
    `/ehr/${patientId}/consults?tab=referrals&referral_id=${referralId}`,
}));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useAcceptTeleconsultSession: () => ({ mutateAsync: teleconsultAcceptMutateAsync, isPending: false }),
}));

describe("IncomingReferralsPage", () => {
  beforeEach(() => {
    fixture.referrals = [specialistReferral];
    acceptMutateAsync.mockReset();
    acceptMutateAsync.mockResolvedValue({ data: specialistReferral });
    teleconsultAcceptMutateAsync.mockReset();
    teleconsultAcceptMutateAsync.mockResolvedValue({ data: {} });
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
    expect(teleconsultAcceptMutateAsync).not.toHaveBeenCalled();
  }, 30_000);

  it("routes a teleconsult referral through the GOVERNED accept, not the plain one", async () => {
    fixture.referrals = [teleconsultReferral];
    const user = userEvent.setup();

    render(<IncomingReferralsPage />);

    expect(await screen.findByText("Teleconsult")).toBeInTheDocument();
    await user.click(await screen.findByRole("button", { name: "Accept Handoff" }));
    const acceptButtons = screen.getAllByRole("button", { name: "Accept Handoff" });
    await user.click(acceptButtons[acceptButtons.length - 1]);

    await waitFor(() =>
      expect(teleconsultAcceptMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({ id: "tc-1", receivingFacilityId: "facility-1" }),
      ),
    );
    expect(acceptMutateAsync).not.toHaveBeenCalled();
  }, 30_000);
});
