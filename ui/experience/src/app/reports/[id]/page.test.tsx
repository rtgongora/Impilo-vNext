import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ReportDetailPage from "./page";

const JOB_ID = "550e8400-e29b-41d4-a716-446655440000";

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: JOB_ID }),
  useRouter: () => ({ push: vi.fn() }),
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

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ReportDetailPage />
    </QueryClientProvider>,
  );
}

describe("ReportDetailPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("loads job via GET /internal/v1/reports/{id} and shows download when result_url is set", async () => {
    get.mockResolvedValue({
      data: {
        id: JOB_ID,
        type: "ReportJob",
        attributes: {
          report_type: "ADMIN_DATA_EXPORT",
          status: "COMPLETED",
          requested_by: "user@example.com",
          parameters: "{}",
          result_url: "https://example.com/export.csv",
          error_message: null,
          queued_at: "2026-04-09T10:00:00.000Z",
          started_at: "2026-04-09T10:00:01.000Z",
          completed_at: "2026-04-09T10:00:05.000Z",
        },
      },
      meta: {},
    });

    renderPage();

    await waitFor(() => expect(get).toHaveBeenCalledWith(`/internal/v1/reports/${JOB_ID}`));

    expect(await screen.findByRole("link", { name: /open download/i })).toHaveAttribute(
      "href",
      "https://example.com/export.csv",
    );
    expect(screen.getByText(/ADMIN DATA EXPORT/i)).toBeInTheDocument();
  });

  it("shows 404 messaging when the job is not found", async () => {
    get.mockRejectedValue({ status: 404 });

    renderPage();

    expect(await screen.findByText(/not found/i)).toBeInTheDocument();
  });

  it("shows retry when status is FAILED", async () => {
    get.mockResolvedValue({
      data: {
        id: JOB_ID,
        type: "ReportJob",
        attributes: {
          report_type: "CUSTOM_CLINICAL",
          status: "FAILED",
          requested_by: "u",
          parameters: '{"dateFrom":"2026-01-01"}',
          result_url: null,
          error_message: "worker timeout",
          queued_at: "2026-04-09T10:00:00.000Z",
          started_at: null,
          completed_at: "2026-04-09T10:01:00.000Z",
        },
      },
      meta: {},
    });

    post.mockResolvedValue({
      data: { id: "660e8400-e29b-41d4-a716-446655440001", type: "ReportJob", attributes: {} },
      meta: {},
    });

    renderPage();

    expect(await screen.findByText("worker timeout")).toBeInTheDocument();
    const retry = screen.getByRole("button", { name: /queue again/i });
    expect(retry).toBeInTheDocument();
  });
});
