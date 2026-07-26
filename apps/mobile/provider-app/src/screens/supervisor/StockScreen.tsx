/**
 * StockScreen — Stock/asset visibility with alerts and dispatch tracking.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Card, CardBody, Button, Badge, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
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

  useEffect(() => {
    load();
  }, [load]);

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

  return (
    <Screen>
      <Header title="Stock & Dispatch" />
      <ScrollView testID="stock-screen" style={styles.container}>
        <View style={styles.toggleRow}>
          <Button
            title="Stock"
            variant={view === "stock" ? "primary" : "outline"}
            onPress={() => setView("stock")}
            testID="view-stock"
          />
          <Button
            title="Dispatches"
            variant={view === "dispatches" ? "primary" : "outline"}
            onPress={() => setView("dispatches")}
            testID="view-dispatches"
          />
          {alerts.length > 0 && (
            <Badge variant="destructive">{`${alerts.length} alerts`}</Badge>
          )}
        </View>

        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={load} />
        ) : view === "stock" ? (
          stockItems.length === 0 ? (
            <EmptyState title="No stock data" message="Inventory not loaded" />
          ) : (
            stockItems.map((item) => (
              <Card key={item.id}>
                <CardBody>
                  <View testID={`stock-${item.id}`} style={styles.itemRow}>
                    <View>
                      <Text style={styles.boldText}>{item.itemName}</Text>
                      <Text style={styles.detailText}>
                        {`${item.currentQuantity} ${item.unit} (reorder: ${item.reorderLevel})`}
                      </Text>
                      <Badge
                        variant={LEVEL_COLORS[item.level] as "primary" | "destructive" | "secondary"}
                      >
                        {item.level}
                      </Badge>
                    </View>
                    {item.expiryDate && (
                      <Badge variant="outline">
                        {`Exp: ${new Date(item.expiryDate).toLocaleDateString()}`}
                      </Badge>
                    )}
                  </View>
                </CardBody>
              </Card>
            ))
          )
        ) : dispatches.length === 0 ? (
          <EmptyState title="No dispatches" message="No active dispatches" />
        ) : (
          dispatches.map((d) => (
            <Card key={d.id}>
              <CardBody>
                <View testID={`dispatch-${d.id}`} style={styles.itemRow}>
                  <View>
                    <Text style={styles.boldText}>{`${d.itemName} x${d.quantity}`}</Text>
                    <Text style={styles.detailText}>{`To: ${d.toFacilityName}`}</Text>
                    <Badge
                      variant={d.status === "DELIVERED" ? "primary" : "secondary"}
                    >
                      {d.status}
                    </Badge>
                  </View>
                  {d.status === "IN_TRANSIT" && (
                    <Button
                      title="Confirm"
                      variant="primary"
                      size="sm"
                      onPress={() => handleConfirmDelivery(d.id)}
                      testID={`confirm-${d.id}`}
                    />
                  )}
                </View>
              </CardBody>
            </Card>
          ))
        )}

        <View style={styles.refreshContainer}>
          <Button title="Refresh" variant="outline" onPress={load} testID="refresh-stock" />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  toggleRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 16,
  },
  itemRow: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  boldText: {
    fontWeight: "700",
  },
  detailText: {
    fontSize: 14,
    color: colors.gray[500],
  },
  refreshContainer: {
    marginTop: 16,
  },
});
