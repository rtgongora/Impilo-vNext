import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TheatreCountPanel } from "./TheatreCountPanel";

const get = vi.fn();
const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { get: (u: string) => get(u), post: (u: string, b: unknown) => post(u, b) } }));

describe("TheatreCountPanel (Lane 3 — surgical counts + WHO Sign-Out gate)", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("makes the Sign-Out BLOCK visible on a non-zero discrepancy (retained-item risk)", async () => {
    get.mockResolvedValue({ data: [{ id: "c1", kind: "SWAB", seq: 1, baseline_count: 10, closing_count: 9, discrepancy: 1, reconciled: false, rito_case_ref: "rito-abc" }] });
    render(<TheatreCountPanel caseId="c-1" />);
    expect(await screen.findByTestId("signout-gate-blocked")).toHaveTextContent(/BLOCKED/i);
  });

  it("shows the gate CLEAR when counts reconcile", async () => {
    get.mockResolvedValue({ data: [{ id: "c1", kind: "SWAB", seq: 1, baseline_count: 10, closing_count: 10, discrepancy: 0, reconciled: true }] });
    render(<TheatreCountPanel caseId="c-1" />);
    expect(await screen.findByTestId("signout-gate-clear")).toBeInTheDocument();
  });

  it("records a count against the real endpoint", async () => {
    get.mockResolvedValue({ data: [] });
    post.mockResolvedValue({ data: { discrepancy: 0 } });
    render(<TheatreCountPanel caseId="c-7" />);
    await screen.findByText(/No counts recorded/i);
    // baseline + closing are the two number inputs in the form row
    const spinners = screen.getAllByRole("spinbutton");
    fireEvent.change(spinners[0], { target: { value: "10" } });
    fireEvent.change(spinners[1], { target: { value: "10" } });
    fireEvent.click(screen.getByText("Record count"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-7/counts", expect.objectContaining({ kind: "SWAB", baselineCount: 10, closingCount: 10 })),
    );
  });
});
