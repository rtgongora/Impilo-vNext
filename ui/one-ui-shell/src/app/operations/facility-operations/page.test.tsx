import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FacilityOperationsHubPage from "./page";

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

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

const roleState = vi.hoisted(() => ({
  isClinical: false,
  isAdmin: false,
  isFinance: false,
  isDispenser: false,
  isQueueManager: true,
  isOperationsOversight: false,
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => roleState,
}));

const facilityState = vi.hoisted(() => ({
  facility: null as { id: string; name: string } | null,
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: typeof facilityState) => unknown) => selector(facilityState),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <FacilityOperationsHubPage />
    </QueryClientProvider>,
  );
}

describe("FacilityOperationsHubPage", () => {
  beforeEach(() => {
    get.mockResolvedValue({ data: [] });
  });

  it("prompts for facility when none is selected", () => {
    facilityState.facility = null;
    roleState.isQueueManager = true;
    roleState.isAdmin = false;
    renderPage();

    expect(screen.getByRole("heading", { name: "Facility operations" })).toBeInTheDocument();
    expect(screen.getByText(/Select a facility from the header context/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Facility Control Tower/ })).not.toBeInTheDocument();
  });

  it("renders role-filtered navigation cards when facility is set", () => {
    facilityState.facility = { id: "f1", name: "Test Facility" };
    roleState.isQueueManager = true;
    roleState.isAdmin = false;
    renderPage();

    expect(screen.getByText("Test Facility")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Facility Control Tower/ })).toHaveAttribute(
      "href",
      "/clinical/control-tower",
    );
    expect(screen.getByRole("link", { name: /Patient Flow Board/ })).toHaveAttribute(
      "href",
      "/operations/facility-operations/patient-flow",
    );
  });

  it("shows platform operations link for admins", () => {
    facilityState.facility = { id: "f1", name: "Test Facility" };
    roleState.isQueueManager = false;
    roleState.isAdmin = true;
    renderPage();

    expect(screen.getByRole("link", { name: /Open platform operations/ })).toHaveAttribute("href", "/operations");
  });
});
