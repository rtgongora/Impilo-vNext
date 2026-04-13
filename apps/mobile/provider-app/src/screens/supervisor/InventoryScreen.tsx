/**
 * InventoryScreen — Enhanced inventory management for supervisor mode.
 *
 * Features:
 * - Stock alerts banner (low stock + stockout counts)
 * - On-hand inventory list with status badges (OK/LOW/OUT)
 * - Create Requisition form (item, quantity, urgency, notes)
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  ScrollView,
  TextInput,
  Alert,
  StyleSheet,
} from "react-native";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Badge,
  Button,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import {
  fetchInventoryOnHand,
  fetchStockAlertsList,
  createRequisition,
} from "../../services/inventoryService";
import type {
  InventoryItem,
  StockAlert,
  RequisitionUrgency,
} from "../../types";

type SectionView = "inventory" | "alerts" | "requisition";

const STATUS_BADGE: Record<string, "primary" | "secondary" | "destructive"> = {
  OK: "primary",
  LOW: "secondary",
  OUT: "destructive",
};

const URGENCY_OPTIONS: RequisitionUrgency[] = [
  "ROUTINE",
  "URGENT",
  "EMERGENCY",
];

export function InventoryScreen() {
  const [section, setSection] = useState<SectionView>("inventory");
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [alerts, setAlerts] = useState<StockAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Requisition form state
  const [reqItemName, setReqItemName] = useState("");
  const [reqQuantity, setReqQuantity] = useState("");
  const [reqUrgency, setReqUrgency] = useState<RequisitionUrgency>("ROUTINE");
  const [reqNotes, setReqNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [inv, al] = await Promise.all([
        fetchInventoryOnHand(),
        fetchStockAlertsList(),
      ]);
      setItems(inv);
      setAlerts(al);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load inventory"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleSubmitRequisition = useCallback(async () => {
    if (!reqItemName || !reqQuantity) {
      Alert.alert("Validation", "Item name and quantity are required");
      return;
    }
    const qty = parseInt(reqQuantity, 10);
    if (isNaN(qty) || qty <= 0) {
      Alert.alert("Validation", "Quantity must be a positive number");
      return;
    }
    setSubmitting(true);
    try {
      await createRequisition({
        itemId: reqItemName.toLowerCase().replace(/\s+/g, "-"),
        itemName: reqItemName,
        quantity: qty,
        urgency: reqUrgency,
        notes: reqNotes || undefined,
      });
      Alert.alert("Success", "Requisition submitted successfully");
      setReqItemName("");
      setReqQuantity("");
      setReqUrgency("ROUTINE");
      setReqNotes("");
      setSection("inventory");
    } catch (err) {
      Alert.alert(
        "Error",
        err instanceof Error ? err.message : "Failed to submit requisition"
      );
    } finally {
      setSubmitting(false);
    }
  }, [reqItemName, reqQuantity, reqUrgency, reqNotes]);

  const lowStockCount = alerts.filter(
    (a) => a.alertType === "LOW_STOCK"
  ).length;
  const stockoutCount = alerts.filter(
    (a) => a.alertType === "STOCKOUT"
  ).length;

  return (
    <Screen>
      <Header title="Inventory Management" />
      <View testID="inventory-screen" style={styles.container}>
        {/* Stock alerts banner */}
        {alerts.length > 0 && (
          <View style={styles.alertBanner}>
            <Text style={styles.alertBannerText}>
              {lowStockCount} low stock
              {"  "}|{"  "}
              {stockoutCount} stockout
            </Text>
            <Badge variant="destructive">{`${alerts.length} alerts`}</Badge>
          </View>
        )}

        {/* Section toggle */}
        <View style={styles.toggleRow}>
          <Button
            title="On-Hand"
            variant={section === "inventory" ? "primary" : "outline"}
            onPress={() => setSection("inventory")}
            testID="section-inventory"
          />
          <Button
            title="Alerts"
            variant={section === "alerts" ? "primary" : "outline"}
            onPress={() => setSection("alerts")}
            testID="section-alerts"
          />
          <Button
            title="Requisition"
            variant={section === "requisition" ? "primary" : "outline"}
            onPress={() => setSection("requisition")}
            testID="section-requisition"
          />
        </View>

        {loading && section !== "requisition" ? (
          <LoadingSpinner size="md" />
        ) : error && section !== "requisition" ? (
          <ErrorState title="Error" message={error} onRetry={load} />
        ) : (
          <ScrollView
            style={styles.scrollArea}
            contentContainerStyle={styles.scrollContent}
          >
            {/* ── On-Hand Inventory ─────────────────────────────── */}
            {section === "inventory" &&
              (items.length === 0 ? (
                <EmptyState
                  title="No inventory"
                  message="On-hand inventory data not available"
                />
              ) : (
                items.map((item) => (
                  <Card key={item.id}>
                    <CardBody>
                      <View
                        testID={`inv-${item.id}`}
                        style={styles.itemRow}
                      >
                        <View style={styles.itemInfo}>
                          <Text style={styles.itemName}>{item.name}</Text>
                          <Text style={styles.itemCategory}>
                            {item.category}
                          </Text>
                          <Text style={styles.itemQty}>
                            {item.quantity} {item.unit}
                          </Text>
                        </View>
                        <Badge
                          variant={STATUS_BADGE[item.status] ?? "secondary"}
                        >
                          {item.status}
                        </Badge>
                      </View>
                    </CardBody>
                  </Card>
                ))
              ))}

            {/* ── Alerts ────────────────────────────────────────── */}
            {section === "alerts" &&
              (alerts.length === 0 ? (
                <EmptyState
                  title="No alerts"
                  message="All stock levels are normal"
                />
              ) : (
                alerts.map((alert) => (
                  <Card key={alert.id}>
                    <CardBody>
                      <View
                        testID={`alert-${alert.id}`}
                        style={styles.alertRow}
                      >
                        <View style={styles.alertInfo}>
                          <Text style={styles.alertItemName}>
                            {alert.itemName}
                          </Text>
                          <Text style={styles.alertDetail}>
                            Current: {alert.currentQuantity} | Reorder:{" "}
                            {alert.reorderLevel}
                          </Text>
                        </View>
                        <View style={styles.alertBadges}>
                          <Badge
                            variant={
                              alert.severity === "CRITICAL"
                                ? "destructive"
                                : "secondary"
                            }
                          >
                            {alert.severity}
                          </Badge>
                          <Badge variant="secondary">{alert.alertType}</Badge>
                        </View>
                      </View>
                    </CardBody>
                  </Card>
                ))
              ))}

            {/* ── Requisition Form ──────────────────────────────── */}
            {section === "requisition" && (
              <View style={styles.formSection}>
                <Text style={styles.formTitle}>Create Requisition</Text>

                <Text style={styles.formLabel}>Item Name</Text>
                <TextInput
                  style={styles.input}
                  placeholder="Enter item name"
                  value={reqItemName}
                  onChangeText={setReqItemName}
                  testID="req-item-name"
                />

                <Text style={styles.formLabel}>Quantity</Text>
                <TextInput
                  style={styles.input}
                  placeholder="Enter quantity"
                  value={reqQuantity}
                  onChangeText={setReqQuantity}
                  keyboardType="numeric"
                  testID="req-quantity"
                />

                <Text style={styles.formLabel}>Urgency</Text>
                <View style={styles.urgencyRow}>
                  {URGENCY_OPTIONS.map((opt) => (
                    <Button
                      key={opt}
                      title={opt}
                      variant={reqUrgency === opt ? "primary" : "outline"}
                      size="sm"
                      onPress={() => setReqUrgency(opt)}
                      testID={`urgency-${opt}`}
                    />
                  ))}
                </View>

                <Text style={styles.formLabel}>Notes (optional)</Text>
                <TextInput
                  style={styles.textArea}
                  placeholder="Additional notes..."
                  value={reqNotes}
                  onChangeText={setReqNotes}
                  multiline
                  testID="req-notes"
                />

                <Button
                  title={submitting ? "Submitting..." : "Submit Requisition"}
                  variant="primary"
                  onPress={handleSubmitRequisition}
                  disabled={!reqItemName || !reqQuantity || submitting}
                  testID="submit-requisition"
                />
              </View>
            )}
          </ScrollView>
        )}

        <View style={styles.refreshContainer}>
          <Button
            title="Refresh"
            variant="outline"
            onPress={load}
            testID="refresh-inventory"
          />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
  },
  alertBanner: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    backgroundColor: "#FEF2F2",
    borderRadius: 8,
    padding: 12,
    marginBottom: 12,
    borderLeftWidth: 4,
    borderLeftColor: "#EF4444",
  },
  alertBannerText: {
    fontSize: 13,
    fontWeight: "600",
    color: "#991B1B",
  },
  toggleRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 16,
  },
  scrollArea: {
    flex: 1,
  },
  scrollContent: {
    gap: 12,
    paddingBottom: 16,
  },
  itemRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  itemInfo: {
    flex: 1,
    gap: 2,
  },
  itemName: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
  },
  itemCategory: {
    fontSize: 12,
    color: "#6B7280",
  },
  itemQty: {
    fontSize: 13,
    fontWeight: "600",
    color: "#374151",
  },
  alertRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  alertInfo: {
    flex: 1,
    gap: 2,
  },
  alertItemName: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
  },
  alertDetail: {
    fontSize: 12,
    color: "#6B7280",
  },
  alertBadges: {
    alignItems: "flex-end",
    gap: 4,
  },
  formSection: {
    gap: 12,
  },
  formTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
  },
  formLabel: {
    fontSize: 13,
    fontWeight: "600",
    color: "#374151",
  },
  input: {
    borderWidth: 1,
    borderColor: "#D1D5DB",
    borderRadius: 8,
    padding: 10,
    fontSize: 14,
  },
  textArea: {
    borderWidth: 1,
    borderColor: "#D1D5DB",
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    minHeight: 80,
    textAlignVertical: "top",
  },
  urgencyRow: {
    flexDirection: "row",
    gap: 8,
  },
  refreshContainer: {
    marginTop: 8,
  },
});
