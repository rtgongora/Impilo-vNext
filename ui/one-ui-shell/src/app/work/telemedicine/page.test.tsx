import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TelemedicineOperatingModelPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderWithQuery(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("/work/telemedicine hub", () => {
  beforeEach(() => {
    get.mockReset();
    window.localStorage.clear();
    get.mockResolvedValue({ data: null });
  });

  it("renders the operating-model doctrine and navigation sections", async () => {
    renderWithQuery(<TelemedicineOperatingModelPage />);

    expect(screen.getByText("Telemedicine operating model")).toBeInTheDocument();
    expect(screen.getByText(/service architecture, not\s+duplicate building architecture/i)).toBeInTheDocument();
    expect(screen.getByText("Virtual hospitals")).toBeInTheDocument();
    expect(screen.getByText("Routing directory")).toBeInTheDocument();
    expect(screen.getByText("Clinical groups")).toBeInTheDocument();
    expect(screen.getByText("Session modes")).toBeInTheDocument();
    expect(screen.getByText("Operations & helpdesk")).toBeInTheDocument();
  });

  it("links into the real teleconsult request flow", () => {
    renderWithQuery(<TelemedicineOperatingModelPage />);
    const ctas = screen.getAllByRole("link", { name: /new teleconsult request/i });
    expect(ctas.length).toBeGreaterThan(0);
    for (const cta of ctas) {
      expect(cta).toHaveAttribute("href", "/telemedicine/new");
    }
  });

  it("shows honest empty states without a facility context or pins", async () => {
    renderWithQuery(<TelemedicineOperatingModelPage />);
    expect(
      screen.getByText(/select a facility context to see its teleconsult backlog/i),
    ).toBeInTheDocument();
    expect(await screen.findByText(/nothing pinned yet/i)).toBeInTheDocument();
  });

  it("links encounter-mode capabilities to real existing routes only", () => {
    renderWithQuery(<TelemedicineOperatingModelPage />);
    expect(
      screen.getByText(/encounter mode — documents like a physical encounter/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Imaging worklist" })).toHaveAttribute(
      "href",
      "/imaging/worklist",
    );
    expect(screen.getByRole("link", { name: "Coverage / medical aid" })).toHaveAttribute(
      "href",
      "/coverage",
    );
    // The orders/prescribing-from-session gap is stated, not faked
    expect(
      screen.getByText(/prescribing from inside a teleconsult session itself is not wired yet/i),
    ).toBeInTheDocument();
  });

  it("reports governance coverage from the config substrate (never fake ops metrics)", () => {
    renderWithQuery(<TelemedicineOperatingModelPage />);
    expect(screen.getByText("Governance coverage")).toBeInTheDocument();
    // 11 strategic + 10 provincial institutions configured
    expect(screen.getByText("21")).toBeInTheDocument();
  });
});
