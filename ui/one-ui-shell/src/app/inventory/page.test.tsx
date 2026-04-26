import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import InventoryPage from "./page";

const { get } = vi.hoisted(() => ({
  get: vi.fn(),
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
  apiClient: { get },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><InventoryPage /></QueryClientProvider>);
}

describe("InventoryPage", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("loads facility-scoped inventory operations and surfaces low-stock handoff", async () => {
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/inventory/items?facility_id=facility-1") {
        return Promise.resolve({ data: [{ id: "item-1", type: "InventoryItem", attributes: { facility_id: "facility-1", product_code: "PARA-500", product_name: "Paracetamol 500mg", category: "Essential Medicines", quantity_on_hand: 20, reorder_level: 100, unit: "TABLET", status: "LOW_STOCK", last_counted_at: "2026-04-08T08:00:00.000Z", created_at: "2026-04-08T08:00:00.000Z", updated_at: "2026-04-08T08:00:00.000Z" } }] });
      }
      if (url === "/internal/v1/inventory/movements?facility_id=facility-1") {
        return Promise.resolve({ data: [{ id: "move-1", type: "InventoryMovement", attributes: { moved_at: "2026-04-09T08:00:00.000Z", item_name: "Paracetamol 500mg", from_location: "Central pharmacy store", to_location: "Outpatient dispensary", quantity: 20, reason: "Top-up", performed_by: "T. Moyo" } }] });
      }
      if (url === "/internal/v1/inventory/requisitions?facility_id=facility-1") {
        return Promise.resolve({ data: [{ id: "req-1", type: "InventoryRequisition", attributes: { requisition_number: "REQ-001", requested_by: "Pharmacy", item_count: 4, status: "SUBMITTED", needed_by: "2026-04-10", notes: "Urgent", created_at: "2026-04-09T08:00:00.000Z" } }] });
      }
      return Promise.resolve({ data: [] });
    });

    renderPage();

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith("/internal/v1/inventory/items?facility_id=facility-1");
      expect(get).toHaveBeenCalledWith("/internal/v1/inventory/movements?facility_id=facility-1");
      expect(get).toHaveBeenCalledWith("/internal/v1/inventory/requisitions?facility_id=facility-1");
    });
    expect((await screen.findAllByText("Paracetamol 500mg")).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole("heading", { name: "Inventory Dashboard" })).toBeInTheDocument();
    expect(screen.getByText("Harare Central Hospital")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Order through marketplace/i })).toHaveAttribute("href", "/marketplace/orders?source=inventory");
  });
});
