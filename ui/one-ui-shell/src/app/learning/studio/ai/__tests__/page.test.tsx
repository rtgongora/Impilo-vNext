import type { ReactNode } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FundoStudioAiPage from "../page";

const { generateMutation, nompiloMutation } = vi.hoisted(() => ({
  generateMutation: {
    mutateAsync: vi.fn(async () => ({ data: { draftId: "d1", output: { title: "Generated draft" } } })),
  },
  nompiloMutation: {
    mutateAsync: vi.fn(async () => ({ data: { response: "Nompilo helper response" } })),
  },
}));

vi.mock("@/components/learning/FundoStudioWorkspace", () => ({
  FundoStudioWorkspace: ({ title, children }: { title: string; children: ReactNode }) => (
    <section>
      <h1>{title}</h1>
      {children}
    </section>
  ),
}));

vi.mock("@/hooks/queries/useFundoStudio", () => ({
  useGenerateFundoAiDraft: () => generateMutation,
  useNompiloAssist: () => nompiloMutation,
}));

describe("FundoStudioAiPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders and can trigger draft generation and assistant", async () => {
    render(<FundoStudioAiPage />);
    expect(screen.getByRole("heading", { name: "Studio AI" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Generate draft" }));
    fireEvent.click(screen.getByRole("button", { name: "Ask Nompilo" }));
    await waitFor(() => {
      expect(generateMutation.mutateAsync).toHaveBeenCalled();
      expect(nompiloMutation.mutateAsync).toHaveBeenCalled();
    });
  });
});
