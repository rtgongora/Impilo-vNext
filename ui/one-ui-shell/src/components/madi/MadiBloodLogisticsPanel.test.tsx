import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MadiBloodLogisticsPanel } from "./MadiBloodLogisticsPanel";

vi.mock("@/hooks/queries/useMadi", () => ({
  useBloodBankStock: () => ({
    data: [{ quantityAvailable: 12, quantityReserved: 2 }],
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/hooks/queries/useDispatchOps", () => ({
  useDispatchDashboard: () => ({
    data: { data: { counters: { in_transit: 2, dispatch_pending: 1 } } },
    isLoading: false,
    isError: false,
  }),
  useDispatchDeliveries: () => ({
    data: {
      data: [
        { id: "d1", delivery_type: "BLOOD_PRODUCT", status: "IN_TRANSIT" },
        { id: "d2", delivery_type: "MEDICINE", status: "DELIVERED" },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

describe("MadiBloodLogisticsPanel", () => {
  it("shows MADI→Nhume boundary with blood delivery KPIs", () => {
    render(<MadiBloodLogisticsPanel />);
    expect(screen.getByTestId("madi-blood-logistics-panel")).toBeInTheDocument();
    expect(screen.getByTestId("madi-logistics-kpi-strip")).toHaveTextContent("1 dispatch pending");
    expect(screen.getByRole("link", { name: /New BLOOD_PRODUCT dispatch/i })).toHaveAttribute(
      "href",
      "/nhume/deliveries/new",
    );
  });
});
