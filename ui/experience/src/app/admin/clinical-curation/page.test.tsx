import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ClinicalCurationPage from "./page";

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

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

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    user: { id: "provider-1", displayName: "Dr. Moyo" },
  }),
}));

const { get, post } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <ClinicalCurationPage />
    </QueryClientProvider>,
  );
}

describe("ClinicalCurationPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();

    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/clinical/source/edliz-default-document-id") {
        return Promise.resolve({ data: { document_id: "doc-1", note: "EDLIZ" } });
      }
      if (url === "/internal/v1/clinical/source/documents/doc-1/ingestion-summary") {
        return Promise.resolve({
          data: { document_id: "doc-1", pdf_derived_section_count: 24, total_section_count: 31 },
        });
      }
      if (url === "/internal/v1/clinical/curation/review-items?status=PROPOSED") {
        return Promise.resolve({
          data: [
            {
              id: "review-1",
              review_status: "PROPOSED",
              proposed_payload: { title: "Malaria first-line regimen", excerpt: "Artemether-lumefantrine..." },
            },
          ],
        });
      }
      return Promise.resolve({ data: [] });
    });

    post.mockResolvedValue({ data: { ok: true } });
  });

  it("renders source ingestion context and the curation queue", async () => {
    renderPage();

    expect(screen.getByRole("heading", { level: 1, name: /Clinical Knowledge Curation/i })).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("doc-1")).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText("24")).toBeInTheDocument();
    });
    expect(screen.getByRole("heading", { level: 3, name: /Malaria first-line regimen/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Sidecar retirement ledger/i })).toHaveAttribute(
      "href",
      "/admin/sidecar-retirement",
    );
  });

  it("posts approve decisions through the Experience BFF contract", async () => {
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Approve/i })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /Approve/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/clinical/curation/review-items/review-1/decision",
        expect.objectContaining({
          decision: "APPROVED",
          reviewer: "provider-1",
        }),
      );
    });
  });
});
