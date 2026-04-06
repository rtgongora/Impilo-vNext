import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { EncounterMenu } from "../EncounterMenu";

// Mock Next.js navigation hooks
const mockUseParams = vi.fn();
const mockUsePathname = vi.fn();

vi.mock("next/navigation", () => ({
  useParams: () => mockUseParams(),
  usePathname: () => mockUsePathname(),
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
  "Consults & Referrals",
  "Visit Outcome",
];

describe("EncounterMenu", () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
      "Summary", "Timeline",
      "Vitals", "Conditions", "History",
      "Allergies", "Immunizations",
      "Medications", "Orders", "Results", "Imaging",
      "Consults", "Teleconsults", "Documents",
      "Notes", "Discharge",
    ];

    for (const item of expectedItems) {
      expect(screen.getByText(item)).toBeInTheDocument();
    }
  });

  it("highlights the active segment with blue styling", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/vitals");

    render(<EncounterMenu />);

    const vitalsLink = screen.getByText("Vitals").closest("a");
    expect(vitalsLink?.className).toContain("bg-blue-50");
    expect(vitalsLink?.className).toContain("text-blue-700");
  });

  it("does not highlight non-active segments", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/vitals");

    render(<EncounterMenu />);

    const summaryLink = screen.getByText("Summary").closest("a");
    expect(summaryLink?.className).toContain("text-gray-600");
    expect(summaryLink?.className).not.toContain("bg-blue-50");
  });

  it("generates correct hrefs for each menu item", () => {
    mockUseParams.mockReturnValue({ patientId: "P-042" });
    mockUsePathname.mockReturnValue("/ehr/P-042/summary");

    render(<EncounterMenu />);

    const summaryLink = screen.getByText("Summary").closest("a");
    expect(summaryLink).toHaveAttribute("href", "/ehr/P-042/summary");

    const medicationsLink = screen.getByText("Medications").closest("a");
    expect(medicationsLink).toHaveAttribute("href", "/ehr/P-042/medications");
  });

  it("renders the auto-saved indicator", () => {
    mockUseParams.mockReturnValue({ patientId: "P-001" });
    mockUsePathname.mockReturnValue("/ehr/P-001/summary");

    render(<EncounterMenu />);
    expect(screen.getByText("Auto-saved")).toBeInTheDocument();
  });
});
