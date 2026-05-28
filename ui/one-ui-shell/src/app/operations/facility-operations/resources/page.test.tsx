import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FacilityResourcesBoardPage from "./page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

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
  apiClient: { get },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <FacilityResourcesBoardPage />
    </QueryClientProvider>,
  );
}

describe("FacilityResourcesBoardPage", () => {
  beforeEach(() => {
    get.mockReset();
    facilityState.facility = null;
  });

  it("prompts for facility when none is selected", () => {
    renderPage();
    expect(screen.getByRole("heading", { name: "Service points & resources" })).toBeInTheDocument();
    expect(screen.getByText(/Select a facility to load Tuso resources/)).toBeInTheDocument();
  });

  it("links back to the facility operations hub", () => {
    renderPage();
    expect(screen.getByRole("link", { name: /Facility operations hub/ })).toHaveAttribute(
      "href",
      "/operations/facility-operations",
    );
  });

  it("loads resources when facility is set", async () => {
    facilityState.facility = { id: "f1", name: "Test Facility" };
    get.mockResolvedValue({ data: [], meta: {} });
    renderPage();
    expect(await screen.findByText(/Loading resources/i)).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith(
      expect.stringContaining("/internal/v1/appointments/resources?facility_id=f1"),
    );
  });
});
