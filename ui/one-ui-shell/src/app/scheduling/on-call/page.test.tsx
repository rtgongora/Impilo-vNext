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

  const rotaRow = (overrides: Record<string, unknown> = {}) => ({
    id: "oc-1",
    type: "OnCallAssignment",
    attributes: {
      assignment_date: "2026-04-06",
      specialty: "Internal Medicine",
      shift_kind: "24hr",
      primary_workforce_profile_id: "11111111-1111-4111-8111-111111111111",
      primary_staff_reference: "PW-004821",
      primary_shift_id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      primary_display_name: "Dr Tendai Moyo",
      primary_profession: "MEDICAL_PRACTITIONER",
      primary_phone: "+263770000000",
      primary_phone_source: "PROVIDER_REGISTRY",
      backup_workforce_profile_id: null,
      backup_staff_reference: null,
      backup_shift_id: null,
      backup_display_name: null,
      backup_phone: null,
      ...overrides,
    },
  });

  const serveRota = (row: ReturnType<typeof rotaRow>, meta: Record<string, unknown> = {}) => {
    get.mockImplementation((path: string) => {
      if (path.includes("/internal/v1/staffing/on-call/swaps")) {
        return Promise.resolve({ data: [], meta });
      }
      return Promise.resolve({ data: [row], meta });
    });
  };

  it("shows the resolved name and the registry phone number", async () => {
    serveRota(rotaRow(), { identity_resolution: "LIVE" });

    renderPage();

    await waitFor(() => {
      expect(screen.getAllByText("Dr Tendai Moyo").length).toBeGreaterThan(0);
    });
    expect(screen.getAllByText("+263770000000").length).toBeGreaterThan(0);
  });

  /**
   * The failure this guards. A rota whose names could not be resolved must not look like a rota of
   * people who have no names — otherwise an identity-registry outage renders as normal operation.
   */
  it("says so when the registries could not be reached, and keeps the worker reference", async () => {
    serveRota(
      rotaRow({ primary_display_name: null, primary_phone: null, primary_phone_source: undefined }),
      { identity_resolution: "UNAVAILABLE" }
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/names could not be looked up/i)).toBeInTheDocument();
    });
    expect(screen.getAllByText("PW-004821").length).toBeGreaterThan(0);
  });

  it("does not warn about names when resolution was live", async () => {
    serveRota(rotaRow(), { identity_resolution: "LIVE" });

    renderPage();

    await waitFor(() => {
      expect(screen.getAllByText("Dr Tendai Moyo").length).toBeGreaterThan(0);
    });
    expect(screen.queryByText(/names could not be looked up/i)).not.toBeInTheDocument();
  });

  /** Resolving names must never turn an uncovered night into a covered-looking one. */
  it("still reports an uncovered night once names resolve", async () => {
    serveRota(rotaRow(), { identity_resolution: "LIVE" });

    renderPage();

    await waitFor(() => {
      expect(screen.getAllByText("No second on call").length).toBeGreaterThan(0);
    });
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
