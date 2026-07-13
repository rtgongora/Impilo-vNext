import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ProviderCredentialsPanel } from "./ProviderCredentialsPanel";

const { state, verifyMutate, contextOpMutate } = vi.hoisted(() => ({
  state: { quals: [] as unknown[], contexts: [] as unknown[] },
  verifyMutate: vi.fn(),
  contextOpMutate: vi.fn(),
}));

vi.mock("@/hooks/queries/useProviderCredentials", () => ({
  useProviderQualifications: () => ({ data: state.quals, isPending: false }),
  useCreateQualification: () => ({ mutate: vi.fn(), isPending: false }),
  useVerifyQualification: () => ({ mutate: verifyMutate, isPending: false }),
  useProviderPracticeContexts: () => ({ data: state.contexts, isPending: false }),
  useCreatePracticeContext: () => ({ mutate: vi.fn(), isPending: false }),
  usePracticeContextOp: () => ({ mutate: contextOpMutate, isPending: false }),
}));

describe("ProviderCredentialsPanel", () => {
  beforeEach(() => {
    verifyMutate.mockReset();
    contextOpMutate.mockReset();
    state.quals = [{ id: 7, qualificationName: "MBChB", verificationStatus: "PENDING" }];
    state.contexts = [{ id: 9, contextType: "EMPLOYMENT", status: "AUTHORISED" }];
  });

  it("lists qualifications and practice contexts", () => {
    render(<ProviderCredentialsPanel providerPublicId="PRV-1" />);
    expect(screen.getByText(/MBChB/)).toBeInTheDocument();
    expect(screen.getByText(/EMPLOYMENT/)).toBeInTheDocument();
  });

  it("verifies a pending qualification", () => {
    render(<ProviderCredentialsPanel providerPublicId="PRV-1" />);
    fireEvent.click(screen.getByRole("button", { name: /Verify/ }));
    expect(verifyMutate).toHaveBeenCalledWith(
      expect.objectContaining({ qualificationId: 7, body: expect.objectContaining({ verificationStatus: "VERIFIED" }) }),
    );
  });

  it("revokes an authorised practice context", () => {
    render(<ProviderCredentialsPanel providerPublicId="PRV-1" />);
    fireEvent.click(screen.getByRole("button", { name: "Revoke" }));
    expect(contextOpMutate).toHaveBeenCalledWith(
      expect.objectContaining({ contextId: 9, op: "revoke" }),
    );
  });
});
