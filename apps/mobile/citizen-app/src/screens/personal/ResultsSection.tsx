/**
 * ResultsSection — View lab results and diagnostic reports.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Card,
  CardBody,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { fetchLabResults } from "../../services/labResultService";
import type { LabResult } from "../../types";

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  ORDERED: "outline",
  COLLECTED: "secondary",
  PROCESSING: "default",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
};

export function ResultsSection() {
  const [results, setResults] = useState<LabResult[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchLabResults({ size: 50 });
      setResults(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (isLoading) return React.createElement(LoadingSpinner, { size: "md" });
  if (error) return React.createElement(ErrorState, { title: "Error", message: error.message, onRetry: load });

  if (results.length === 0) {
    return React.createElement(EmptyState, {
      title: "No results",
      message: "Your lab results and reports will appear here",
    });
  }

  return React.createElement(
    "div",
    { "data-testid": "results-section", style: { display: "flex", flexDirection: "column", gap: "12px" } },
    React.createElement("h3", { style: { margin: 0 } }, "Lab Results & Reports"),
    results.map((lab) =>
      React.createElement(
        Card,
        { key: lab.id },
        React.createElement(
          CardBody,
          null,
          React.createElement(
            "div",
            {
              "data-testid": `result-${lab.id}`,
              style: { cursor: "pointer" },
              onClick: () => setExpandedId(expandedId === lab.id ? null : lab.id),
            },
            React.createElement(
              "div",
              { style: { display: "flex", justifyContent: "space-between", alignItems: "flex-start" } },
              React.createElement(
                "div",
                null,
                React.createElement(
                  "div",
                  { style: { display: "flex", gap: "8px", alignItems: "center", marginBottom: "4px" } },
                  React.createElement("strong", null, lab.testName),
                  React.createElement(Badge, {
                    variant: STATUS_VARIANT[lab.status] ?? "outline",
                    children: lab.status,
                  })
                ),
                React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0" } },
                  `${lab.category} \u2022 ${lab.facilityName}`
                ),
                React.createElement("p", { style: { fontSize: "12px", color: "#9CA3AF", margin: "2px 0" } },
                  `Ordered by ${lab.orderedBy}`
                )
              ),
              React.createElement("span", { style: { fontSize: "12px", color: "#9CA3AF" } },
                expandedId === lab.id ? "\u25B2" : "\u25BC"
              )
            ),

            expandedId === lab.id
              ? React.createElement(
                  "div",
                  { style: { marginTop: "12px", padding: "12px", backgroundColor: "#F9FAFB", borderRadius: "8px" } },
                  lab.value
                    ? React.createElement("p", { style: { margin: "4px 0", fontSize: "14px" } },
                        `Value: ${lab.value} ${lab.unit ?? ""}`
                      )
                    : null,
                  lab.referenceRange
                    ? React.createElement("p", { style: { margin: "4px 0", fontSize: "14px", color: "#6B7280" } },
                        `Reference Range: ${lab.referenceRange}`
                      )
                    : null,
                  lab.interpretation
                    ? React.createElement("p", { style: { margin: "4px 0", fontSize: "14px" } },
                        `Interpretation: ${lab.interpretation}`
                      )
                    : null,
                  lab.collectedAt
                    ? React.createElement("p", { style: { margin: "4px 0", fontSize: "13px", color: "#9CA3AF" } },
                        `Collected: ${new Date(lab.collectedAt).toLocaleString()}`
                      )
                    : null,
                  lab.resultAt
                    ? React.createElement("p", { style: { margin: "4px 0", fontSize: "13px", color: "#9CA3AF" } },
                        `Result: ${new Date(lab.resultAt).toLocaleString()}`
                      )
                    : null
                )
              : null
          )
        )
      )
    )
  );
}
