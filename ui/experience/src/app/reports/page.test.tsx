import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import ReportsHubPage from "./page";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
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

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({ data: { gold_stats: {} } }),
  },
}));

function renderPage() {
  const client = new QueryClient();
  return render(
    <QueryClientProvider client={client}>
      <ReportsHubPage />
    </QueryClientProvider>
  );
}

describe("ReportsHubPage", () => {
  it("renders category links to report workspaces", () => {
    renderPage();
    expect(screen.getByRole("heading", { level: 1, name: /^Reports$/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Clinical Reports/i })).toHaveAttribute("href", "/reports/clinical");
    expect(screen.getByRole("link", { name: /Custom Reports/i })).toHaveAttribute("href", "/reports/custom");
  });
});
