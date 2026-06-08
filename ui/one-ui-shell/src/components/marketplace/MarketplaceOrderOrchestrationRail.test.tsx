import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MarketplaceOrderOrchestrationRail } from "./MarketplaceOrderOrchestrationRail";

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (s: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "fac-1", name: "Central Clinic" } }),
}));

vi.mock("@/hooks/queries/useMarketplace", () => ({
  useMarketplaceOrders: () => ({
    data: [{ id: "o1", status: "OPEN" }, { id: "o2", status: "DELIVERED" }],
    isLoading: false,
  }),
}));

describe("MarketplaceOrderOrchestrationRail", () => {
  it("shows facility-scoped order orchestration", () => {
    render(<MarketplaceOrderOrchestrationRail />);
    expect(screen.getByTestId("marketplace-order-orchestration-rail")).toBeInTheDocument();
    expect(screen.getByTestId("marketplace-order-kpi-strip")).toHaveTextContent("2 order(s)");
    expect(screen.getByRole("link", { name: /Order history/i })).toHaveAttribute("href", "/marketplace/orders");
  });
});
