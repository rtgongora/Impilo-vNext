import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProviderClaimJourney } from "./ProviderClaimJourney";

const mockUseEligibility = vi.fn();
const mockUsePreview = vi.fn();
const mockUseClaim = vi.fn();
const mockUseEvidence = vi.fn();
const mockUseRecover = vi.fn();
const mockUseEmploymentMatch = vi.fn();
const mockUseUploadDocument = vi.fn();

vi.mock("@/hooks/queries/useProviderClaim", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useProviderClaim")>(
    "@/hooks/queries/useProviderClaim",
  );
  return {
    // Keep the pure helpers real (providerClaimErrorMessage / isFeaturePending);
    // mock only the network hooks.
    providerClaimErrorMessage: actual.providerClaimErrorMessage,
    isFeaturePending: actual.isFeaturePending,
    useProviderClaimEligibility: (...args: unknown[]) => mockUseEligibility(...args),
    usePreviewClaimToken: (...args: unknown[]) => mockUsePreview(...args),
    useClaimProviderProfile: (...args: unknown[]) => mockUseClaim(...args),
    useSubmitClaimEvidence: (...args: unknown[]) => mockUseEvidence(...args),
    useRecoverProviderProfile: (...args: unknown[]) => mockUseRecover(...args),
    useUploadProviderDocument: (...args: unknown[]) => mockUseUploadDocument(...args),
  };
});

