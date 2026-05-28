import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DistrictOperationsPage from "./page";

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

const roleState = vi.hoisted(() => ({
  isOperationsOversight: false,
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => roleState,
}));

vi.mock("@/hooks/queries/useFacilityOperations", () => ({
  useFacilityQueueSnapshots: () => ({
    data: { data: [] },
    isLoading: false,
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <DistrictOperationsPage />
    </QueryClientProvider>,
  );
}

describe("DistrictOperationsPage", () => {
  beforeEach(() => {
    get.mockReset();
    roleState.isOperationsOversight = false;
  });

  it("shows access guidance when the user lacks operations oversight", () => {
    renderPage();
    expect(screen.getByRole("heading", { name: "District operations" })).toBeInTheDocument();
    expect(screen.getByText(/does not have operations oversight roles/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Back to facility operations/ })).toHaveAttribute(
      "href",
      "/operations/facility-operations",
    );
  });

  it("renders the district board for oversight roles", async () => {
    roleState.isOperationsOversight = true;
    get.mockResolvedValue({ data: [], meta: {} });
    renderPage();
    expect(screen.getByRole("heading", { name: "District & national operations" })).toBeInTheDocument();
    expect(screen.getByLabelText(/Province filter/i)).toBeInTheDocument();
    expect(await screen.findByText(/No facilities returned for this filter/)).toBeInTheDocument();
  });
});
