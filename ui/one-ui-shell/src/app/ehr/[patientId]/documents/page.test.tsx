import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import DocumentsPage from "./page";

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

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    user: { displayName: "Dr. Moyo", email: "moyo@example.com" },
  }),
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

vi.mock("@/hooks/queries/useClinicalDocuments", () => ({
  useClinicalDocuments: () => ({
    data: {
      data: [
        {
          id: "doc-1",
          attributes: {
            documentType: "CLINICAL_NOTE",
            title: "Referral response",
            description: "Summary of teleconsult guidance",
            mimeType: "application/pdf",
            fileSize: 24000,
            uploadedBy: "Dr. Moyo",
            status: "ACTIVE",
            createdAt: "2026-04-08T09:30:00.000Z",
          },
        },
        {
          id: "doc-2",
          attributes: {
            documentType: "LAB_REPORT",
            title: "Troponin result",
            description: null,
            mimeType: "application/pdf",
            fileSize: 18000,
            uploadedBy: "Lab Tech",
            status: "ACTIVE",
            createdAt: "2026-04-08T10:00:00.000Z",
          },
        },
      ],
    },
    isLoading: false,
  }),
  useUploadDocument: () => ({ mutate: vi.fn(), isPending: false }),
  useUploadDocumentFile: () => ({ mutate: vi.fn(), isPending: false }),
  useRequestDocumentOcr: () => ({ mutate: vi.fn(), isPending: false }),
  useDocumentOcrStatus: () => ({ data: undefined, isLoading: false }),
}));

describe("DocumentsPage", () => {
  it("surfaces document continuity across notes, orders, and results", () => {
    render(<DocumentsPage />);

    expect(screen.getByText("Keep uploads tied to the current review so notes, results, and supporting files stay in one workflow")).toBeInTheDocument();
    expect(screen.getByText("Document continuity")).toBeInTheDocument();
    expect(screen.getByText("Diagnostic attachments")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Notes" })).toHaveAttribute("href", "/ehr/patient-1/notes");
    expect(screen.getByRole("link", { name: "Results" })).toHaveAttribute("href", "/ehr/patient-1/results");
  });
});
