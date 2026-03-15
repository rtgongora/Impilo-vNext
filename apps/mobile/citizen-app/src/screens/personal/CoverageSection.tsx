/**
 * CoverageSection — View insurance/coverage information.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet } from "react-native";
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

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  if (coverage.length === 0) {
    return (
      <EmptyState
        title="No coverage information"
        message="Your insurance and coverage details will appear here when available"
      />
    );
  }

  return (
    <View testID="coverage-section" style={styles.container}>
      <Text style={styles.heading}>Coverage & Insurance</Text>
      {coverage.map((plan) => (
        <Card key={plan.id}>
          <CardHeader title={plan.planName} />
          <CardBody>
            <View testID={`coverage-${plan.id}`}>
              <View style={styles.badgeRow}>
                <Badge variant={plan.status === "ACTIVE" ? "default" : "outline"}>
                  {plan.status}
                </Badge>
                <Text style={styles.planType}>{plan.planType}</Text>
              </View>
              <Text style={styles.infoText}>{`Member ID: ${plan.memberId}`}</Text>
              <Text style={styles.infoText}>
                {`Effective: ${new Date(plan.effectiveFrom).toLocaleDateString()}${plan.effectiveTo ? ` - ${new Date(plan.effectiveTo).toLocaleDateString()}` : " (ongoing)"}`}
              </Text>
              {plan.copay !== undefined ? (
                <Text style={styles.infoText}>{`Copay: ${plan.currency} ${plan.copay}`}</Text>
              ) : null}
              {plan.deductible !== undefined ? (
                <Text style={styles.infoText}>{`Deductible: ${plan.currency} ${plan.deductible}`}</Text>
              ) : null}
              {plan.outOfPocketMax !== undefined ? (
                <Text style={styles.infoText}>{`Out-of-pocket max: ${plan.currency} ${plan.outOfPocketMax}`}</Text>
              ) : null}
            </View>
          </CardBody>
        </Card>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
  },
  heading: {
    fontSize: 18,
    fontWeight: "600",
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginBottom: 8,
  },
  planType: {
    fontSize: 13,
    color: "#6B7280",
  },
  infoText: {
    fontSize: 14,
    marginVertical: 4,
  },
});
