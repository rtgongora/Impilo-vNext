/**
 * PrescriptionsSection — View prescriptions and request refills.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Card,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
  RxCard,
} from "@impilo/mobile-design-system";
import { fetchPrescriptions, requestRefill } from "../../services/prescriptionService";
import type { Prescription } from "../../types";

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  ACTIVE: "default",
  PENDING: "secondary",
  DISPENSED: "secondary",
  EXPIRED: "destructive",
  CANCELLED: "outline",
};

export function PrescriptionsSection() {
  const [prescriptions, setPrescriptions] = useState<Prescription[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [refillInProgress, setRefillInProgress] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchPrescriptions({ size: 50 });
      setPrescriptions(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleRefill = useCallback(async (rxId: string) => {
    setRefillInProgress(rxId);
    try {
      await requestRefill(rxId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setRefillInProgress(null);
    }
  }, [load]);

  if (isLoading) return React.createElement(LoadingSpinner, { size: "md" });
  if (error) return React.createElement(ErrorState, { title: "Error", message: error.message, onRetry: load });

  if (prescriptions.length === 0) {
    return React.createElement(EmptyState, {
      title: "No prescriptions",
      message: "Your prescriptions will appear here after a visit",
    });
  }

  return React.createElement(
    "div",
    { "data-testid": "prescriptions-section", style: { display: "flex", flexDirection: "column", gap: "12px" } },
    React.createElement("h3", { style: { margin: 0 } }, "Prescriptions"),
    prescriptions.map((rx) =>
      React.createElement(
        Card,
        { key: rx.id },
        React.createElement(
          CardBody,
          null,
          React.createElement(
            "div",
            { "data-testid": `prescription-${rx.id}` },
            React.createElement(
              "div",
              { style: { display: "flex", justifyContent: "space-between", alignItems: "flex-start" } },
              React.createElement(
                "div",
                null,
                React.createElement(
                  "div",
                  { style: { display: "flex", gap: "8px", alignItems: "center", marginBottom: "4px" } },
                  React.createElement("strong", { style: { fontSize: "15px" } }, rx.medicationName),
                  React.createElement(Badge, {
                    variant: STATUS_VARIANT[rx.status] ?? "outline",
                    children: rx.status,
                  })
                ),
                React.createElement("p", { style: { fontSize: "14px", color: "#374151", margin: "2px 0" } },
                  `${rx.dosage} \u2022 ${rx.frequency} \u2022 ${rx.duration}`
                ),
                React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0" } },
                  `Prescribed by ${rx.prescribedBy} at ${rx.facilityName}`
                ),
                React.createElement("p", { style: { fontSize: "13px", color: "#9CA3AF", margin: "2px 0" } },
                  `Qty: ${rx.quantity} \u2022 Refills remaining: ${rx.refillsRemaining}`
                ),
                rx.lastFilledAt
                  ? React.createElement("p", { style: { fontSize: "12px", color: "#9CA3AF", margin: "2px 0" } },
                      `Last filled: ${new Date(rx.lastFilledAt).toLocaleDateString()}`
                    )
                  : null
              ),
              rx.status === "ACTIVE" && rx.refillsRemaining > 0
                ? React.createElement(Button, {
                    title: refillInProgress === rx.id ? "Requesting..." : "Request Refill",
                    variant: "secondary",
                    size: "sm",
                    onPress: () => handleRefill(rx.id),
                    disabled: refillInProgress === rx.id,
                    testID: `refill-${rx.id}`,
                  })
                : null
            )
          )
        )
      )
    )
  );
}
