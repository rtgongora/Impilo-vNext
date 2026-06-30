import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TheatreBoardPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (<div><h1>{title}</h1>{children}</div>),
}));
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({ NompiloContextualGuidance: () => <div data-testid="nompilo" /> }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

describe("TheatreBoardPage", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); });

  it("renders real theatre cases from the BFF with triage + a real detail link", async () => {
    get.mockResolvedValueOnce([
      { id: "c-1", patient_id: "CPID-1", procedure_name: "Appendectomy", triage_priority: "URGENT", status: "BOOKED", surgeon_id: "surgeon-1" },
    ]);
    render(<TheatreBoardPage />);
    await waitFor(() => expect(screen.getByText("Appendectomy")).toBeInTheDocument());
    expect(screen.getByText("URGENT")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "CPID-1" })).toHaveAttribute("href", "/work/clinical/theatre/c-1");
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/queue");
  });

  it("shows an honest empty state, not a fake board", async () => {
    get.mockResolvedValueOnce([]);
    render(<TheatreBoardPage />);
    await waitFor(() => expect(screen.getByText(/No theatre cases on the board/)).toBeInTheDocument());
  });

  it("surfaces a real error instead of hiding it", async () => {
    get.mockRejectedValueOnce({ error: { message: "Upstream theatre service unavailable" } });
    render(<TheatreBoardPage />);
    await waitFor(() => expect(screen.getByText(/Upstream theatre service unavailable/)).toBeInTheDocument());
  });
});
