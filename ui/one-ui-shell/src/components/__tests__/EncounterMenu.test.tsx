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

function getWizardNavLinkByHref(href: string) {
  const links = [...document.querySelectorAll(`a[href="${href}"]`)] as HTMLAnchorElement[];
  const inNav = links.find((a) => a.closest("nav"));
  if (!inNav) {
    throw new Error(`Expected wizard nav link for href ${href}`);
  }
  return inNav;
}

describe("EncounterMenu", () => {
  beforeEach(() => {
    vi.clearAllMocks();
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

  it("returns null when params is null", () => {
    mockUseParams.mockReturnValue(null);
    mockUsePathname.mockReturnValue("/ehr");

    const { container } = render(<EncounterMenu />);
    expect(container.innerHTML).toBe("");
  });

  it("renders wizard progress and first clinical phase for doctors", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/summary");

    render(<EncounterMenu />);

    expect(screen.getByText("Progress")).toBeInTheDocument();
    expect(screen.getByText("1. Assess")).toBeInTheDocument();
    expect(screen.getByText("Jane Doe")).toBeInTheDocument();
    expect(screen.getByText("CPID-001")).toBeInTheDocument();
  });

  it("renders Patient Chart link to the longitudinal chart", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/summary");

    render(<EncounterMenu />);

    const patientChartLink = screen.getByText("Patient Chart").closest("a");
    expect(patientChartLink).toHaveAttribute("href", "/ehr/P-001");
  });

  it("highlights the active wizard step", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/vitals");

    render(<EncounterMenu />);

    const vitalsLink = getWizardNavLinkByHref("/ehr/P-001/vitals");
    expect(vitalsLink.className).toContain("bg-primary-soft");
    expect(vitalsLink.className).toContain("text-primary-hover");
  });

  it("does not highlight non-active steps", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/vitals");

    render(<EncounterMenu />);

    const historyLink = getWizardNavLinkByHref("/ehr/P-001/history");
    expect(historyLink.className).not.toContain("bg-primary-soft");
  });

  it("generates correct hrefs for wizard segments", () => {
    mockUseParams.mockReturnValue({ patientId: "P-042" });
    mockUsePathname.mockReturnValue("/ehr/P-042/summary");

    render(<EncounterMenu />);

    expect(getWizardNavLinkByHref("/ehr/P-042/vitals")).toHaveAttribute("href", "/ehr/P-042/vitals");
    expect(getWizardNavLinkByHref("/ehr/P-042/medications")).toHaveAttribute("href", "/ehr/P-042/medications");
  });
});