// EC-lane re-point (E2-TRUST): the EC path now calls the real employment-match
// endpoint. Keep the pure error-message helper real; mock only the mutation hook.
vi.mock("@/hooks/queries/useTrustEmploymentMatch", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useTrustEmploymentMatch")>(
    "@/hooks/queries/useTrustEmploymentMatch",
  );
  return {
    employmentMatchErrorMessage: actual.employmentMatchErrorMessage,
    useEmploymentMatch: (...args: unknown[]) => mockUseEmploymentMatch(...args),
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

describe("ProviderClaimJourney", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseEligibility.mockReturnValue({
      data: { alreadyLinked: false, eligibleForClaim: true },
      isLoading: false,
      isError: false,
    });
    mockUsePreview.mockReturnValue(idleMutation());
    mockUseClaim.mockReturnValue(idleMutation());
    mockUseEvidence.mockReturnValue(idleMutation());
    mockUseRecover.mockReturnValue(idleMutation());
    mockUseEmploymentMatch.mockReturnValue(idleMutation());
    mockUseUploadDocument.mockReturnValue(idleMutation());
  });

  it("document lane: offers a real upload form and submits", () => {
    const uploadMutate = vi.fn();
    mockUseUploadDocument.mockReturnValue(idleMutation({ mutate: uploadMutate }));
    render(<ProviderClaimJourney />);
    fireEvent.click(screen.getByText(/Upload a supporting document/));
    expect(screen.getByTestId("provider-claim-document-flow")).toBeInTheDocument();
    expect(screen.queryByTestId("provider-claim-coming-soon")).not.toBeInTheDocument();

    const input = screen.getByLabelText(/Supporting document/) as HTMLInputElement;
    const file = new File(["x"], "cert.pdf", { type: "application/pdf" });
    fireEvent.change(input, { target: { files: [file] } });
    fireEvent.click(screen.getByRole("button", { name: /Upload document/ }));
    expect(uploadMutate).toHaveBeenCalledWith(file);
  });

  it("document lane: shows the durable review-request confirmation after upload", () => {
    mockUseUploadDocument.mockReturnValue(
      idleMutation({ data: { publicId: "REQ-9", status: "PENDING", nextStep: "Under review." } }),
    );
    render(<ProviderClaimJourney />);
    fireEvent.click(screen.getByText(/Upload a supporting document/));
    expect(screen.getByTestId("provider-claim-document-recorded")).toBeInTheDocument();
    expect(screen.getByText(/REQ-9/)).toBeInTheDocument();
  });

  it("checks eligibility first (loading state)", () => {
    mockUseEligibility.mockReturnValue({ data: undefined, isLoading: true, isError: false });
    render(<ProviderClaimJourney />);
    expect(screen.getByText(/Checking whether a provider profile/)).toBeInTheDocument();
  });

  it("offers all four paths when eligible for claim", () => {
    render(<ProviderClaimJourney />);
    expect(screen.getByTestId("provider-claim-path-chooser")).toBeInTheDocument();
    expect(screen.getByText(/I have a claim token/)).toBeInTheDocument();
    expect(screen.getByText(/I have a council number/)).toBeInTheDocument();
    expect(screen.getByText(/EC \(employment\) number/)).toBeInTheDocument();
    expect(screen.getByText(/Recover an existing profile/)).toBeInTheDocument();
  });

  it("shows the already-linked panel with the masked provider id", () => {
    mockUseEligibility.mockReturnValue({
      data: { alreadyLinked: true, eligibleForClaim: false, providerPublicId: "ABCD***90" },
      isLoading: false,
      isError: false,
    });
    render(<ProviderClaimJourney />);
    expect(screen.getByTestId("provider-claim-already-linked")).toBeInTheDocument();
    expect(screen.getByText("ABCD***90")).toBeInTheDocument();
    expect(screen.queryByTestId("provider-claim-path-chooser")).not.toBeInTheDocument();
  });

  it("claim-token path: previews masked summary, gates claim behind consent", () => {
    const previewMutate = vi.fn();
    mockUsePreview.mockReturnValue(
      idleMutation({
        mutate: previewMutate,
        data: {
          providerPublicId: "ABCD***90",
          givenNameInitial: "T.",
          familyName: "Moyo",
          profession: "DOCTOR",
          lifecycleStatus: "PRELOADED",
        },
      }),
    );
    const claimMutate = vi.fn();
    mockUseClaim.mockReturnValue(idleMutation({ mutate: claimMutate }));

    render(<ProviderClaimJourney />);
    fireEvent.click(screen.getByText(/I have a claim token/));

    // Masked preview rendered; the raw provider id never appears.
    expect(screen.getByTestId("provider-claim-preview")).toBeInTheDocument();
    expect(screen.getByText("ABCD***90")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Claim token/), { target: { value: "tok-1" } });

    // Without consent the claim button stays disabled.
    const button = screen.getByRole("button", { name: /Claim this profile/ });
    expect(button).toBeDisabled();

    fireEvent.click(screen.getByRole("checkbox"));
    expect(button).not.toBeDisabled();
    fireEvent.click(button);
    expect(claimMutate).toHaveBeenCalledWith({ claimToken: "tok-1", consent: true });
  });

  it("claim-token path: renders confirmation after a successful claim", () => {
    mockUseClaim.mockReturnValue(
      idleMutation({ data: { linked: true, providerPublicId: "ABCD***90" } }),
    );
    render(<ProviderClaimJourney />);
    fireEvent.click(screen.getByText(/I have a claim token/));
    expect(screen.getByTestId("provider-claim-confirmation")).toBeInTheDocument();
    expect(screen.getByText("ABCD***90")).toBeInTheDocument();
  });

  it("EC path (re-pointed): submits the EC number to the employment-match endpoint", () => {
    const matchMutate = vi.fn();
    mockUseEmploymentMatch.mockReturnValue(idleMutation({ mutate: matchMutate }));
    render(<ProviderClaimJourney />);
    fireEvent.click(screen.getByText(/EC \(employment\) number/));
    fireEvent.change(screen.getByLabelText(/EC \(employment\) number/), {
      target: { value: " EC-77 " },
    });
    fireEvent.click(screen.getByRole("button", { name: /Match employment record/ }));
    // Calls the real employment-match lane (not the old report-only evidence path),
    // with a trimmed EC number and no Health ID in the body (server-authoritative).
    expect(matchMutate).toHaveBeenCalledWith({ ecNumber: "EC-77" });
  });

  it("EC path (re-pointed): renders EMPLOYMENT_MATCHED asserted on an ASSERTED trust assertion", () => {
    mockUseEmploymentMatch.mockReturnValue(
      idleMutation({
        data: {
          status: "OK",
          maskedEcNumber: "EC***",
          matchStatus: "MATCHED_BOTH",
          trustAssertion: {
            status: "ASSERTED",
            providerPublicId: "ABCD***90",
            trustLevel: "EMPLOYMENT_MATCHED",
            sourceOfRecord: "varapi-service (provider trust ledger)",
          },
        },
      }),
    );
    render(<ProviderClaimJourney />);
    fireEvent.click(screen.getByText(/EC \(employment\) number/));
    expect(screen.getByTestId("provider-claim-ec-asserted")).toBeInTheDocument();
    expect(screen.getByText(/EMPLOYMENT_MATCHED\s+asserted/)).toBeInTheDocument();
    expect(screen.getByText("EC***")).toBeInTheDocument();
    expect(screen.getByText("ABCD***90")).toBeInTheDocument();
  });

  it("EC path (re-pointed): a matched employee without a provider gets honest guidance, never a fake claim", () => {
    mockUseEmploymentMatch.mockReturnValue(
      idleMutation({
        data: {
          status: "OK",
          maskedEcNumber: "EC***",
          matchStatus: "MATCHED_EMPLOYMENT_ONLY",
          providerCreationRequired: true,
        },
      }),
    );
    render(<ProviderClaimJourney />);
    fireEvent.click(screen.getByText(/EC \(employment\) number/));
    expect(screen.getByTestId("provider-claim-ec-provider-required")).toBeInTheDocument();
    expect(screen.queryByTestId("provider-claim-ec-asserted")).not.toBeInTheDocument();
  });

  it("council path: shows recorded-pending state with masked evidence ref", () => {
    mockUseEvidence.mockReturnValue(
      idleMutation({
        data: {
          status: "RECORDED",
          evidenceRef: "EC***",
          providerPublicId: "ABCD***90",
          nextStep: "Verification is pending council-record review.",
        },
      }),
    );
    render(<ProviderClaimJourney />);
    fireEvent.click(screen.getByText(/I have a council number/));
    expect(screen.getByTestId("provider-claim-evidence-recorded")).toBeInTheDocument();
    expect(screen.getByText("EC***")).toBeInTheDocument();
    expect(screen.getByText(/verification pending/i)).toBeInTheDocument();
  });

  it("recovery path: confirmation shows the SAME masked provider id (recover-not-reissue)", () => {
    mockUseRecover.mockReturnValue(
      idleMutation({
        data: {
          recovered: true,
          providerPublicId: "ABCD***90",
          note: "The Provider ID is unchanged — recovery never issues a new one.",
        },
      }),
    );
    render(<ProviderClaimJourney />);
    fireEvent.click(screen.getByText(/Recover an existing profile/));
    expect(screen.getByTestId("provider-claim-recovered")).toBeInTheDocument();
    expect(screen.getByText("ABCD***90")).toBeInTheDocument();
    expect(screen.getByText(/never issues a new one/)).toBeInTheDocument();
  });

  it("recovery path: surfaces the honest no-linked-profile error", () => {
    mockUseRecover.mockReturnValue(
      idleMutation({
        isError: true,
        error: {
          status: 404,
          error: {
            code: "NO_LINKED_PROFILE",
            message: "No provider profile is linked to this Health ID.",
          },
        },
      }),
    );
    render(<ProviderClaimJourney />);
    fireEvent.click(screen.getByText(/Recover an existing profile/));
    expect(screen.getByRole("alert")).toHaveTextContent(/No provider profile is linked/);
  });
});
