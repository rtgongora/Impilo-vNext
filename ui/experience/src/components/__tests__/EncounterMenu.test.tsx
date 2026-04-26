import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { EncounterMenu } from "../EncounterMenu";

const mockUseParams = vi.fn();
const mockUsePathname = vi.fn();
const mockUsePatient = vi.fn();
const mockUseEncounters = vi.fn();

vi.mock("next/navigation", () => ({
  useParams: () => mockUseParams(),
  usePathname: () => mockUsePathname(),
}));

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatient: () => mockUsePatient(),
}));

vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounters: () => mockUseEncounters(),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (s: { user: { roles: string[] } | null }) => unknown) =>
    selector({ user: { roles: ["CLINICIAN"] } }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({
    isClinical: true,
    isPrescriber: true,
    isDispenser: false,
    isAdmin: false,
    isFinance: false,
    isPayerOps: false,
    isMsikaGovernance: false,
    isCommerce: false,
    isQueueManager: false,
    isCitizen: false,
    hasGroup: () => false,
  }),
}));

vi.mock("@/hooks/usePrivacyDisplayStore", () => ({
  usePrivacyDisplayStore: {
    getState: () => ({ level: "FULL" as const }),
  },
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

function getEncounterNavLinkByHref(href: string) {
  const links = [...document.querySelectorAll(`a[href="${href}"]`)] as HTMLAnchorElement[];
  const inNav = links.find((a) => a.closest("nav[aria-label='Encounter workspace']"));
  if (!inNav) {
    throw new Error(`Expected encounter nav link for href ${href}`);
  }
  return inNav;
}

describe("EncounterMenu", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    mockUsePatient.mockReturnValue({
      data: {
        data: {
          attributes: {
            displayName: "Jane Doe",
            cpid: "CPID-001",
          },
        },
      },
    });
    mockUseEncounters.mockReturnValue({
      data: {
        data: [
          {
            id: "enc-1",
            attributes: {
              status: "ACTIVE",
              encounterType: "OUTPATIENT",
              startedAt: new Date().toISOString(),
              closedAt: null,
            },
          },
        ],
      },
    });
  });

  it("returns null when no patientId is present", () => {
    mockUseParams.mockReturnValue({});
    mockUsePathname.mockReturnValue("/ehr");

    const { container } = render(<EncounterMenu />);
    expect(container.innerHTML).toBe("");
  });

  it("renders Lovable-parity encounter menu labels", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/summary");

    render(<EncounterMenu />);

    expect(screen.getByText("Encounter menu")).toBeInTheDocument();
    expect(screen.getByText("Overview")).toBeInTheDocument();
    expect(screen.getByText("Assessment")).toBeInTheDocument();
    expect(screen.getByText("Problems & Diagnoses")).toBeInTheDocument();
    expect(screen.getByText("Orders & Results")).toBeInTheDocument();
    expect(screen.getByText("Jane Doe")).toBeInTheDocument();
    expect(screen.getByText("CPID-001")).toBeInTheDocument();
  });

  it("renders patient overview link", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/summary");

    render(<EncounterMenu />);

    const overviewLink = screen.getByText("Patient overview").closest("a");
    expect(overviewLink).toHaveAttribute("href", "/ehr/P-001");
  });

  it("highlights assessment when on vitals route", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/vitals");

    render(<EncounterMenu />);

    const assessmentLink = getEncounterNavLinkByHref("/ehr/P-001/encounter/enc-1");
    expect(assessmentLink.className).toContain("bg-impilo-100");
  });

  it("does not highlight orders when on vitals", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/vitals");

    render(<EncounterMenu />);

    const ordersLink = getEncounterNavLinkByHref("/ehr/P-001/orders?encounter_id=enc-1");
    expect(ordersLink.className).not.toContain("bg-impilo-100");
  });

  it("generates encounter-aware assessment href when active encounter exists", () => {
    mockUseParams.mockReturnValue({ patientId: "P-042" });
    mockUsePathname.mockReturnValue("/ehr/P-042/summary");

    render(<EncounterMenu />);

    expect(getEncounterNavLinkByHref("/ehr/P-042/encounter/enc-1")).toBeTruthy();
    expect(getEncounterNavLinkByHref("/ehr/P-042/conditions?encounter_id=enc-1")).toBeTruthy();
  });
});
