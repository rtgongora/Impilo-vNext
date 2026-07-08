import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { FacilityClaimJourney } from "./FacilityClaimJourney";

const mockUseEligibility = vi.fn();
const mockUsePreview = vi.fn();
const mockUseAppoint = vi.fn();
const mockUseAcceptOrgInvitation = vi.fn();
const mockUseUploadEvidence = vi.fn();

vi.mock("@/hooks/queries/useFacilityClaim", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useFacilityClaim")>(
    "@/hooks/queries/useFacilityClaim",
  );
  return {
    // Keep the pure helpers real; mock only the network hooks.
    facilityClaimErrorMessage: actual.facilityClaimErrorMessage,
    isFeaturePending: actual.isFeaturePending,
    useFacilityClaimEligibility: (...args: unknown[]) => mockUseEligibility(...args),
    usePreviewFacilityClaim: (...args: unknown[]) => mockUsePreview(...args),
    useAppointFacilityClaim: (...args: unknown[]) => mockUseAppoint(...args),
    useAcceptOrgInvitation: (...args: unknown[]) => mockUseAcceptOrgInvitation(...args),
    useUploadFacilityEvidence: (...args: unknown[]) => mockUseUploadEvidence(...args),
  };
});

function idleMutation(overrides: Record<string, unknown> = {}) {
  return {
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
    data: undefined,
    ...overrides,
  };
}

const FAC = "11111111-2222-3333-4444-555555555555";

