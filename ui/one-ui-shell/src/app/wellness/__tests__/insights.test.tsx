import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn() },
}));
vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

import { apiClient } from "@/lib/api-client";
import WellnessInsightsPage from "../insights/page";

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <WellnessInsightsPage />
    </QueryClientProvider>,
  );
}

describe("WellnessInsightsPage", () => {
  beforeEach(() => get.mockReset());

  it("renders aggregate wellness metrics from the sovereign dashboard endpoint", async () => {
    get.mockResolvedValue({
      data: {
        enrolled_persons: 42,
        active_enrolments: 30,
        assessments_completed: 18,
        screening_coverage_pct: 61,
        high_risk_escalations: 4,
      },
    });

    renderPage();

    expect(await screen.findByText("42")).toBeInTheDocument();
    expect(screen.getByText("Enrolled persons")).toBeInTheDocument();
    expect(screen.getByText("61%")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/wellness/aggregate/dashboard");
  });
});
