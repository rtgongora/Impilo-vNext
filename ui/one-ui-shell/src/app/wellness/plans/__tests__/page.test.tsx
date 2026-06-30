import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import WellnessPlansPage from "../page";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (sel: (s: { user: { id: string } }) => unknown) => sel({ user: { id: "CPID-1" } }),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({
  NompiloContextualGuidance: () => <div data-testid="nompilo-guidance" />,
}));

import { apiClient } from "@/lib/api-client";
const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <WellnessPlansPage />
    </QueryClientProvider>,
  );
}

describe("WellnessPlansPage", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("renders available journeys from Simba and shows a clinical-caution prompt", async () => {
    get.mockImplementation((url: string) => {
      if (url.includes("/wellness/programs")) {
        return Promise.resolve({
          data: [
            { planId: "P1", name: "30-Day Walking Journey", category: "ACTIVITY", durationDays: 30, clinicalCaution: false, description: "Walk daily" },
            { planId: "P2", name: "Diabetes Prevention", category: "CHRONIC_PREVENTION", durationDays: 56, clinicalCaution: true, description: "Reduce risk" },
          ],
        });
      }
      if (url.includes("/wellness/enrollments")) return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });

    renderPage();

    await waitFor(() => expect(screen.getByText("30-Day Walking Journey")).toBeInTheDocument());
    expect(screen.getByText("Diabetes Prevention")).toBeInTheDocument();
    // Clinical-caution journey surfaces the safe care prompt
    expect(screen.getByText(/Touches a health risk/i)).toBeInTheDocument();
    // Nompilo guidance is present
    expect(screen.getByTestId("nompilo-guidance")).toBeInTheDocument();
  });

  it("shows a truthful empty state when there are no journeys", async () => {
    get.mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/No wellness journeys are available/i)).toBeInTheDocument(),
    );
  });
});
