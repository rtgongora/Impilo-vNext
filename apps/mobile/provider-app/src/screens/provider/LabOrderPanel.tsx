/**
 * LabOrderPanel — Lab order creation within an encounter.
 */

import React, { useState, useCallback, useEffect } from "react";
import { View, Text, StyleSheet } from "react-native";
import { Card, CardBody, Button, TextField, Select, Badge, LabResultCard, ErrorState } from "@impilo/mobile-design-system";
import { createLabOrder, getLabOrdersForEncounter } from "../../services/labService";
import { encounterStore, useEncounterStore } from "../../stores/encounterStore";

interface LabOrderPanelProps {
  encounterId: string;
  patientId: string;
}

const COMMON_TESTS = [
  { value: "FBC", label: "Full Blood Count" },
  { value: "U_E", label: "Urea & Electrolytes" },
  { value: "LFT", label: "Liver Function Tests" },
  { value: "RBS", label: "Random Blood Sugar" },
  { value: "HIV_TEST", label: "HIV Rapid Test" },
  { value: "MALARIA_RDT", label: "Malaria RDT" },
  { value: "URINALYSIS", label: "Urinalysis" },
  { value: "CRP", label: "C-Reactive Protein" },
  { value: "HBA1C", label: "HbA1c" },
  { value: "LIPID_PANEL", label: "Lipid Panel" },
];

export function LabOrderPanel({ encounterId, patientId }: LabOrderPanelProps) {
  const { labOrders } = useEncounterStore();
  const [testCode, setTestCode] = useState("FBC");
  const [urgency, setUrgency] = useState<"ROUTINE" | "URGENT" | "STAT">("ROUTINE");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getLabOrdersForEncounter(encounterId)
      .then((orders) => orders.forEach((o) => encounterStore.getState().addLabOrder(o)))
      .catch(() => {});
  }, [encounterId]);

  const handleOrder = useCallback(async () => {
    setSaving(true);
    setError(null);
    const test = COMMON_TESTS.find((t) => t.value === testCode);
    try {
      const order = await createLabOrder({
        encounterId,
        patientId,
        testName: test?.label ?? testCode,
        testCode,
        urgency,
      });
      encounterStore.getState().addLabOrder(order);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to order lab");
    } finally {
      setSaving(false);
    }
  }, [encounterId, patientId, testCode, urgency]);

  return (
    <View testID="lab-order-panel">
      <Card>
        <CardBody>
          <View style={styles.inputRow}>
            <Select
              label="Test"
              value={testCode}
              options={COMMON_TESTS}
              onChange={setTestCode}
              testID="lab-test-select"
            />
            <Select
              label="Urgency"
              value={urgency}
              options={[
                { value: "ROUTINE", label: "Routine" },
                { value: "URGENT", label: "Urgent" },
                { value: "STAT", label: "STAT" },
              ]}
              onChange={(v: string) => setUrgency(v as "ROUTINE" | "URGENT" | "STAT")}
              testID="lab-urgency-select"
            />
            <Button
              title="Order"
              onPress={handleOrder}
              loading={saving}
              testID="order-lab-btn"
            />
          </View>
          {error ? <ErrorState title="Error" message={error} /> : null}
        </CardBody>
      </Card>

      <View style={styles.ordersList}>
        {labOrders.map((order) => (
          <Card key={order.id}>
            <CardBody>
              <View testID={`lab-order-${order.id}`}>
                <View style={styles.orderHeader}>
                  <Text style={styles.bold}>{order.testName}</Text>
                  <Badge variant={order.urgency === "STAT" ? "destructive" : "outline"}>
                    {order.urgency}
                  </Badge>
                </View>
                <Badge variant="secondary">{order.status}</Badge>
                {order.results && order.results.length > 0
                  ? order.results.map((r) => (
                      <LabResultCard
                        key={r.id}
                        parameter={r.parameter}
                        value={r.value}
                        unit={r.unit}
                        referenceRange={r.referenceRange}
                        isAbnormal={r.isAbnormal}
                      />
                    ))
                  : null}
              </View>
            </CardBody>
          </Card>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  inputRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "flex-end",
  },
  ordersList: {
    marginTop: 12,
  },
  orderHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  bold: {
    fontWeight: "bold",
  },
});
