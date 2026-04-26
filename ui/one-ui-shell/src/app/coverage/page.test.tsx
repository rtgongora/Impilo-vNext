import type { ReactNode } from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CoveragePage from "./page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

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

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post: vi.fn() },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <CoveragePage />
    </QueryClientProvider>
  );
}

describe("CoveragePage", () => {
  beforeEach(() => {
    get.mockReset();
    get.mockImplementation((url: string) => {
      if (url.includes("/internal/v1/coverage/plans")) {
        return Promise.resolve({
          data: [
            {
              id: "plan-1",
              type: "CoveragePlan",
              attributes: {
                plan_code: "P1",
                plan_name: "Test Plan",
                payer_id: "pay-x",
                plan_type: "STANDARD",
                status: "ACTIVE",
              },
            },
          ],
        });
      }
      if (url.includes("/internal/v1/coverage/remittances")) {
        return Promise.resolve({ data: [] });
      }
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });
  });

  it("dashboard shows real plan counts from coverage API (no demo millions)", async () => {
    renderPage();
    await waitFor(() => {
      const card = screen.getByText("Configured plans").closest(".bg-violet-50");
      expect(card).toBeTruthy();
      expect(within(card as HTMLElement).getByText("1")).toBeInTheDocument();
    });
    expect(get.mock.calls.some((c) => String(c[0]).includes("/internal/v1/coverage/plans"))).toBe(true);
    expect(screen.queryByText("4.7M")).not.toBeInTheDocument();
  });

  it("claims tab loads list via coverage claims query when user applies filter", async () => {
    const user = userEvent.setup();
    get.mockImplementation((url: string) => {
      if (url.includes("/internal/v1/coverage/plans")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/remittances")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/claims")) {
        return Promise.resolve({
          data: [
            {
              id: "c1",
              type: "CoverageClaim",
              attributes: {
                claim_number: "CN-1",
                claim_type: "OUTPATIENT",
                total_amount: 120,
                status: "SUBMITTED",
                created_at: "2026-04-01T00:00:00Z",
                coverage_id: "cov-a",
                facility_id: "fac-1",
              },
            },
          ],
        });
      }
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });

    renderPage();
    await user.click(screen.getByRole("button", { name: /Claims/i }));

    await user.type(screen.getByLabelText(/Coverage ID \(list claims\)/i), "cov-a");
    await user.click(screen.getByRole("button", { name: /Load claims/i }));

    await waitFor(() => {
      expect(screen.getByText("CN-1")).toBeInTheDocument();
    });
    expect(get.mock.calls.some((c) => String(c[0]).includes("coverage/claims?coverageId=cov-a"))).toBe(true);
  });
});
