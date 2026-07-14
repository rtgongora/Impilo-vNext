import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FundraisersPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

const ACTIVE_CASE = {
  id: "case-1",
  assistanceReference: "AS-2026-0001",
  title: "Help with surgery costs",
  story: "I need help paying for an operation.",
  category: "SURGERY",
  targetAmount: 1000,
  raisedAmount: 250,
  donorCount: 3,
  currency: "USD",
  verificationStatus: "ACTIVE",
  shareToken: "tok-1",
};

describe("FundraisersPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("renders verified public fundraiser cards with progress and donor count", async () => {
    get.mockResolvedValue([ACTIVE_CASE]);
    render(<FundraisersPage />);

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith("/internal/v1/citizen/fundraisers"),
    );
    const card = await screen.findByTestId("fundraiser-card");
    expect(card).toHaveAttribute("href", "/my-life/fundraisers/case-1");
    expect(screen.getByText("Help with surgery costs")).toBeInTheDocument();
    expect(screen.getByTestId("verified-badge")).toBeInTheDocument();
    expect(screen.getByText("25% of target")).toBeInTheDocument();
    expect(screen.getByText(/3 donors/)).toBeInTheDocument();
    expect(screen.getByText("SURGERY")).toBeInTheDocument();
  });

  it("shows my fundraisers with honest status chips (DRAFT is never dressed as live)", async () => {
    get.mockImplementation((url: string) =>
      url.endsWith("/mine")
        ? Promise.resolve([
            { ...ACTIVE_CASE, id: "case-2", verificationStatus: "DRAFT", donorCount: 0 },
          ])
        : Promise.resolve([]),
    );
    render(<FundraisersPage />);
    await userEvent.click(screen.getByTestId("fundraisers-tab-mine"));

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith("/internal/v1/citizen/fundraisers/mine"),
    );
    expect(await screen.findByTestId("status-chip")).toHaveTextContent("Draft — not public");
    expect(screen.queryByTestId("verified-badge")).not.toBeInTheDocument();
  });

  it("surfaces load errors honestly with a retry", async () => {
    get.mockRejectedValue({ status: 503, error: { message: "service is unavailable right now" } });
    render(<FundraisersPage />);

    expect(await screen.findByText("service is unavailable right now")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Retry/ })).toBeInTheDocument();
  });

  it("shows an honest empty state when no verified fundraisers are open", async () => {
    get.mockResolvedValue([]);
    render(<FundraisersPage />);

    expect(
      await screen.findByText("No verified fundraisers are open right now."),
    ).toBeInTheDocument();
  });
});
