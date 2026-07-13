import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ProviderCompliancePanel } from "./ProviderCompliancePanel";

const { state, openCaseMutate, recordMutate } = vi.hoisted(() => ({
  state: { compliance: [] as unknown[], disciplinary: [] as unknown[] },
  openCaseMutate: vi.fn(),
  recordMutate: vi.fn(),
}));

vi.mock("@/hooks/queries/useProviderCompliance", () => ({
  useProviderCompliance: () => ({ data: state.compliance, isPending: false }),
  useCreateComplianceAction: () => ({ mutate: vi.fn(), isPending: false }),
  useComplianceOp: () => ({ mutate: vi.fn(), isPending: false }),
  useProviderDisciplinary: () => ({ data: state.disciplinary, isPending: false }),
  useOpenDisciplinaryCase: () => ({ mutate: openCaseMutate, isPending: false }),
  useRecordDisciplinaryAction: () => ({ mutate: recordMutate, isPending: false }),
  useCloseDisciplinaryCase: () => ({ mutate: vi.fn(), isPending: false }),
}));

describe("ProviderCompliancePanel", () => {
  beforeEach(() => {
    openCaseMutate.mockReset();
    recordMutate.mockReset();
    state.compliance = [{ id: 1, actionType: "CPD_REQUIRED", status: "OPEN" }];
    state.disciplinary = [{ id: 3, triggerType: "COMPLAINT", status: "OPEN" }];
  });

  it("lists compliance actions and disciplinary cases", () => {
    render(<ProviderCompliancePanel providerPublicId="PRV-1" />);
    expect(screen.getByText(/CPD REQUIRED/)).toBeInTheDocument();
    expect(screen.getByText(/COMPLAINT/)).toBeInTheDocument();
  });

  it("records a suspension on an open disciplinary case", () => {
    render(<ProviderCompliancePanel providerPublicId="PRV-1" />);
    fireEvent.click(screen.getByRole("button", { name: "Suspend" }));
    expect(recordMutate).toHaveBeenCalledWith(
      expect.objectContaining({ caseId: 3, body: expect.objectContaining({ actionType: "SUSPENSION" }) }),
    );
  });

  it("opens a disciplinary case from the form", () => {
    render(<ProviderCompliancePanel providerPublicId="PRV-1" />);
    // First click reveals the form (header button); the form adds a second "Open case" button.
    fireEvent.click(screen.getByRole("button", { name: /^Open case$/ }));
    const buttons = screen.getAllByRole("button", { name: /^Open case$/ });
    fireEvent.click(buttons[buttons.length - 1]); // the form's submit
    expect(openCaseMutate).toHaveBeenCalled();
  });
});
