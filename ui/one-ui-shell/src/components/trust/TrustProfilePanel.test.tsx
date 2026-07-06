import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TrustProfilePanel } from "./TrustProfilePanel";
import type { TrustProfile } from "@/hooks/queries/useTrustProfile";

const { useTrustProfile } = vi.hoisted(() => ({ useTrustProfile: vi.fn() }));

vi.mock("@/hooks/queries/useTrustProfile", () => ({
  useTrustProfile,
}));

// professional OK, employment UNAVAILABLE, operational OK, identity OK — proves
// each block degrades independently.
const PROFILE: TrustProfile = {
  healthId: "hid-77",
  generatedAt: "2026-07-06T00:00:00Z",
  identity: { sourceOfRecord: "identity-assurance-service", status: "OK", assurance: { loa: "LOA2" } },
  professional: {
    sourceOfRecord: "varapi-service (provider registry trust ledger)",
    status: "OK",
    providerId: "PRV-1234",
    trustLevel: "EMPLOYMENT_MATCHED",
    registryStatus: "ACTIVE",
  },
  employment: {
    sourceOfRecord: "workforce-governance-service (wgv_hsc_employment)",
    status: "UNAVAILABLE",
  },
  operational: {
    sourceOfRecord: "vashandi-workforce-service (work-context read model)",
    status: "OK",
    activeAssignmentCount: 2,
  },
};

describe("TrustProfilePanel", () => {
  beforeEach(() => {
    useTrustProfile.mockReset();
  });

  it("renders all four block cards, each citing its source of record", () => {
    useTrustProfile.mockReturnValue({ data: PROFILE, isLoading: false, isError: false });
    render(<TrustProfilePanel />);

    expect(screen.getByTestId("trust-profile-panel")).toBeInTheDocument();
    expect(screen.getByTestId("trust-block-identity")).toBeInTheDocument();
    expect(screen.getByTestId("trust-block-professional")).toBeInTheDocument();
    expect(screen.getByTestId("trust-block-employment")).toBeInTheDocument();
    expect(screen.getByTestId("trust-block-operational")).toBeInTheDocument();
    // Sources of record are surfaced, not hidden behind the composition layer.
    expect(screen.getAllByText(/Source of record:/i)).toHaveLength(4);
    expect(screen.getByText(/varapi-service/)).toBeInTheDocument();
  });

  it("shows honest degradation for an UNAVAILABLE block and fabricates nothing", () => {
    useTrustProfile.mockReturnValue({ data: PROFILE, isLoading: false, isError: false });
    render(<TrustProfilePanel />);

    const employmentCard = screen.getByTestId("trust-block-employment");
    // Honest copy — the source is named unreachable, not filled with invented rows.
    expect(employmentCard).toHaveTextContent(/nothing fabricated/i);
    // The OK-only fields never appear for an unreachable source.
    expect(employmentCard).not.toHaveTextContent(/EC number/i);
    // A degraded employment block never poisons the OK professional block.
    expect(screen.getByTestId("trust-block-professional")).toHaveTextContent("PRV-1234");
  });

  it("guides provider creation when the professional block is NO_PROVIDER_LINKED", () => {
    useTrustProfile.mockReturnValue({
      data: {
        ...PROFILE,
        professional: {
          sourceOfRecord: "varapi-service (provider registry trust ledger)",
          status: "NO_PROVIDER_LINKED",
          providerCreationRequired: true,
        },
      },
      isLoading: false,
      isError: false,
    });
    render(<TrustProfilePanel />);

    expect(screen.getByTestId("trust-professional-no-provider")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /provider claim/i })).toHaveAttribute(
      "href",
      "/citizen/provider-claim",
    );
  });

  it("surfaces an honest load failure without fabricating a profile", () => {
    useTrustProfile.mockReturnValue({ data: undefined, isLoading: false, isError: true });
    render(<TrustProfilePanel />);

    expect(screen.getByTestId("trust-profile-panel")).toHaveTextContent(/could not be loaded/i);
    expect(screen.queryByTestId("trust-block-identity")).not.toBeInTheDocument();
  });
});
