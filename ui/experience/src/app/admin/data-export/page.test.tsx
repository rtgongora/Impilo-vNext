import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DataExportPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

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

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <DataExportPage />
    </QueryClientProvider>
  );
}

describe("DataExportPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url.includes("/internal/v1/admin/reports/jobs")) {
        return Promise.resolve({
          data: [
            {
              id: "job-1",
              type: "ReportJob",
              attributes: {
                report_type: "ADMIN_DATA_EXPORT",
                status: "QUEUED",
                requested_by: "tester",
                parameters: JSON.stringify({
                  export_name: "My export",
                  format: "CSV",
                  date_from: "2026-04-01",
                  date_to: "2026-04-07",
                  data_types: ["Encounters"],
                }),
                result_url: null,
                error_message: null,
                queued_at: "2026-04-08T12:00:00Z",
                started_at: null,
                completed_at: null,
              },
            },
          ],
          meta: { page: { number: 0, size: 50, total_elements: 1, total_pages: 1 } },
        });
      }
      return Promise.reject(new Error(`unexpected ${url}`));
    });
  });

  it("lists jobs from admin reports API (not in-page MOCK_JOBS)", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("My export")).toBeInTheDocument();
    });
    expect(get.mock.calls.some((c) => String(c[0]).includes("/internal/v1/admin/reports/jobs"))).toBe(true);
  });

  it("queues export via POST /internal/v1/reports/generate with snake_case body", async () => {
    const user = userEvent.setup();
    post.mockResolvedValue({
      data: {
        id: "new-job",
        type: "ReportJob",
        attributes: {
          report_type: "ADMIN_DATA_EXPORT",
          status: "QUEUED",
          requested_by: "u",
          queued_at: "2026-04-08T12:00:00Z",
        },
      },
      meta: {},
    });

    renderPage();
    await waitFor(() => expect(screen.getByText("My export")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /New export/i }));
    await user.type(screen.getByPlaceholderText(/e.g., Monthly Patient Summary/i), "Q2 dump");
    await user.type(screen.getByLabelText("Date From"), "2026-04-01");
    await user.type(screen.getByLabelText("Date To"), "2026-04-07");

    await user.click(screen.getByRole("button", { name: /Queue Export/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalled();
    });
    const [, body] = post.mock.calls[0] as [string, Record<string, unknown>];
    expect(body).toMatchObject({
      report_type: "ADMIN_DATA_EXPORT",
      requested_by: expect.any(String),
    });
    expect(body.parameters).toMatchObject({
      export_name: "Q2 dump",
      format: "CSV",
      date_from: "2026-04-01",
      date_to: "2026-04-07",
    });
  });
});

