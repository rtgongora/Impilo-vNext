import type { ReactNode } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ImagingPage from "./page";

vi.mock("next/navigation", () => ({ useParams: () => ({ patientId: "patient-1" }) }));
vi.mock("@/components/EHRLayout", () => ({ EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div> }));
vi.mock("@/hooks/useFacilityStore", () => ({ useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) => selector({ facility: { id: "facility-1", name: "Harare Central Hospital" } }) }));
vi.mock("@/hooks/queries/useEncounters", () => ({ useEncounters: () => ({ data: { data: [{ id: "enc-1", attributes: { status: "IN_PROGRESS", encounterType: "OUTPATIENT", startedAt: "2026-04-08T09:00:00.000Z" } }] } }) }));
vi.mock("@tanstack/react-query", () => ({
  useQuery: ({ queryKey }: { queryKey: unknown[] }) => {
    if (queryKey[0] === "pacs" && queryKey[1] === "studies") {
      return {
        data: [
          {
            ID: "study-1",
            MainDicomTags: {
              StudyDate: "20260407",
              StudyDescription: "Chest X-Ray",
              StudyInstanceUID: "1.2.3",
              AccessionNumber: "ACC-1",
            },
            PatientMainDicomTags: { PatientID: "patient-1", PatientName: "Tariro Moyo" },
            Series: ["series-1"],
          },
        ],
        isLoading: false,
        error: null,
      };
    }

    return {
      data: [
        { ID: "series-1", label: "Series 1", modality: "DICOM", instances: ["inst-1"] },
      ],
      isLoading: false,
      error: null,
    };
  },
}));
vi.mock("@/lib/api-client", () => ({ apiClient: { get: vi.fn() } }));

describe("ImagingPage", () => {
  it("surfaces patient-matched imaging continuity and keeps chart handoff close", () => {
    render(<ImagingPage />);

    expect(screen.getByText("Imaging continuity")).toBeInTheDocument();
    expect(screen.getByText("Imaging loop status")).toBeInTheDocument();
    expect(screen.getAllByText("Chest X-Ray")).toHaveLength(2);
    expect(screen.getByRole("link", { name: "Orders" })).toHaveAttribute("href", "/ehr/patient-1/orders");

    fireEvent.click(screen.getByRole("button", { name: "Series 1 DICOM � 1 images" }));

    expect(screen.getByText(/Reviewing Series 1/)).toBeInTheDocument();
    expect(screen.getByText("Tariro Moyo")).toBeInTheDocument();
  });
});
