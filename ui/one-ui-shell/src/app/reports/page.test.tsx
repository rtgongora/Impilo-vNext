import type { ReactNode } from "react";
import { render, screen, within } from "@testing-library/react";
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

vi.mock("@/hooks/queries/useReports", () => ({
  useGenerateReport: () => ({ mutate: vi.fn(), isPending: false, data: undefined }),
  useReportJob: () => ({ data: undefined, isLoading: false, isError: false }),
}));

vi.mock("@/hooks/queries/useNdila", () => ({
  useNdilaTileConfig: () => ({ data: undefined, isLoading: false, isError: false }),
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
    const workspaces = screen.getByRole("heading", { name: "Report workspaces" }).closest("section");
    expect(within(workspaces!).getByRole("link", { name: /Custom Reports/i })).toHaveAttribute(
      "href",
      "/reports/custom",
    );
  });
});
