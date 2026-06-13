import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import ClientRegistryPage from "./page";

const mockUseClientRegistryClients = vi.fn();
const mockUseClientRegistryDashboard = vi.fn();

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

vi.mock("@/components/experience/RegistryPlaneContextBar", () => ({
  RegistryPlaneContextBar: () => <div data-testid="registry-plane-context-bar" />,
}));

vi.mock("@/hooks/queries/useClientRegistry", () => ({
  useClientRegistryClients: (...args: unknown[]) => mockUseClientRegistryClients(...args),
  useClientRegistryDashboard: (...args: unknown[]) => mockUseClientRegistryDashboard(...args),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ClientRegistryPage />
    </QueryClientProvider>,
  );
}

describe("ClientRegistryPage", () => {
  it("renders client intake status badges from registry data", () => {
    mockUseClientRegistryClients.mockReturnValue({
      isLoading: false,
      data: {
        data: {
          items: [
            {
              healthId: "hid-1",
              crid: "crid-12345678",
              impiloId: null,
              displayName: "Tariro Moyo",
              lifecycleStatus: "PROVISIONAL",
              verificationStatus: "UNVERIFIED",
              identityAssuranceLevel: 1,
              latestRegistrationType: "FACILITY_REGISTRATION",
              latestRegistrationChannel: "FACILITY_DESK",
              openStewardshipActions: 1,
              openMatches: 0,
            },
            {
              healthId: "hid-2",
              crid: "crid-87654321",
              impiloId: "IMP-001",
              displayName: "John Verified",
              lifecycleStatus: "ACTIVE",
              verificationStatus: "VERIFIED",
              identityAssuranceLevel: 3,
              latestRegistrationType: "SELF_INITIATED",
              latestRegistrationChannel: "SELF_SERVICE",
              openStewardshipActions: 0,
              openMatches: 0,
            },
          ],
        },
      },
    });
    mockUseClientRegistryDashboard.mockReturnValue({
      data: {
        data: {
          totalClients: 2,
          pendingVerification: 1,
          pendingMatchReview: 0,
          openStewardshipActions: 1,
        },
      },
    });
    renderPage();
    expect(screen.getByTestId("client-intake-badge-unverified")).toBeInTheDocument();
    expect(screen.getByTestId("client-intake-badge-facility-registered")).toBeInTheDocument();
    expect(screen.getByTestId("client-intake-badge-verified")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /New registration/i })).toHaveAttribute("href", "/registry/clients/new");
  });

  it("links each client row to the identity workspace route", () => {
    mockUseClientRegistryClients.mockReturnValue({
      isLoading: false,
      data: {
        data: {
          items: [
            {
              healthId: "hid-1",
              crid: "crid-12345678",
              impiloId: null,
              displayName: "Tariro Moyo",
              lifecycleStatus: "PROVISIONAL",
              verificationStatus: "UNVERIFIED",
              identityAssuranceLevel: 1,
              latestRegistrationType: "FACILITY_REGISTRATION",
              latestRegistrationChannel: "FACILITY_DESK",
              openStewardshipActions: 1,
              openMatches: 0,
            },
            {
              healthId: "hid-2",
              crid: "crid-87654321",
              impiloId: "IMP-001",
              displayName: "John Verified",
              lifecycleStatus: "ACTIVE",
              verificationStatus: "VERIFIED",
              identityAssuranceLevel: 3,
              latestRegistrationType: "SELF_INITIATED",
              latestRegistrationChannel: "SELF_SERVICE",
              openStewardshipActions: 0,
              openMatches: 0,
            },
          ],
        },
      },
    });
    mockUseClientRegistryDashboard.mockReturnValue({ data: { data: {} } });
    renderPage();
    const workspaceLinks = screen.getAllByRole("link", { name: /Open workspace/i });
    expect(workspaceLinks.map((link) => link.getAttribute("href"))).toEqual([
      "/registry/clients/hid-1",
      "/registry/clients/hid-2",
    ]);
  });

  it("shows guidance when the client list is empty and upstream is degraded", () => {
    mockUseClientRegistryClients.mockReturnValue({
      isLoading: false,
      data: {
        data: { items: [] },
        meta: {
          degraded: true,
          guidance: "Client registry is temporarily unavailable.",
        },
      },
    });
    mockUseClientRegistryDashboard.mockReturnValue({ data: { data: {} } });
    renderPage();
    expect(screen.getByText(/Client registry is temporarily unavailable/i)).toBeInTheDocument();
  });
});
