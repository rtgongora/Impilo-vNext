/**
 * CoverageSection — View insurance/coverage information.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Card,
  CardHeader,
  CardBody,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { fetchCoverage } from "../../services/coverageService";
import type { CoverageInfo } from "../../types";

export function CoverageSection() {
  const [coverage, setCoverage] = useState<CoverageInfo[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchCoverage();
      setCoverage(result);
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

  if (coverage.length === 0) {
    return React.createElement(EmptyState, {
      title: "No coverage information",
      message: "Your insurance and coverage details will appear here when available",
    });
  }

  return React.createElement(
    "div",
    { "data-testid": "coverage-section", style: { display: "flex", flexDirection: "column", gap: "12px" } },
    React.createElement("h3", { style: { margin: 0 } }, "Coverage & Insurance"),
    coverage.map((plan) =>
      React.createElement(
        Card,
        { key: plan.id },
        React.createElement(CardHeader, { title: plan.planName }),
        React.createElement(
          CardBody,
          null,
          React.createElement(
            "div",
            { "data-testid": `coverage-${plan.id}` },
            React.createElement(
              "div",
              { style: { display: "flex", gap: "8px", alignItems: "center", marginBottom: "8px" } },
              React.createElement(Badge, {
                variant: plan.status === "ACTIVE" ? "default" : "outline",
                children: plan.status,
              }),
              React.createElement("span", { style: { fontSize: "13px", color: "#6B7280" } }, plan.planType)
            ),
            React.createElement("p", { style: { fontSize: "14px", margin: "4px 0" } }, `Member ID: ${plan.memberId}`),
            React.createElement("p", { style: { fontSize: "14px", margin: "4px 0" } },
              `Effective: ${new Date(plan.effectiveFrom).toLocaleDateString()}${plan.effectiveTo ? ` - ${new Date(plan.effectiveTo).toLocaleDateString()}` : " (ongoing)"}`
            ),
            plan.copay !== undefined
              ? React.createElement("p", { style: { fontSize: "14px", margin: "4px 0" } }, `Copay: ${plan.currency} ${plan.copay}`)
              : null,
            plan.deductible !== undefined
              ? React.createElement("p", { style: { fontSize: "14px", margin: "4px 0" } }, `Deductible: ${plan.currency} ${plan.deductible}`)
              : null,
            plan.outOfPocketMax !== undefined
              ? React.createElement("p", { style: { fontSize: "14px", margin: "4px 0" } }, `Out-of-pocket max: ${plan.currency} ${plan.outOfPocketMax}`)
              : null
          )
        )
      )
    )
  );
}
