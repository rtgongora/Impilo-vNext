import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ErpHrPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ErpHrPage />
    </QueryClientProvider>,
  );
}

describe("ErpHrPage — attendance + leave read Vashandi", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url.includes("/erp/hr/employees")) {
        return Promise.resolve([{ employeeId: "E-001", providerId: "PRV-7" }]);
      }
      // Vashandi-shaped attendance events (the BFF now proxies these from Vashandi)
      if (url.includes("/erp/hr/attendance")) {
        if (!url.includes("provider_worker_id=PRV-7")) {
          return Promise.reject(new Error(`attendance must use provider id: ${url}`));
        }
        return Promise.resolve([
          { eventType: "check_in", eventTime: "2026-03-02T08:00:00Z", status: "present" },
        ]);
      }
      // Vashandi-shaped leave windows
      if (url.includes("/erp/hr/leave/requests")) {
        if (!url.includes("provider_worker_id=PRV-7")) {
          return Promise.reject(new Error(`leave must use provider id: ${url}`));
        }
        return Promise.resolve([
          { leaveType: "ANNUAL", startDate: "2026-03-01", endDate: "2026-03-03", status: "approved" },
        ]);
      }
      // Everything else the page calls → empty
      return Promise.resolve([]);
    });
  });

  it("renders Vashandi attendance events and leave windows via the provider-id bridge", async () => {
    renderPage();

    // Leave window from Vashandi
    await waitFor(() => expect(screen.getByText("ANNUAL")).toBeInTheDocument());
    expect(screen.getByText("2026-03-01")).toBeInTheDocument();

    // Attendance event from Vashandi (eventType rendered via the field-fallback)
    expect(screen.getByText("check_in")).toBeInTheDocument();
  });
});
