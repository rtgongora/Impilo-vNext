import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TheatreSessionDetailPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("next/navigation", () => ({ useParams: () => ({ sessionId: "s-1" }) }));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (<div><h1>{title}</h1>{children}</div>),
}));
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({ NompiloContextualGuidance: () => <div data-testid="nompilo" /> }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

describe("TheatreSessionDetailPage", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); });

  it("renders the session with its cases and publishes to the episode seam", async () => {
    get.mockResolvedValue({ data: { id: "s-1", facilityId: "FAC-1", sessionDate: "2026-05-02", status: "DRAFT",
      items: [{ id: "i-1", patientId: "CPID-1", zibo: "ZIBO-CHOLE", status: "PLANNED" }] } });
    post.mockResolvedValueOnce({ data: { status: "PUBLISHED" } });
    render(<TheatreSessionDetailPage />);

    await waitFor(() => expect(screen.getByText("ZIBO-CHOLE")).toBeInTheDocument());
    expect(get).toHaveBeenCalledWith("/internal/v1/scheduling/theatre-sessions/s-1");

    await userEvent.click(screen.getByRole("button", { name: /Publish/ }));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/scheduling/theatre-sessions/s-1/publish", {}));
  });

  it("surfaces a publish conflict honestly", async () => {
    get.mockResolvedValue({ data: { id: "s-1", status: "DRAFT", items: [{ id: "i-1", patientId: "CPID-1" }] } });
    post.mockRejectedValueOnce({ blockers: [{ code: "SURGEON_DOUBLE_BOOKED" }] });
    render(<TheatreSessionDetailPage />);
    await waitFor(() => expect(screen.getByRole("button", { name: /Publish/ })).toBeEnabled());
    await userEvent.click(screen.getByRole("button", { name: /Publish/ }));
    await waitFor(() => expect(screen.getByText(/Publish blocked by conflicts/)).toBeInTheDocument());
  });
});
