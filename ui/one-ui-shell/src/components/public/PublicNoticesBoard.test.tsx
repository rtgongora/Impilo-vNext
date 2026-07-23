import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PublicNoticesBoard } from "./PublicNoticesBoard";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn() },
}));

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

const CAMPAIGN = {
  id: "n1",
  category: "PUBLIC_HEALTH_CAMPAIGN",
  title: "Measles catch-up immunisation",
  body: "Children under five can be vaccinated free.",
  publishedAt: "2026-07-20T09:00:00Z",
  primaryActions: [{ label: "Find a clinic", href: "/welcome/find-care?service=IMMUNISATION" }],
};
const RECALL = {
  id: "n2",
  category: "SAFETY_RECALL",
  title: "Product recall — paracetamol syrup",
  body: "Do not use affected batches.",
  publishedAt: "2026-07-21T09:00:00Z",
};

describe("PublicNoticesBoard", () => {
  beforeEach(() => get.mockReset());

  it("lists published notices with category badge, date and actions", async () => {
    get.mockResolvedValue({ data: { notices: [CAMPAIGN, RECALL], total: 2 } });
    render(<PublicNoticesBoard />);

    expect(await screen.findByText("Measles catch-up immunisation")).toBeInTheDocument();
    expect(screen.getByText("Product recall — paracetamol syrup")).toBeInTheDocument();
    // Category badge + a deep-linked action.
    expect(screen.getByText("Campaign")).toBeInTheDocument();
    expect(screen.getByText("Safety / recall")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Find a clinic" })).toHaveAttribute(
      "href",
      "/welcome/find-care?service=IMMUNISATION",
    );
    // First call is unfiltered.
    expect(get).toHaveBeenCalledWith(expect.stringContaining("/internal/v1/public/gateway/notices?"));
    expect(get.mock.calls[0][0]).not.toContain("category=");
  });

  it("filters by category when a chip is chosen", async () => {
    get.mockResolvedValue({ data: { notices: [CAMPAIGN], total: 1 } });
    render(<PublicNoticesBoard />);
    await screen.findByText("Measles catch-up immunisation");

    get.mockResolvedValue({ data: { notices: [RECALL], total: 1 } });
    fireEvent.click(screen.getByTestId("notices-filter-SAFETY_RECALL"));

    await waitFor(() =>
      expect(get.mock.calls.at(-1)?.[0]).toContain("category=SAFETY_RECALL"),
    );
    expect(await screen.findByText("Product recall — paracetamol syrup")).toBeInTheDocument();
  });

  it("shows an honest empty state", async () => {
    get.mockResolvedValue({ data: { notices: [], total: 0 } });
    render(<PublicNoticesBoard />);
    expect(await screen.findByTestId("notices-empty")).toBeInTheDocument();
  });

  it("shows an honest error state when the lane fails", async () => {
    // First load succeeds; the failing fetch is then triggered by a filter click (an
    // event inside act), matching the estate's other public-lane error tests.
    get.mockResolvedValueOnce({ data: { notices: [CAMPAIGN], total: 1 } });
    render(<PublicNoticesBoard />);
    await screen.findByText("Measles catch-up immunisation");

    get.mockRejectedValueOnce({ status: 502 });
    fireEvent.click(screen.getByTestId("notices-filter-SAFETY_RECALL"));
    expect(await screen.findByText(/temporarily unavailable/i)).toBeInTheDocument();
  });
});
