import type { ReactNode } from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdjudicationCaseDetail from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ caseId: "42" }),
}));
vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

const caseMock = { data: undefined as unknown, isLoading: false, isError: false };
const assignMut = { mutateAsync: vi.fn(), isPending: false };
const decideMut = { mutateAsync: vi.fn(), isPending: false };
const mergeMut = { mutateAsync: vi.fn(), isPending: false };

vi.mock("@/hooks/queries/useAbisAdjudication", () => ({
  useAdjudicationCase: () => caseMock,
  useAssignReviewer: () => assignMut,
  useRecordDecision: () => decideMut,
  useMergeCase: () => mergeMut,
}));

function confirmedDuplicateCase() {
  return {
    id: 42,
    subjectRef: "probe-1",
    modality: "FINGERPRINT",
    reason: "ENROLMENT",
    status: "RESOLVED",
    decision: "CONFIRMED_DUPLICATE",
    decidedBy: "reviewer-a",
    mergedAt: null,
    candidates: [
      { candidateSubjectRef: "existing-1", score: 0.93, summary: "engine match", rank: 0 },
    ],
  };
}

describe("AdjudicationCaseDetail", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    caseMock.data = confirmedDuplicateCase();
  });

  it("shows the governed merge section only for a confirmed, unmerged duplicate", () => {
    render(<AdjudicationCaseDetail />);
    expect(screen.getByTestId("merge-section")).toBeTruthy();
    expect(screen.getByTestId("candidate-body").textContent).toContain("existing-1");
    // Dual-control guidance is present.
    expect(screen.getByTestId("merge-section").textContent).toContain("DIFFERENT reviewer");
  });

  it("performs a governed merge with the selected survivor", async () => {
    mergeMut.mutateAsync.mockResolvedValue({ ...confirmedDuplicateCase(), mergedAt: "now" });
    render(<AdjudicationCaseDetail />);
    fireEvent.change(screen.getByTestId("survivor-select"), { target: { value: "existing-1" } });
    fireEvent.click(screen.getByTestId("merge-btn"));
    await waitFor(() => expect(mergeMut.mutateAsync).toHaveBeenCalledWith("existing-1"));
  });

  it("surfaces a dual-control conflict from the server", async () => {
    // Plain-object rejection (not `new Error`) to avoid vitest's unhandled-rejection detector.
    mergeMut.mutateAsync.mockRejectedValue({
      message: "dual control violated: the merge reviewer must differ from the decision reviewer",
    });
    render(<AdjudicationCaseDetail />);
    fireEvent.change(screen.getByTestId("survivor-select"), { target: { value: "existing-1" } });
    fireEvent.click(screen.getByTestId("merge-btn"));
    const err = await screen.findByTestId("error");
    expect(err.textContent).toContain("dual control");
  });

  it("does not show the merge section for a DISTINCT decision", () => {
    caseMock.data = { ...confirmedDuplicateCase(), decision: "DISTINCT" };
    render(<AdjudicationCaseDetail />);
    expect(screen.queryByTestId("merge-section")).toBeNull();
  });
});
