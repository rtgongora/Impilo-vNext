import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ControlTowerPage from "./page";

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

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } | null }) => unknown) =>
    selector({ facility: { id: "f1000000-0000-0000-0000-000000000001", name: "Harare Central" } }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function mockOpsApis() {
  get.mockImplementation((url: string) => {
    if (url.includes("/internal/v1/beds/wards")) {
      return Promise.resolve({
        data: [
          {
            id: "w1",
            type: "ward",
            attributes: {
              name: "Medical",
              wardType: "GENERAL",
              floor: "1",
              totalBeds: 4,
              availableBeds: 2,
              occupiedBeds: 2,
              status: "ACTIVE",
            },
          },
        ],
        meta: {},
      });
    }
    if (url.includes("/internal/v1/beds?")) {
      return Promise.resolve({
        data: [
          {
            id: "b1",
            type: "bed",
            attributes: {
              bedNumber: "1",
              bedType: "STANDARD",
              status: "OCCUPIED",
              wardId: "w1",
              wardName: "Medical",
              acuity: "MEDIUM",
              patientId: "p1",
              patientName: "Test Patient",
              admittedAt: "2026-04-01T10:00:00Z",
              assignedDoctor: null,
            },
          },
        ],
        meta: {},
      });
    }
    if (url.includes("/internal/v1/queue/entries")) {
      return Promise.resolve({ data: [], meta: {} });
    }
    if (url.includes("/internal/v1/staffing/roster-week")) {
      return Promise.resolve({
        data: [
          {
            id: "s1",
            type: "StaffingShift",
            attributes: {
              user_id: "u1",
              staff_display_name: "Staff One",
              facility_id: "f1000000-0000-0000-0000-000000000001",
              workspace_id: null,
              status: "ACTIVE",
              started_at: "2026-04-08T06:00:00Z",
              ended_at: null,
            },
          },
        ],
        meta: {},
      });
    }
    return Promise.resolve({ data: [], meta: {} });
  });

  post.mockImplementation((url: string) => {
    if (url.includes("/internal/v1/queue/entries/stats")) {
      return Promise.resolve({
        data: {
          waiting: 0,
          called: 0,
          inService: 0,
          completed: 0,
          noShow: 0,
          avgWaitSeconds: 0,
        },
      });
    }
    return Promise.resolve({});
  });
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ControlTowerPage />
    </QueryClientProvider>
  );
}

describe("ControlTowerPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    mockOpsApis();
  });

  it("fetches live BFF endpoints instead of in-page MOCK fixtures", async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Facility overview")).toBeInTheDocument();
    });

    expect(screen.getByRole("link", { name: "Facility operations hub" })).toHaveAttribute(
      "href",
      "/facility-operations",
    );

    expect(get).toHaveBeenCalled();
    expect(post).toHaveBeenCalledWith("/internal/v1/queue/entries/stats", {
      facilityId: "f1000000-0000-0000-0000-000000000001",
    });

    const urls = get.mock.calls.map((c) => String(c[0]));
    expect(urls.some((u) => u.includes("/internal/v1/beds/wards"))).toBe(true);
    expect(urls.some((u) => u.includes("/internal/v1/beds?"))).toBe(true);
    expect(urls.some((u) => u.includes("/internal/v1/queue/entries"))).toBe(true);
    expect(urls.some((u) => u.includes("/internal/v1/staffing/roster-week"))).toBe(true);
  });
});
