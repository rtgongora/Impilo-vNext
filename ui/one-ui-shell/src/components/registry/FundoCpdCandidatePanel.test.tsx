import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { FundoCpdCandidatePanel } from "./FundoCpdCandidatePanel";

const acceptMutate = vi.fn();
const rejectMutate = vi.fn();

vi.mock("@/hooks/queries/useProviderCouncil", () => ({
  useFundoCpdCandidates: () => ({
    data: [
      {
        id: 11,
        courseId: "course-1",
        courseName: "IPC refresher",
        completedAt: "2026-06-20T10:00:00Z",
        creditsSuggested: 2,
        verificationState: "PENDING",
        externalRef: "FUNDO-CERT-0001",
      },
      {
        id: 12,
        courseId: "course-2",
        courseName: "EHR basics",
        completedAt: "2026-06-01T10:00:00Z",
        creditsSuggested: 1,
        verificationState: "ACCEPTED",
        linkedCpdEventId: 555,
      },
    ],
    isLoading: false,
  }),
  useFundoCpdCandidatesByPublicId: () => ({ data: undefined, isLoading: false }),
  useAcceptFundoCpd: () => ({ mutate: acceptMutate, isPending: false }),
  useRejectFundoCpd: () => ({ mutate: rejectMutate, isPending: false }),
}));

describe("FundoCpdCandidatePanel", () => {
  beforeEach(() => {
    acceptMutate.mockClear();
    rejectMutate.mockClear();
  });

  it("read-only mode shows states but no decision actions", () => {
    render(<FundoCpdCandidatePanel providerId="9" />);
    expect(screen.getByTestId("fundo-cpd-state-11")).toHaveTextContent("PENDING");
    expect(screen.getByTestId("fundo-cpd-state-12")).toHaveTextContent("ACCEPTED");
    expect(screen.getByText(/CPD ledger event #555/)).toBeInTheDocument();
    expect(screen.queryByTestId("fundo-cpd-accept-11")).not.toBeInTheDocument();
    expect(screen.queryByTestId("fundo-cpd-reject-11")).not.toBeInTheDocument();
  });

  it("decision mode accepts a pending candidate only", async () => {
    render(<FundoCpdCandidatePanel providerId="9" canDecide />);
    // Accepted candidate offers no further actions.
    expect(screen.queryByTestId("fundo-cpd-accept-12")).not.toBeInTheDocument();
    await userEvent.click(screen.getByTestId("fundo-cpd-accept-11"));
    expect(acceptMutate).toHaveBeenCalledWith(11, expect.anything());
  });

  it("decision mode requires the reject confirmation step and passes the reason", async () => {
    render(<FundoCpdCandidatePanel providerId="9" canDecide />);
    expect(rejectMutate).not.toHaveBeenCalled();
    await userEvent.click(screen.getByTestId("fundo-cpd-reject-11"));
    await userEvent.type(
      screen.getByTestId("fundo-cpd-reject-reason-11"),
      "Duplicate submission",
    );
    await userEvent.click(screen.getByTestId("fundo-cpd-reject-confirm-11"));
    expect(rejectMutate).toHaveBeenCalledWith(
      { candidateId: 11, reason: "Duplicate submission" },
      expect.anything(),
    );
  });
});
