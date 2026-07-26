import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RosterPage from "./page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

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

vi.mock("@/hooks/useWorkspaceStore", () => ({
  useWorkspaceStore: (selector: (state: { workspace: null }) => unknown) => selector({ workspace: null }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <RosterPage />
    </QueryClientProvider>
  );
}

describe("RosterPage", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("loads roster from staffing roster-week endpoint (not in-page fixtures)", async () => {
    get.mockResolvedValueOnce({ data: [], meta: {} });

    renderPage();

    await waitFor(() => {
      expect(get).toHaveBeenCalled();
    });

    const call = get.mock.calls.find((c) => typeof c[0] === "string" && c[0].includes("/internal/v1/staffing/roster-week"));
    expect(call).toBeDefined();
    expect(String(call?.[0])).toContain("facility_id=f1000000-0000-0000-0000-000000000001");
    expect(String(call?.[0])).toContain("week_start=");
  });

  it("renders shift-derived rows when API returns staffing shifts", async () => {
    get.mockResolvedValueOnce({
      data: [
        {
          id: "shift-1",
          type: "StaffingShift",
          attributes: {
            // Vashandi is the rostering SoR and holds no names — the worker reference is what a
            // rota can honestly show without inventing a person's name.
            workforce_profile_id: "11111111-1111-4111-8111-111111111111",
            staff_reference: "PW-004821",
            facility_id: "f1000000-0000-0000-0000-000000000001",
            shift_type: "DAY",
            on_call_role: null,
            specialty: null,
            status: "ACTIVE",
            started_at: "2000-01-01T00:00:00.000Z",
            ended_at: null,
            virtual_pool_id: null,
          },
        },
      ],
      meta: {},
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("PW-004821")).toBeInTheDocument();
    });
  });
});
