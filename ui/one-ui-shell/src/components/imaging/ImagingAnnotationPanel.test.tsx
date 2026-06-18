import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ImagingAnnotationPanel } from "./ImagingAnnotationPanel";

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

describe("ImagingAnnotationPanel", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    getMock.mockResolvedValue({ data: [], meta: {} });
    postMock.mockResolvedValue({
      data: { id: 1, note: "Opacity noted", annotationType: "REVIEW_NOTE" },
      meta: {},
    });
  });

  it("renders review note form and loads existing annotations", async () => {
    getMock.mockResolvedValue({
      data: [{ id: 2, note: "Prior note", annotationType: "REVIEW_NOTE" }],
      meta: {},
    });
    renderWithClient(<ImagingAnnotationPanel governedStudyId="42" chartPatientCpid="CPID-001" />);

    expect(screen.getByText(/Imaging review notes/i)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("Prior note")).toBeInTheDocument());
    expect(getMock).toHaveBeenCalledWith("/internal/v1/imaging/studies/42/annotations?chart_patient_cpid=CPID-001");
  });

  it("posts review note to governed imaging annotations endpoint", async () => {
    renderWithClient(<ImagingAnnotationPanel governedStudyId="42" chartPatientCpid="CPID-001" />);

    fireEvent.change(screen.getByPlaceholderText(/Clinical review note/i), {
      target: { value: "Opacity noted" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Save review note/i }));

    await waitFor(() => expect(postMock).toHaveBeenCalled());
    const [path, body] = postMock.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/internal/v1/imaging/studies/42/annotations?chart_patient_cpid=CPID-001");
    expect(body.annotationType).toBe("REVIEW_NOTE");
    expect(body.note).toBe("Opacity noted");
  });
});