describe("FacilityClaimJourney", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseEligibility.mockReturnValue({
      data: { facilityUuid: FAC, allowedOnPlatform: true, claimable: true, alreadyAdministered: false, activeAdministratorCount: 0 },
      isLoading: false,
      isError: false,
    });
    mockUsePreview.mockReturnValue(idleMutation());
    mockUseAppoint.mockReturnValue(idleMutation());
    mockUseAcceptOrgInvitation.mockReturnValue(idleMutation());
    mockUseUploadEvidence.mockReturnValue(idleMutation());
  });

  it("requires a facility uuid (no facility selected)", () => {
    render(<FacilityClaimJourney facilityUuid={undefined} />);
    expect(screen.getByText(/No facility selected/)).toBeInTheDocument();
  });

  it("checks eligibility first (loading state)", () => {
    mockUseEligibility.mockReturnValue({ data: undefined, isLoading: true, isError: false });
    render(<FacilityClaimJourney facilityUuid={FAC} />);
    expect(screen.getByText(/Checking whether this facility can be administered/)).toBeInTheDocument();
  });

  it("offers the path chooser when the facility is claimable", () => {
    render(<FacilityClaimJourney facilityUuid={FAC} />);
    expect(screen.getByTestId("facility-claim-path-chooser")).toBeInTheDocument();
    expect(screen.getByText(/I have an appointment letter/)).toBeInTheDocument();
    expect(screen.getByText(/Upload a supporting document/)).toBeInTheDocument();
    expect(screen.getByText(/Organisation invitation/)).toBeInTheDocument();
  });

  it("blocks the chooser and explains when the facility is not claimable", () => {
    mockUseEligibility.mockReturnValue({
      data: { facilityUuid: FAC, allowedOnPlatform: false, claimable: false, alreadyAdministered: false, activeAdministratorCount: 0 },
      isLoading: false,
      isError: false,
    });
    render(<FacilityClaimJourney facilityUuid={FAC} />);
    expect(screen.getByTestId("facility-claim-not-claimable")).toBeInTheDocument();
    expect(screen.getByText(/not currently allowed on the platform/)).toBeInTheDocument();
    expect(screen.queryByTestId("facility-claim-path-chooser")).not.toBeInTheDocument();
  });

  it("document lane: offers a real upload form (no coming-soon)", () => {
    render(<FacilityClaimJourney facilityUuid={FAC} />);
    fireEvent.click(screen.getByText(/Upload a supporting document/));
    expect(screen.getByTestId("facility-claim-document-flow")).toBeInTheDocument();
    expect(screen.queryByTestId("facility-claim-coming-soon")).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Supporting document/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Upload document/ })).toBeInTheDocument();
  });

  it("document lane: after upload, shows the uploaded document and offers preview", () => {
    mockUseUploadEvidence.mockReturnValue(
      idleMutation({
        data: { evidenceRef: "obj-123", filename: "letter.pdf", scanStatus: "CLEAN" },
      }),
    );
    render(<FacilityClaimJourney facilityUuid={FAC} />);
    fireEvent.click(screen.getByText(/Upload a supporting document/));
    expect(screen.getByTestId("facility-claim-document-uploaded")).toBeInTheDocument();
    expect(screen.getByText(/letter\.pdf/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Preview this facility/ })).toBeInTheDocument();
  });

  it("org-invitation lane: accepts an invitation token behind consent", () => {
    const acceptMutate = vi.fn();
    mockUseAcceptOrgInvitation.mockReturnValue(idleMutation({ mutate: acceptMutate }));

    render(<FacilityClaimJourney facilityUuid={FAC} />);
    fireEvent.click(screen.getByText(/Organisation invitation/));

    // Real form (no coming-soon), token accept gated behind consent.
    expect(screen.getByTestId("facility-claim-org-invitation-flow")).toBeInTheDocument();
    expect(screen.queryByTestId("facility-claim-coming-soon")).not.toBeInTheDocument();

    const button = screen.getByRole("button", { name: /Accept invitation/ });
    expect(button).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Invitation token/), { target: { value: "TOK-123" } });
    fireEvent.click(screen.getByRole("checkbox"));
    expect(button).not.toBeDisabled();
    fireEvent.click(button);
    expect(acceptMutate).toHaveBeenCalledWith("TOK-123");
  });

  it("org-invitation lane: renders active-affiliation confirmation after accepting", () => {
    mockUseAcceptOrgInvitation.mockReturnValue(
      idleMutation({
        data: { accepted: true, role: "FACILITY_ADMIN", status: "ACCEPTED", note: "Affiliation active." },
      }),
    );
    render(<FacilityClaimJourney facilityUuid={FAC} />);
    fireEvent.click(screen.getByText(/Organisation invitation/));
    expect(screen.getByTestId("facility-claim-org-invitation-accepted")).toBeInTheDocument();
    expect(screen.getByText(/administering affiliation active/i)).toBeInTheDocument();
  });

  it("appointment-letter lane: previews masked facility summary, gates appoint behind consent", () => {
    const previewMutate = vi.fn();
    mockUsePreview.mockReturnValue(
      idleMutation({
        mutate: previewMutate,
        data: {
          facilityUuid: FAC,
          facilityCode: "ZW-H***",
          name: "Harare Central Hospital",
          facilityType: "Central Hospital",
          district: "Harare",
          province: "Harare Metropolitan",
          allowedOnPlatform: true,
          claimable: true,
        },
      }),
    );
    const appointMutate = vi.fn();
    mockUseAppoint.mockReturnValue(idleMutation({ mutate: appointMutate }));

    render(<FacilityClaimJourney facilityUuid={FAC} />);
    fireEvent.click(screen.getByText(/I have an appointment letter/));

    // Masked facility code rendered; nothing is leaked in the clear.
    expect(screen.getByTestId("facility-claim-preview")).toBeInTheDocument();
    expect(screen.getByText("ZW-H***")).toBeInTheDocument();

    // Without consent the appoint button stays disabled.
    const button = screen.getByRole("button", { name: /Request administrator access/ });
    expect(button).toBeDisabled();

    fireEvent.click(screen.getByRole("checkbox"));
    expect(button).not.toBeDisabled();
    fireEvent.click(button);
    expect(appointMutate).toHaveBeenCalledWith({
      facilityUuid: FAC,
      consent: true,
      role: undefined,
      evidenceRef: undefined,
    });
  });

  it("appointment-letter lane: renders pending-approval confirmation after appointing", () => {
    mockUseAppoint.mockReturnValue(
      idleMutation({
        data: { submitted: true, appointmentId: "app-1", approvalState: "PENDING", note: "Pending approval." },
      }),
    );
    render(<FacilityClaimJourney facilityUuid={FAC} />);
    fireEvent.click(screen.getByText(/I have an appointment letter/));
    expect(screen.getByTestId("facility-claim-confirmation")).toBeInTheDocument();
    expect(screen.getByText(/request recorded — pending approval/i)).toBeInTheDocument();
  });

  it("appointment-letter lane: propagates a downstream 409 verdict verbatim", () => {
    mockUseAppoint.mockReturnValue(
      idleMutation({
        isError: true,
        error: { status: 409, error: { code: "UPSTREAM_409", message: "You already administer this facility." } },
      }),
    );
    render(<FacilityClaimJourney facilityUuid={FAC} />);
    fireEvent.click(screen.getByText(/I have an appointment letter/));
    expect(screen.getByRole("alert")).toHaveTextContent(/already administer this facility/);
  });
});
