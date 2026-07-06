import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FacilityLegitimacyPanel } from "./FacilityLegitimacyPanel";
import type { FacilityStatusComposite } from "@/hooks/queries/useFacilityStatusComposite";

function composite(overrides: Partial<FacilityStatusComposite> = {}): FacilityStatusComposite {
  return {
    facilityId: "fac-1",
    facilityCode: "ZW-HCH-001",
    facilityName: "Harare Central Hospital",
    regulatoryStatus: "REGISTERED_ACTIVE",
    regulatoryStatusUpdatedAt: null,
    certificates: [],
    sourceLegitimacy: [],
    platformAccessAllowed: true,
    reasons: [],
    ...overrides,
  };
}

describe("FacilityLegitimacyPanel", () => {
  it("renders the platform-access verdict (allowed)", () => {
    render(<FacilityLegitimacyPanel composite={composite()} facilityId="fac-1" />);
    expect(screen.getByTestId("facility-platform-access-verdict")).toHaveTextContent(
      /Allowed on platform/,
    );
  });

  it("renders a not-allowed verdict when platform access is denied", () => {
    render(
      <FacilityLegitimacyPanel
        composite={composite({ platformAccessAllowed: false, reasons: ["HPA registration expired"] })}
        facilityId="fac-1"
      />,
    );
    expect(screen.getByTestId("facility-platform-access-verdict")).toHaveTextContent(
      /Not allowed on platform/,
    );
    expect(screen.getByText(/HPA registration expired/)).toBeInTheDocument();
  });

  it("lists each per-source verdict side by side", () => {
    render(
      <FacilityLegitimacyPanel
        composite={composite({
          sourceLegitimacy: [
            { source: "HPA_LEGAL", status: "REGISTERED_CURRENT", asOf: null, evidenceRef: null, allowedOnPlatform: true, reason: null, recordedBy: null },
            { source: "MINISTRY_OPERATIONAL", status: "REGISTERED_CURRENT", asOf: null, evidenceRef: null, allowedOnPlatform: true, reason: null, recordedBy: null },
            { source: "PLATFORM_OPERATIONAL", status: "PENDING_VERIFICATION", asOf: null, evidenceRef: null, allowedOnPlatform: false, reason: null, recordedBy: null },
          ],
        })}
        facilityId="fac-1"
      />,
    );
    expect(screen.getByTestId("legitimacy-source-HPA_LEGAL")).toBeInTheDocument();
    expect(screen.getByTestId("legitimacy-source-MINISTRY_OPERATIONAL")).toBeInTheDocument();
    expect(screen.getByTestId("legitimacy-source-PLATFORM_OPERATIONAL")).toBeInTheDocument();
  });

  it("gives GOVERNMENT_OPERATIONAL_EXCEPTION distinct treatment with a mandatory reason", () => {
    render(
      <FacilityLegitimacyPanel
        composite={composite({
          sourceLegitimacy: [
            {
              source: "HPA_LEGAL",
              status: "GOVERNMENT_OPERATIONAL_EXCEPTION",
              asOf: null,
              evidenceRef: null,
              allowedOnPlatform: true,
              reason: "Public facility operating under Ministry authority despite expired HPA registration.",
              recordedBy: "registry-admin",
            },
          ],
        })}
        facilityId="fac-1"
      />,
    );
    const reason = screen.getByTestId("legitimacy-exception-reason-HPA_LEGAL");
    expect(reason).toHaveTextContent(/Government operational exception/);
    expect(reason).toHaveTextContent(/Ministry authority despite expired HPA registration/);
  });

  it("links to the facility-claim journey with the facility uuid", () => {
    render(<FacilityLegitimacyPanel composite={composite()} facilityId="fac-1" />);
    const link = screen.getByRole("link", { name: /Claim administration/ });
    expect(link).toHaveAttribute("href", "/facility/claim?facilityUuid=fac-1");
  });
});
