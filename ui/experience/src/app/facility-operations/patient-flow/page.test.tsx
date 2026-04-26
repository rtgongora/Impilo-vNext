import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import PatientFlowBoardPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    children,
    title,
    subtitle,
  }: {
    children: ReactNode;
    title: string;
    subtitle?: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

const facilityState = vi.hoisted(() => ({
  facility: null as { id: string; name: string } | null,
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: typeof facilityState) => unknown) => selector(facilityState),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function mockApis() {
  get.mockImplementation((url: string) => {
    if (url.includes("/internal/v1/queue/entries")) {
      if (url.includes("queue_type=")) {
        return Promise.resolve({ data: [], meta: {} });
      }
      const status = url.includes("status=WAITING")
        ? "WAITING"
        : url.includes("status=CALLED")
          ? "CALLED"
          : "IN_PROGRESS";
      if (status === "WAITING") {
        return Promise.resolve({
          data: [
            {
              id: "qe1",
              type: "queue_entry",
              attributes: {
                patientId: "patient-uuid-1",
                facilityId: "f1",
                status: "WAITING",
                priority: 1,
                queuedAt: "2026-04-12T08:00:00Z",
                patientName: "Queue Patient",
              },
            },
          ],
          meta: {},
        });
      }
      return Promise.resolve({ data: [], meta: {} });
    }
    if (url.includes("/internal/v1/appointments")) {
      return Promise.resolve({ data: [], meta: {} });
    }
    return Promise.resolve({ data: [], meta: {} });
  });

  post.mockImplementation((url: string) => {
    if (url.includes("/internal/v1/queue/entries/stats")) {
      return Promise.resolve({
        data: {
          waiting: 1,
          called: 0,
          inService: 0,
          completed: 0,
          noShow: 0,
          avgWaitSeconds: 120,
        },
      });
    }
    return Promise.resolve({});
  });
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <PatientFlowBoardPage />
    </QueryClientProvider>,
  );
}

describe("PatientFlowBoardPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    mockApis();
  });

  it("shows facility selection message when no facility is active", () => {
    facilityState.facility = null;
    renderPage();
    expect(screen.getByText(/Select a facility to load patient flow/)).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });

  it("loads queue columns and links to chart when patient id is present", async () => {
    facilityState.facility = { id: "f1", name: "Clinic" };
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Queue Patient")).toBeInTheDocument();
    });

    expect(get).toHaveBeenCalled();
    expect(post).toHaveBeenCalledWith("/internal/v1/queue/entries/stats", { facilityId: "f1" });

    const chart = screen.getByRole("link", { name: "Open chart" });
    expect(chart).toHaveAttribute("href", "/ehr/patient-uuid-1");

    expect(screen.getByRole("link", { name: /Facility operations hub/ })).toHaveAttribute(
      "href",
      "/facility-operations",
    );
  });
});
