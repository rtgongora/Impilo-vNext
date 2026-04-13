import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { EncounterMenu } from "../EncounterMenu";

// Mock Next.js navigation hooks
const mockUseParams = vi.fn();
const mockUsePathname = vi.fn();
const mockUsePatient = vi.fn();
const mockUseEncounters = vi.fn();
const mockUseClinicalNotes = vi.fn();
const mockUseVitals = vi.fn();

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

vi.mock("@/hooks/queries/useClinicalNotes", () => ({
  useClinicalNotes: () => mockUseClinicalNotes(),
}));

vi.mock("@/hooks/queries/useVitals", () => ({
  useVitals: () => mockUseVitals(),
}));

// Mock next/link to render a plain anchor
vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const EXPECTED_SECTIONS = [
  "Overview",
  "Assessment",
  "Problems & Diagnoses",
  "Care & Management",
  "History & Context",
  "Clinical Depth",
  "Consults & Referrals",
  "Visit Outcome",
];

function getLinkByHref(href: string) {
  const link = document.querySelector(`a[href="${href}"]`);
  if (!(link instanceof HTMLAnchorElement)) {
    throw new Error(`Expected link for href ${href}`);
  }
  return link;
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
    mockUseClinicalNotes.mockReturnValue({
      data: {
        data: [
          {
            attributes: {
              createdAt: new Date().toISOString(),
              signedAt: null,
            },
          },
        ],
      },
    });
    mockUseVitals.mockReturnValue({
      data: {
        data: [
          {
            attributes: {
              recordedAt: new Date().toISOString(),
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

  it("renders all menu sections", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/summary");

    render(<EncounterMenu />);

    for (const section of EXPECTED_SECTIONS) {
      expect(screen.getByText(section)).toBeInTheDocument();
    }
  });

  it("renders the Encounter Record header", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/summary");

    render(<EncounterMenu />);
    expect(screen.getByText("Encounter Record")).toBeInTheDocument();
    expect(screen.getByText("Clinical Documentation")).toBeInTheDocument();
    expect(screen.getByText("Jane Doe")).toBeInTheDocument();
    expect(screen.getByText("CPID-001")).toBeInTheDocument();
  });

  it("renders Patient Chart and Encounters links", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/summary");

    render(<EncounterMenu />);
    expect(screen.getByText("Patient Chart")).toBeInTheDocument();
    expect(screen.getByText("Encounters")).toBeInTheDocument();

    const patientChartLink = screen.getByText("Patient Chart").closest("a");
    expect(patientChartLink).toHaveAttribute("href", "/ehr/P-001");

    const encountersLink = screen.getByText("Encounters").closest("a");
    expect(encountersLink).toHaveAttribute("href", "/ehr/P-001/encounters");
  });

  it("renders all expected menu items", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/summary");

    render(<EncounterMenu />);

    const expectedItems = [
      "Summary", "Timeline", "IPS",
      "Vitals", "Conditions", "History",
      "Allergies", "Immunizations",
      "Medications", "Orders", "Results", "Imaging", "Care Plans", "Care Team", "Goals",
      "Family History", "Social History", "Functional Status", "Advance Directives",
      "Procedures", "Growth Chart", "Assessments",
      "Consults", "Documents",
      "Notes", "Discharge",
    ];

    for (const item of expectedItems) {
      expect(screen.getAllByText(item).length).toBeGreaterThan(0);
    }
  });

  it("highlights the active segment with blue styling", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/vitals");

    render(<EncounterMenu />);

    const vitalsLink = getLinkByHref("/ehr/P-001/vitals");
    expect(vitalsLink?.className).toContain("bg-impilo-50");
    expect(vitalsLink?.className).toContain("text-impilo-600");
  });

  it("does not highlight non-active segments", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/vitals");

    render(<EncounterMenu />);

    const summaryLink = getLinkByHref("/ehr/P-001/summary");
    expect(summaryLink?.className).toContain("text-gray-600");
    expect(summaryLink?.className).not.toContain("bg-impilo-50");
  });

  it("generates correct hrefs for each menu item", () => {
    mockUseParams.mockReturnValue({ patientId: "P-042" });
    mockUsePathname.mockReturnValue("/ehr/P-042/summary");

    render(<EncounterMenu />);

    const summaryLink = getLinkByHref("/ehr/P-042/summary");
    expect(summaryLink).toHaveAttribute("href", "/ehr/P-042/summary");

    const medicationsLink = getLinkByHref("/ehr/P-042/medications");
    expect(medicationsLink).toHaveAttribute("href", "/ehr/P-042/medications");

    const ipsLink = getLinkByHref("/ehr/P-042/ips");
    expect(ipsLink).toHaveAttribute("href", "/ehr/P-042/ips");
  });

  it("renders the live workspace and save-state indicator", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/summary");

    render(<EncounterMenu />);
    expect(screen.getByText("Workspace live")).toBeInTheDocument();
    expect(screen.getByText(/Last saved/)).toBeInTheDocument();
  });

  it("renders the active item description in the workspace header", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/summary");

    render(<EncounterMenu />);
    expect(screen.getAllByText("Patient summary and status").length).toBeGreaterThan(0);
  });
});
