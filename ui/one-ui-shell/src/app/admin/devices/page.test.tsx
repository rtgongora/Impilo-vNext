import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DevicesPage from "./page";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

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

vi.mock("@/components/experience/OrganizationPlaneContextBar", () => ({
  OrganizationPlaneContextBar: () => null,
}));

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <DevicesPage />
    </QueryClientProvider>,
  );
}

describe("DevicesPage", () => {
  beforeEach(() => {
    get.mockReset();
    get.mockResolvedValue({
      data: [
        {
          deviceId: "dev-a1",
          deviceName: "Test Phone",
          deviceType: "MOBILE",
          trustLevel: "MEDIUM",
          status: "ACTIVE",
          lastActivity: "2026-06-01T10:00:00Z",
        },
      ],
    });
  });

  it("loads devices from /internal/v1/devices BFF path", async () => {
    renderPage();
    await waitFor(() => {
      expect(get).toHaveBeenCalledWith("/internal/v1/devices");
      expect(screen.getByText("Test Phone")).toBeInTheDocument();
    });
  });
});
