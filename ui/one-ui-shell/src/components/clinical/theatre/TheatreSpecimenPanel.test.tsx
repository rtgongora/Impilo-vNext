import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TheatreSpecimenPanel } from "./TheatreSpecimenPanel";

const get = vi.fn();
const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { get: (u: string) => get(u), post: (u: string, b: unknown) => post(u, b) } }));

describe("TheatreSpecimenPanel (Lane 3 — OROS specimens + custody)", () => {
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

  it("shows unavailable banner on list GET failure and never clears prior specimens", async () => {
    get.mockResolvedValueOnce({
      data: [{ id: "s-keep", specimen_label: "Kept specimen", status: "OPEN", collected_at: null }],
    });
    const { rerender } = render(<TheatreSpecimenPanel caseId="c-keep" />);
    expect(await screen.findByText("Kept specimen")).toBeInTheDocument();

    get.mockRejectedValueOnce({ status: 502, error: { message: "upstream down" } });
    fireEvent.click(screen.getByText("Refresh"));
    expect(await screen.findByTestId("specimen-list-unavailable")).toHaveTextContent(/do not treat this as no specimens/i);
    expect(screen.getByText("Kept specimen")).toBeInTheDocument();
    // rerender keeps the same instance; assert empty-state copy is absent
    rerender(<TheatreSpecimenPanel caseId="c-keep" />);
    expect(screen.queryByText(/No specimens for this case/i)).not.toBeInTheDocument();
  });

  it("gates custody actions by stamps and posts collect with containerType/fixative", async () => {
    get.mockResolvedValue({
      data: [{ id: "s2", specimen_label: "Biopsy", status: "OPEN" }],
    });
    post.mockResolvedValue({ data: {} });
    render(<TheatreSpecimenPanel caseId="c-2" />);
    await screen.findByText("Biopsy");

    expect(screen.getByTestId("collect-s2")).toBeInTheDocument();
    expect(screen.queryByTestId("confirm-label-s2")).not.toBeInTheDocument();
    expect(screen.queryByTestId("receive-s2")).not.toBeInTheDocument();

    fireEvent.change(screen.getByTestId("collect-container-s2"), { target: { value: "pot" } });
    fireEvent.change(screen.getByTestId("collect-fixative-s2"), { target: { value: "formalin" } });
    fireEvent.click(screen.getByTestId("collect-s2"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/theatre/cases/c-2/specimens/s2/collect",
        expect.objectContaining({ containerType: "pot", fixative: "formalin" }),
      ),
    );
  });

  it("offers confirm-label only after collect, receive after label, adequacy after receive", async () => {
    get.mockResolvedValue({
      data: [{
        id: "s3",
        specimen_label: "Node",
        status: "OPEN",
        collected_at: "2026-07-01T10:00:00Z",
        label_confirmed_at: null,
      }],
    });
    post.mockResolvedValue({ data: {} });
    render(<TheatreSpecimenPanel caseId="c-3" />);
    await screen.findByText("Node");
    expect(screen.queryByTestId("collect-s3")).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId("confirm-label-s3"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-3/specimens/s3/confirm-label", {}),
    );

    get.mockResolvedValue({
      data: [{
        id: "s3",
        specimen_label: "Node",
        collected_at: "2026-07-01T10:00:00Z",
        label_confirmed_at: "2026-07-01T10:05:00Z",
      }],
    });
    fireEvent.click(screen.getByText("Refresh"));
    fireEvent.click(await screen.findByTestId("receive-s3"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-3/specimens/s3/receive", {}),
    );

    get.mockResolvedValue({
      data: [{
        id: "s3",
        specimen_label: "Node",
        collected_at: "2026-07-01T10:00:00Z",
        label_confirmed_at: "2026-07-01T10:05:00Z",
        received_at: "2026-07-01T11:00:00Z",
        adequacy: "NOT_ASSESSED",
      }],
    });
    fireEvent.click(screen.getByText("Refresh"));
    fireEvent.click(await screen.findByTestId("adequacy-adequate-s3"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/theatre/cases/c-3/specimens/s3/adequacy",
        { adequacy: "ADEQUATE" },
      ),
    );
  });
});
