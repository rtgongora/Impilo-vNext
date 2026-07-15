import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AnaesthesiaChartPanel } from "./AnaesthesiaChartPanel";

const get = vi.fn();
const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { get: (u: string) => get(u), post: (u: string, b: unknown) => post(u, b) } }));

describe("AnaesthesiaChartPanel (Lane 3 — multi-channel chart)", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("renders the projected MADI blood channel as read-through (not hand-entered)", async () => {
    get.mockResolvedValue({ data: { entries: [{ channel: "BLOOD", label: "PRBC", value_text: "TRANSFUSING", projected: true }] } });
    render(<AnaesthesiaChartPanel caseId="c-1" />);
    expect(get).toHaveBeenCalledWith("/internal/v1/procedures/c-1/anaesthesia/chart");
    expect(await screen.findByText(/projected · MADI/i)).toBeInTheDocument();
  });

  it("adds a chart entry via the real POST endpoint", async () => {
    get.mockResolvedValue({ data: { entries: [] } });
    post.mockResolvedValue({ data: {} });
    render(<AnaesthesiaChartPanel caseId="c-2" />);
    await screen.findByText(/No chart entries yet/i);
    fireEvent.change(screen.getByPlaceholderText("e.g. Sevoflurane"), { target: { value: "Propofol" } });
    fireEvent.click(screen.getByText("Add entry"));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/procedures/c-2/anaesthesia/chart", expect.objectContaining({ channel: "MEDICATION", label: "Propofol" })));
  });
});
