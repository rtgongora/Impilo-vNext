import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TheatreBloodPanel } from "./TheatreBloodPanel";

const get = vi.fn();
const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { get: (u: string) => get(u), post: (u: string, b: unknown) => post(u, b) } }));

describe("TheatreBloodPanel (Lane 3 — MADI blood)", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("reads blood links from the real endpoint and reflects MADI status truth (no fabrication)", async () => {
    get.mockResolvedValue({ data: [{ id: "b1", status: "GROUP_SCREEN", component_type: "PRBC", units_requested: 2, reservation_status: "PENDING", blood_group: "O_NEG" }] });
    render(<TheatreBloodPanel caseId="c-1" />);
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/blood");
    expect(await screen.findByText("GROUP_SCREEN")).toBeInTheDocument();
    expect(screen.getByText("O_NEG")).toBeInTheDocument();
  });

  it("shows an honest empty state when no blood is ordered", async () => {
    get.mockResolvedValue({ data: [] });
    render(<TheatreBloodPanel caseId="c-1" />);
    expect(await screen.findByText(/No blood ordered/i)).toBeInTheDocument();
  });

  it("requests blood against the real POST endpoint with the entered component", async () => {
    get.mockResolvedValue({ data: [] });
    post.mockResolvedValue({ data: { status: "GROUP_SCREEN" } });
    render(<TheatreBloodPanel caseId="c-9" />);
    await screen.findByText(/No blood ordered/i);
    fireEvent.click(screen.getByText("Request blood"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-9/blood/request", expect.objectContaining({ units: 2, componentType: "PRBC" })),
    );
  });
});
