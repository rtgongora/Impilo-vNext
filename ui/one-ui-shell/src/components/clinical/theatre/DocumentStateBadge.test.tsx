import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DocumentStateBadge } from "./DocumentStateBadge";

describe("DocumentStateBadge (§19 draft/signed/amended/final distinction)", () => {
  it("renders each lifecycle state with a distinct label and data-state", () => {
    const states = [
      ["DRAFT", "Draft"],
      ["SIGNED", "Signed"],
      ["AMENDED", "Amended"],
      ["FINAL", "Final"],
    ] as const;
    for (const [state, label] of states) {
      const { unmount } = render(<DocumentStateBadge state={state} />);
      const badge = screen.getByTestId("doc-state-badge");
      expect(badge).toHaveAttribute("data-state", state);
      expect(badge).toHaveTextContent(label);
      unmount();
    }
  });

  it("draft and signed are visually distinct (colour is not the only cue — distinct classes + labels)", () => {
    const { rerender } = render(<DocumentStateBadge state="DRAFT" />);
    const draftClass = screen.getByTestId("doc-state-badge").className;
    rerender(<DocumentStateBadge state="SIGNED" />);
    const signedClass = screen.getByTestId("doc-state-badge").className;
    expect(draftClass).not.toEqual(signedClass);
    expect(draftClass).toContain("dashed"); // draft reads as provisional even in greyscale
  });

  it("exposes an accessible status role/label", () => {
    render(<DocumentStateBadge state="SIGNED" />);
    expect(screen.getByRole("status")).toHaveAttribute("aria-label", "Document state: Signed");
  });
});
