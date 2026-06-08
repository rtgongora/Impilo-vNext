import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DispatchDeliveryOrchestrationPanel } from "./DispatchDeliveryOrchestrationPanel";

vi.mock("@/hooks/queries/useDispatchOps", () => ({
  useDispatchDashboard: () => ({
    data: { data: { counters: { in_transit: 3, dispatch_pending: 2 } } },
    isLoading: false,
  }),
  useDispatchConsole: () => ({
    data: { data: { pending: [{ id: "d1" }, { id: "d2" }] } },
    isLoading: false,
  }),
  useDispatchDeliveries: () => ({
    data: { data: [{ id: "x1" }] },
    isLoading: false,
  }),
}));

describe("DispatchDeliveryOrchestrationPanel", () => {
  it("shows dispatch and delivery orchestration KPIs", () => {
    render(<DispatchDeliveryOrchestrationPanel />);
    expect(screen.getByTestId("dispatch-delivery-orchestration-panel")).toBeInTheDocument();
    expect(screen.getByTestId("dispatch-kpi-strip")).toHaveTextContent("2 dispatch pending");
    expect(screen.getByRole("link", { name: /NHUME dashboard/i })).toHaveAttribute("href", "/nhume/dashboard");
  });
});
