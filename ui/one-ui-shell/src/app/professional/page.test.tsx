import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProfessionalProfilePage from "./page";

/**
 * experience-bff empty-200 honesty sweep — UI half.
 *
 * Registration status, licence validity and outstanding compliance notices are the claims on this
 * page a provider may act on. None of them may be manufactured from a failed read.
 */

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/registry/ProviderCertificatesPanel", () => ({
  ProviderCertificatesPanel: () => null,
}));
vi.mock("@/components/intelligent/NompiloHint", () => ({ NompiloHint: () => null }));

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get } }));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "u-1", providerId: "PRV-1" } }),
}));

const linkedIds = vi.hoisted(() => ({
  state: { data: undefined as unknown, isLoading: false, isError: false },
}));
vi.mock("@/hooks/queries/useLinkedIds", () => ({ useLinkedIds: () => linkedIds.state }));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ProfessionalProfilePage />
    </QueryClientProvider>,
  );
}

describe("ProfessionalProfilePage honesty", () => {
  beforeEach(() => {
    get.mockImplementation((path: string) => {
      if (path === "/internal/v1/professional/alerts") {
        return Promise.resolve({
          data: {
            id: "hid-1",
            type: "professional-alerts",
            attributes: { status: "EMPTY", items: [], summary: {}, generatedAt: "2026-07-28T00:00:00Z" },
          },
        });
      }
      return Promise.resolve({ data: [] });
    });
    linkedIds.state = { data: undefined, isLoading: false, isError: false };
  });

  it("reports registration and licence status when the registry answered", async () => {
    linkedIds.state = {
      data: { data: { attributes: { providerId: "PRV-1", providerStatus: "Active", licenceValid: true } } },
      isLoading: false,
      isError: false,
    };

    renderPage();

    expect(await screen.findByText(/Active \(MCAZ\)/)).toBeInTheDocument();
    expect(screen.getByText("Valid")).toBeInTheDocument();
  });

  it("never renders Active or Valid from an unreachable registry", async () => {
    linkedIds.state = { data: undefined, isLoading: false, isError: true };

    renderPage();

    expect(
      await screen.findByText(/Registration and licence status could not be read/i),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Active \(MCAZ\)/)).not.toBeInTheDocument();
    expect(screen.queryByText("Valid")).not.toBeInTheDocument();
  });

  it("does not issue a compliance all-clear when notices could not be read", async () => {
    get.mockImplementation((url: string) => {
      if (url.includes("/notices")) return Promise.reject(new Error("502"));
      if (url === "/internal/v1/professional/alerts") {
        return Promise.resolve({
          data: { id: "hid-1", type: "professional-alerts", attributes: { status: "EMPTY", items: [], summary: {}, generatedAt: "" } },
        });
      }
      return Promise.resolve({ data: [] });
    });

    renderPage();

    await waitFor(() =>
      expect(screen.getByText(/Professional notices could not be read/i)).toBeInTheDocument(),
    );
    expect(screen.queryByText("No professional notices at this time.")).not.toBeInTheDocument();
  });

  it("does not send an affiliated provider to chase HR when affiliations could not be read", async () => {
    get.mockImplementation((url: string) => {
      if (url.includes("/affiliations")) return Promise.reject(new Error("502"));
      if (url === "/internal/v1/professional/alerts") {
        return Promise.resolve({
          data: { id: "hid-1", type: "professional-alerts", attributes: { status: "EMPTY", items: [], summary: {}, generatedAt: "" } },
        });
      }
      return Promise.resolve({ data: [] });
    });

    renderPage();

    await waitFor(() =>
      expect(screen.getByText(/Facility affiliations could not be read/i)).toBeInTheDocument(),
    );
    expect(screen.queryByText(/No facility affiliations yet/i)).not.toBeInTheDocument();
  });

  it("shows an honest degraded warning (not a fabricated all-clear) when professional alerts could not be read", async () => {
    get.mockImplementation((url: string) =>
      url === "/internal/v1/professional/alerts" ? Promise.reject(new Error("502")) : Promise.resolve({ data: [] }),
    );

    renderPage();

    expect(
      await screen.findByText(/Professional alerts could not be read/i),
    ).toBeInTheDocument();
    expect(screen.queryByText("No professional alerts at this time.")).not.toBeInTheDocument();
  });

  it("renders a real professional alert with a review link", async () => {
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/professional/alerts") {
        return Promise.resolve({
          data: {
            id: "hid-1",
            type: "professional-alerts",
            attributes: {
              status: "OK",
              items: [{ id: "licence:1", title: "Licence expires soon", priority: "URGENT", href: "/my-professional/licence" }],
              summary: { total: 1 },
              generatedAt: "",
            },
          },
        });
      }
      return Promise.resolve({ data: [] });
    });

    renderPage();

    expect(await screen.findByText("Licence expires soon")).toBeInTheDocument();
    expect(screen.getByText("Review")).toBeInTheDocument();
  });
});
