import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TheatreSpecimenPanel } from "./TheatreSpecimenPanel";

const get = vi.fn();
const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { get: (u: string) => get(u), post: (u: string, b: unknown) => post(u, b) } }));

describe("TheatreSpecimenPanel (Lane 3 — OROS specimens)", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("lists specimens and offers acknowledge only for a critical, unacknowledged one", async () => {
    get.mockResolvedValue({ data: [{ id: "s1", specimen_label: "Frozen section", status: "RESULTED", is_critical: true }] });
    render(<TheatreSpecimenPanel caseId="c-1" />);
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/specimens");
    fireEvent.click(await screen.findByText("Acknowledge critical"));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/specimens/s1/acknowledge-critical", expect.objectContaining({})));
  });

  it("dispatches a specimen transport leg via the real endpoint", async () => {
    get.mockResolvedValue({ data: [] });
    post.mockResolvedValue({ data: {} });
    render(<TheatreSpecimenPanel caseId="c-4" />);
    await screen.findByText(/No specimens for this case/i);
    fireEvent.click(screen.getByText("Dispatch specimen leg"));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-4/transport/specimen", expect.objectContaining({})));
  });
});
