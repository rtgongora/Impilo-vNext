/**
 * StockScreen — Stock/asset visibility with alerts and dispatch tracking.
 */

import React, { useState, useEffect, useCallback } from "react";
import { Screen, Header, Card, CardBody, Button, Badge, LoadingSpinner, EmptyState, ErrorState } from "@impilo/mobile-design-system";
import { getStockItems, getStockAlerts, getDispatches, confirmDelivery } from "../../services/inventoryService";
import { useAppStore } from "../../stores/appStore";
import type { StockItem, DispatchRecord } from "../../types";

export function StockScreen() {
  const { facilityId } = useAppStore();
  const [view, setView] = useState<"stock" | "dispatches">("stock");
  const [stockItems, setStockItems] = useState<StockItem[]>([]);
  const [alerts, setAlerts] = useState<StockItem[]>([]);
  const [dispatches, setDispatches] = useState<DispatchRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      if (view === "stock") {
        const [items, alertItems] = await Promise.all([
          getStockItems(facilityId),
          getStockAlerts(facilityId),
        ]);
        setStockItems(items);
        setAlerts(alertItems);
      } else {
        const d = await getDispatches(facilityId);
        setDispatches(d);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load inventory");
    } finally {
      setLoading(false);
    }
  }, [facilityId, view]);

  useEffect(() => { load(); }, [load]);

  const handleConfirmDelivery = useCallback(async (id: string) => {
    try {
      await confirmDelivery(id);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to confirm delivery");
    }
  }, [load]);

  const LEVEL_COLORS: Record<string, string> = {
    ADEQUATE: "primary",
    LOW: "secondary",
    CRITICAL: "destructive",
    OUT_OF_STOCK: "destructive",
  };

  return React.createElement(
    Screen, null,
    React.createElement(Header, { title: "Stock & Dispatch" }),
    React.createElement("div", { "data-testid": "stock-screen", style: { padding: "16px" } },
      React.createElement("div", { style: { display: "flex", gap: "8px", marginBottom: "16px" } },
        React.createElement(Button, { title: "Stock", variant: view === "stock" ? "primary" : "outline", onPress: () => setView("stock"), testID: "view-stock" }),
        React.createElement(Button, { title: "Dispatches", variant: view === "dispatches" ? "primary" : "outline", onPress: () => setView("dispatches"), testID: "view-dispatches" }),
        alerts.length > 0 ? React.createElement(Badge, { variant: "destructive", children: `${alerts.length} alerts` }) : null
      ),

      loading
        ? React.createElement(LoadingSpinner, { size: "md" })
        : error
          ? React.createElement(ErrorState, { title: "Error", message: error, onRetry: load })
          : view === "stock"
            ? stockItems.length === 0
              ? React.createElement(EmptyState, { title: "No stock data", message: "Inventory not loaded" })
              : stockItems.map((item) =>
                  React.createElement(Card, { key: item.id },
                    React.createElement(CardBody, null,
                      React.createElement("div", { "data-testid": `stock-${item.id}`, style: { display: "flex", justifyContent: "space-between" } },
                        React.createElement("div", null,
                          React.createElement("strong", null, item.itemName),
                          React.createElement("p", { style: { fontSize: "14px", color: "#6B7280" } }, `${item.currentQuantity} ${item.unit} (reorder: ${item.reorderLevel})`),
                          React.createElement(Badge, { variant: LEVEL_COLORS[item.level] as "primary" | "destructive" | "secondary", children: item.level })
                        ),
                        item.expiryDate
                          ? React.createElement(Badge, { variant: "outline", children: `Exp: ${new Date(item.expiryDate).toLocaleDateString()}` })
                          : null
                      )
                    )
                  )
                )
            : dispatches.length === 0
              ? React.createElement(EmptyState, { title: "No dispatches", message: "No active dispatches" })
              : dispatches.map((d) =>
                  React.createElement(Card, { key: d.id },
                    React.createElement(CardBody, null,
                      React.createElement("div", { "data-testid": `dispatch-${d.id}`, style: { display: "flex", justifyContent: "space-between" } },
                        React.createElement("div", null,
                          React.createElement("strong", null, `${d.itemName} x${d.quantity}`),
                          React.createElement("p", { style: { fontSize: "14px", color: "#6B7280" } }, `To: ${d.toFacilityName}`),
                          React.createElement(Badge, { variant: d.status === "DELIVERED" ? "primary" : "secondary", children: d.status })
                        ),
                        d.status === "IN_TRANSIT"
                          ? React.createElement(Button, { title: "Confirm", variant: "primary", size: "sm", onPress: () => handleConfirmDelivery(d.id), testID: `confirm-${d.id}` })
                          : null
                      )
                    )
                  )
                ),

      React.createElement("div", { style: { marginTop: "16px" } },
        React.createElement(Button, { title: "Refresh", variant: "outline", onPress: load, testID: "refresh-stock" })
      )
    )
  );
}
