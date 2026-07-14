import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TheatreListsPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (<div><h1>{title}</h1>{children}</div>),
}));
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({ NompiloContextualGuidance: () => <div data-testid="nompilo" /> }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

describe("TheatreListsPage", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); });

  it("renders real OR sessions with a detail link", async () => {
    get.mockResolvedValueOnce([
      { id: "s-1", facilityId: "FAC-1", sessionDate: "2026-05-02", leadSurgeonRef: "surgeon-1", status: "DRAFT" },
    ]);
    render(<TheatreListsPage />);
    await waitFor(() => expect(screen.getByRole("link", { name: "2026-05-02" })).toHaveAttribute("href", "/scheduling/theatre-lists/s-1"));
    expect(screen.getByText("DRAFT")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/scheduling/theatre-sessions");
  });

  it("shows an honest empty state", async () => {
    get.mockResolvedValueOnce([]);
    render(<TheatreListsPage />);
    await waitFor(() => expect(screen.getByText(/No theatre sessions scheduled/)).toBeInTheDocument());
  });

  it("surfaces a real error", async () => {
    get.mockRejectedValueOnce({ status: 502 });
    render(<TheatreListsPage />);
    await waitFor(() => expect(screen.getByText(/HTTP 502/)).toBeInTheDocument());
  });
});
