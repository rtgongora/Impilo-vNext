import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RequisitionsPage from "./page";

const { get, post, patch } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => <a href={href} {...props}>{children}</a>,
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div>,
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central Hospital" } }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post, patch },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><RequisitionsPage /></QueryClientProvider>);
}

describe("RequisitionsPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    patch.mockReset();
  });

  it("submits a real requisition to the inventory endpoint", async () => {
    get.mockResolvedValue({ data: [] });
    post.mockResolvedValue({ data: { id: "req-2", type: "InventoryRequisition", attributes: { requisition_number: "REQ-002", requested_by: "Ward 4", item_count: 5, status: "SUBMITTED", needed_by: "2026-04-11", notes: "Restock IV fluids", created_at: "2026-04-09T10:00:00.000Z" } } });

    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText(/Requested by/i), "Ward 4");
    await user.clear(screen.getByLabelText(/Number of line items/i));
    await user.type(screen.getByLabelText(/Number of line items/i), "5");
    await user.type(screen.getByLabelText(/Notes/i), "Restock IV fluids");
    await user.click(screen.getByRole("button", { name: /Submit requisition/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/inventory/requisitions", expect.objectContaining({
        facility_id: "facility-1",
        requested_by: "Ward 4",
        item_count: 5,
        notes: "Restock IV fluids",
      }));
    });
  });
});
