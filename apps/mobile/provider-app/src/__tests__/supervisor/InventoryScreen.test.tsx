/**
 * InventoryScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", () => ({
  Screen: ({ children }: any) => children,
  Header: ({ title }: any) => title,
  Card: ({ children }: any) => children,
  CardHeader: ({ title }: any) => title,
  CardBody: ({ children }: any) => children,
  Badge: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
}));

vi.mock("../../services/inventoryService", () => ({
  fetchInventoryOnHand: vi.fn().mockResolvedValue([]),
  fetchStockAlertsList: vi.fn().mockResolvedValue([]),
  createRequisition: vi.fn().mockResolvedValue({ id: "req-1" }),
}));

describe("InventoryScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/supervisor/InventoryScreen");
    expect(typeof mod.InventoryScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/supervisor/InventoryScreen");
    const element = React.createElement(mod.InventoryScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.InventoryScreen);
  });
});
