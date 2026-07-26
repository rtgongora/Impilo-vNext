import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OnCallPage from "./page";

const { get, patch, post } = vi.hoisted(() => ({
  get: vi.fn(),
  patch: vi.fn(),
  post: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/components/experience/FacilityWorkClusterRibbon", () => ({
  FacilityWorkClusterRibbon: () => null,
}));

vi.mock("@/components/experience/OrganizationPlaneContextBar", () => ({
  OrganizationPlaneContextBar: () => null,
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } | null }) => unknown) =>
    selector({ facility: { id: "f1000000-0000-0000-0000-000000000001", name: "Harare Central" } }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, patch, post },
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <OnCallPage />
    </QueryClientProvider>
  );
}

describe("OnCallPage", () => {
  beforeEach(() => {
    get.mockReset();
    patch.mockReset();
    post.mockReset();
  });

  it("loads on-call week and swaps from staffing endpoints", async () => {
    get.mockImplementation((path: string) => {
      if (path.includes("/internal/v1/staffing/on-call/swaps")) {
        return Promise.resolve({ data: [], meta: {} });
      }
      if (path.includes("/internal/v1/staffing/on-call?")) {
        return Promise.resolve({ data: [], meta: {} });
      }
      return Promise.resolve({ data: [], meta: {} });
    });

    renderPage();

    await waitFor(() => {
      expect(get.mock.calls.length).toBeGreaterThanOrEqual(2);
    });

    const weekCall = get.mock.calls.find(
      (c) => typeof c[0] === "string" && c[0].includes("/internal/v1/staffing/on-call?") && !c[0].includes("/swaps")
    );
    const swapsCall = get.mock.calls.find((c) => typeof c[0] === "string" && c[0].includes("/internal/v1/staffing/on-call/swaps"));
    expect(weekCall).toBeDefined();
    expect(swapsCall).toBeDefined();
    expect(String(weekCall?.[0])).toContain("facility_id=f1000000-0000-0000-0000-000000000001");
  });

  it("renders seeded specialty rows from API attributes", async () => {
    get.mockImplementation((path: string) => {
      if (path.includes("/internal/v1/staffing/on-call/swaps")) {
        return Promise.resolve({ data: [], meta: {} });
      }
      return Promise.resolve({
        data: [
          {
            id: "oc-1",
            type: "OnCallAssignment",
            attributes: {
              assignment_date: "2026-04-06",
              specialty: "Internal Medicine",
              shift_kind: "24hr",
              primary_workforce_profile_id: "11111111-1111-4111-8111-111111111111",
              primary_staff_reference: "PW-004821",
              primary_shift_id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
              primary_phone: "+263770000000",
              backup_workforce_profile_id: "22222222-2222-4222-8222-222222222222",
              backup_staff_reference: "PW-009134",
              backup_shift_id: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
              backup_phone: "+263770000001",
            },
          },
        ],
        meta: {},
      });
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Internal Medicine")).toBeInTheDocument();
    });
    // The rota shows the worker reference vashandi holds, not an invented name.
    expect(screen.getAllByText("PW-004821").length).toBeGreaterThan(0);
  });
});
