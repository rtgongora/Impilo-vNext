import { render, screen, fireEvent } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReportLostCard } from "./ReportLostCard";

const mutateAsync = vi.fn();
const state: { data: unknown; isPending: boolean; isError: boolean } = {
  data: undefined,
  isPending: false,
  isError: false,
};

vi.mock("@/hooks/queries/useVitoCards", () => ({
  useReportLostCard: () => ({ ...state, mutateAsync }),
}));

vi.mock("@/components/citizen/StepUpPrompt", () => ({
  StepUpPrompt: () => <div data-testid="stepup" />,
}));

const readStepUp = vi.fn(() => ({ required: false, methods: [] as string[] }));
vi.mock("@/lib/stepUp", () => ({ readStepUp: () => readStepUp() }));

describe("ReportLostCard (CJ8)", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    readStepUp.mockReturnValue({ required: false, methods: [] });
    state.data = undefined;
    state.isPending = false;
    state.isError = false;
  });

  it("reports STOLEN with a replacement request — no card id supplied by the client", () => {
    render(<ReportLostCard />);
    fireEvent.click(screen.getByText("Report a lost or stolen card"));
    fireEvent.click(screen.getByLabelText("My card was stolen"));
    // replacement checkbox defaults on
    fireEvent.click(screen.getByText("Block my card"));
    expect(mutateAsync).toHaveBeenCalledWith({ reason: "STOLEN", requestReplacement: true });
  });

  it("defaults to LOST", () => {
    render(<ReportLostCard />);
    fireEvent.click(screen.getByText("Report a lost or stolen card"));
    fireEvent.click(screen.getByText("Block my card"));
    expect(mutateAsync).toHaveBeenCalledWith({ reason: "LOST", requestReplacement: true });
  });

  it("shows the blocked confirmation on REPORTED", () => {
    state.data = { data: { status: "REPORTED", reason: "LOST", replacementRequested: true } };
    render(<ReportLostCard />);
    expect(screen.getByText(/Your card has been blocked/i)).toBeInTheDocument();
    expect(screen.getByText(/replacement card has been requested/i)).toBeInTheDocument();
  });

  it("shows a truthful message when there is no active card", () => {
    state.data = { data: { status: "NO_ACTIVE_CARD", reason: "LOST", replacementRequested: false } };
    render(<ReportLostCard />);
    expect(screen.getByText(/No active card found/i)).toBeInTheDocument();
  });

  it("surfaces the step-up prompt when verification is required", () => {
    mutateAsync.mockRejectedValueOnce(new Error("step up"));
    readStepUp.mockReturnValue({ required: true, methods: ["MFA"] });
    render(<ReportLostCard />);
    fireEvent.click(screen.getByText("Report a lost or stolen card"));
    fireEvent.click(screen.getByText("Block my card"));
    return screen.findByTestId("stepup").then((el) => expect(el).toBeInTheDocument());
  });
});
