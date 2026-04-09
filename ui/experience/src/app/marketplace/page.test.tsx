import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MarketplacePage from "./page";

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
  return render(<QueryClientProvider client={client}><MarketplacePage /></QueryClientProvider>);
}

describe("MarketplacePage", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("loads the real marketplace summary surfaces and keeps facility continuity", async () => {
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/marketplace/orders?facility_id=facility-1") {
        return Promise.resolve({ data: [{ id: "order-1", type: "MarketplaceOrder", attributes: { facility_id: "facility-1", order_number: "PO-001", status: "PENDING", total_amount: 295, currency: "USD", items: "[]", ordered_by: "Tariro", created_at: "2026-04-09T08:00:00.000Z" } }] });
      }
      if (url === "/internal/v1/marketplace/catalog") {
        return Promise.resolve({ data: [{ id: "cat-1", type: "MarketplaceCatalogItem", attributes: { name: "Courier run", description: "Same-day logistics", category: "Logistics", facility_name: "Harare Central Hospital", price: 95, currency: "USD", availability: "AVAILABLE", rating: 4.5 } }] });
      }
      if (url === "/internal/v1/marketplace/vendors") {
        return Promise.resolve({ data: [{ id: "partner-1", type: "MarketplacePartner", attributes: { name: "Harare Central Hospital", facility_id: "facility-1", service_count: 2, active_listings: 2, status: "ACTIVE", rating: 4.6 } }] });
      }
      if (url === "/internal/v1/marketplace/bookings") {
        return Promise.resolve({ data: [{ id: "booking-1", type: "MarketplaceBooking", attributes: { booking_number: "BK-001", service_name: "Courier run", provider_name: "Harare Central Hospital", status: "CONFIRMED", requested_at: "2026-04-08T08:00:00.000Z", scheduled_at: "2026-04-10T08:00:00.000Z" } }] });
      }
      return Promise.resolve({ data: [] });
    });

    renderPage();

    expect(await screen.findByText(/Harare Central Hospital marketplace workflow/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Return to inventory/i })).toHaveAttribute("href", "/inventory?source=marketplace");
    await waitFor(() => {
      expect(get).toHaveBeenCalledWith("/internal/v1/marketplace/orders?facility_id=facility-1");
      expect(get).toHaveBeenCalledWith("/internal/v1/marketplace/catalog");
      expect(get).toHaveBeenCalledWith("/internal/v1/marketplace/vendors");
      expect(get).toHaveBeenCalledWith("/internal/v1/marketplace/bookings");
    });
  });
});
