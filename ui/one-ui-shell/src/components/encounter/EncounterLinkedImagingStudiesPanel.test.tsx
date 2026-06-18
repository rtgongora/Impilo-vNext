import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { EncounterLinkedImagingStudiesPanel } from "./EncounterLinkedImagingStudiesPanel";

const getMock = vi.fn();
const postMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
  },
}));

function renderWithClient(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("EncounterLinkedImagingStudiesPanel", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    getMock.mockResolvedValue({ data: [], meta: {} });
    postMock.mockResolvedValue({ data: { governedStudyId: "42" }, meta: {} });
  });

  it("loads and displays linked imaging studies", async () => {
    getMock.mockResolvedValue({
      data: [{ id: 1, governedStudyId: "42", orosOrderId: "OROS-1" }],
      meta: {},
    });
    renderWithClient(<EncounterLinkedImagingStudiesPanel patientId="CPID-001" encounterId="10" />);
    await waitFor(() => expect(screen.getByText(/Study 42/i)).toBeInTheDocument());
    expect(getMock).toHaveBeenCalledWith("/internal/v1/encounters/10/imaging-links");
  });

  it("posts imaging link to PCT proxy endpoint", async () => {
    renderWithClient(<EncounterLinkedImagingStudiesPanel patientId="CPID-001" encounterId="10" />);
    fireEvent.change(screen.getByPlaceholderText(/e.g. 42/i), { target: { value: "42" } });
    fireEvent.click(screen.getByRole("button", { name: /Link imaging study/i }));
    await waitFor(() => expect(postMock).toHaveBeenCalled());
    const [path, body] = postMock.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/internal/v1/encounters/10/imaging-links");
    expect(body.governed_study_id).toBe("42");
  });
});
