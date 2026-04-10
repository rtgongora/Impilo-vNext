import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ClinicalReportsPage from "./page";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
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

const { post } = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { post, get: vi.fn() },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ClinicalReportsPage />
    </QueryClientProvider>
  );
}

describe("ClinicalReportsPage", () => {
  beforeEach(() => {
    push.mockReset();
    post.mockReset();
    post.mockResolvedValue({
      data: {
        id: "job-clin",
        type: "ReportJob",
        attributes: {
          report_type: "DAILY_PATIENT_CENSUS",
          status: "QUEUED",
          requested_by: "t",
          queued_at: "2026-04-09T00:00:00Z",
        },
      },
      meta: {},
    });
  });

  it("queues clinical report via POST /internal/v1/reports/generate", async () => {
    const user = userEvent.setup();
    renderPage();

    const dates = document.querySelectorAll('input[type="date"]');
    await user.type(dates[0], "2026-04-01");
    await user.type(dates[1], "2026-04-09");

    await user.click(screen.getByRole("button", { name: /Generate Report/i }));

    await waitFor(() => expect(post).toHaveBeenCalled());
    const [path, body] = post.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toContain("/internal/v1/reports/generate");
    expect(body).toMatchObject({
      report_type: "DAILY_PATIENT_CENSUS",
      requested_by: expect.any(String),
    });
    expect(body.parameters).toMatchObject({
      dateFrom: "2026-04-01",
      dateTo: "2026-04-09",
      category: "clinical",
    });
    await waitFor(() => expect(push).toHaveBeenCalledWith("/reports/job-clin"));
  });
});
