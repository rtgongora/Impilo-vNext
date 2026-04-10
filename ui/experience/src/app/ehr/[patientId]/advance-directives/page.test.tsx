import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AdvanceDirectivesPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
}));

vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounters: () => ({
    data: {
      data: [
        {
          id: "enc-1",
          attributes: {
            status: "IN_PROGRESS",
            encounterType: "OUTPATIENT",
            startedAt: "2026-04-08T09:00:00.000Z",
          },
        },
      ],
    },
  }),
}));

vi.mock("@/hooks/queries/useStructuredHistory", () => ({
  __esModule: true,
  useAdvanceDirectives: () => ({
    data: {
      data: [
        {
          id: "ad-1",
          type: "DNR",
          status: "Active",
          effectiveDate: "2025-06-15",
          reviewDate: "2026-06-15",
          documentRef: "DOC-2025-0612",
          summary: "Full DNR in effect.",
          contact: "Mary M.",
          contactRelation: "Spouse",
          contactPhone: "+263 77 234 5678",
        },
        {
          id: "ad-2",
          type: "Mental Health Directive",
          status: "Pending Review",
          effectiveDate: "2024-12-01",
          reviewDate: "2025-12-01",
          documentRef: "DOC-2024-1188",
          summary: "Review preferences.",
          contact: "Dr. S. Chirwa",
          contactRelation: "Psychiatrist",
          contactPhone: "+263 24 276 1234",
        },
      ],
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
}));

describe("AdvanceDirectivesPage", () => {
  it("surfaces directive continuity with documents and team communication", () => {
    render(<AdvanceDirectivesPage />);

    expect(screen.getByText("Keep legal preferences and emergency limits visible wherever the care plan or encounter may escalate")).toBeInTheDocument();
    expect(screen.getByText("Directive continuity")).toBeInTheDocument();
    expect(screen.getByText("DNR status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Documents" })).toHaveAttribute("href", "/ehr/patient-1/documents");
    expect(screen.getByRole("link", { name: "Care Team" })).toHaveAttribute("href", "/ehr/patient-1/care-team");
  });
});
