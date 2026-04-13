/**
 * FinanceOverviewScreen — Revenue summary, pending claims, and recent payments.
 *
 * Provides a financial dashboard for the provider with:
 * - Revenue summary card (today, week, month)
 * - Pending claims count and total value
 * - Recent payments list with status badges
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
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
  fetchRevenueSummary,
  fetchPendingClaims,
  fetchRecentPayments,
} from "../../services/financeService";
import type { RevenueSummary, Claim, Payment } from "../../types";

const PAYMENT_BADGE_VARIANT: Record<string, "primary" | "secondary" | "destructive"> = {
  PAID: "primary",
  PENDING: "secondary",
  REJECTED: "destructive",
};

function formatCurrency(amount: number, currency: string): string {
  return `${currency} ${amount.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

export function FinanceOverviewScreen() {
  const [revenue, setRevenue] = useState<RevenueSummary | null>(null);
  const [claims, setClaims] = useState<Claim[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [rev, cl, pay] = await Promise.all([
        fetchRevenueSummary(),
        fetchPendingClaims(),
        fetchRecentPayments(),
      ]);
      setRevenue(rev);
      setClaims(cl);
      setPayments(pay);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load finance data"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const pendingTotal = claims.reduce((sum, c) => sum + c.amount, 0);

  return (
    <Screen>
      <Header title="Finance Overview" />
      <View testID="finance-overview-screen" style={styles.container}>
        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={load} />
        ) : (
          <ScrollView
            style={styles.scrollArea}
            contentContainerStyle={styles.scrollContent}
          >
            {/* ── Revenue Summary ────────────────────────────────── */}
            {revenue && (
              <Card>
                <CardHeader title="Revenue Summary" />
                <CardBody>
                  <View style={styles.revenueGrid}>
                    <View style={styles.revenueItem}>
                      <Text style={[styles.revenueValue, { color: "#22C55E" }]}>
                        {formatCurrency(revenue.todayRevenue, revenue.currency)}
                      </Text>
                      <Text style={styles.revenueLabel}>Today</Text>
                    </View>
                    <View style={styles.revenueItem}>
                      <Text style={[styles.revenueValue, { color: "#3B82F6" }]}>
                        {formatCurrency(revenue.weekTotal, revenue.currency)}
                      </Text>
                      <Text style={styles.revenueLabel}>This Week</Text>
                    </View>
                    <View style={styles.revenueItem}>
                      <Text style={[styles.revenueValue, { color: "#8B5CF6" }]}>
                        {formatCurrency(revenue.monthTotal, revenue.currency)}
                      </Text>
                      <Text style={styles.revenueLabel}>This Month</Text>
                    </View>
                  </View>
                </CardBody>
              </Card>
            )}

            {/* ── Pending Claims ─────────────────────────────────── */}
            <Card>
              <CardHeader title="Pending Claims" />
              <CardBody>
                <View style={styles.claimsSummary}>
                  <View style={styles.claimsStat}>
                    <Text style={styles.claimsCount}>{claims.length}</Text>
                    <Text style={styles.claimsLabel}>Claims Pending</Text>
                  </View>
                  <View style={styles.claimsStat}>
                    <Text style={styles.claimsTotal}>
                      {formatCurrency(
                        pendingTotal,
                        revenue?.currency ?? "ZAR"
                      )}
                    </Text>
                    <Text style={styles.claimsLabel}>Total Value</Text>
                  </View>
                </View>
              </CardBody>
            </Card>

            {/* ── Recent Payments ────────────────────────────────── */}
            <Text style={styles.sectionTitle}>Recent Payments</Text>
            {payments.length === 0 ? (
              <EmptyState
                title="No payments"
                message="No recent payment records"
              />
            ) : (
              payments.map((payment) => (
                <Card key={payment.id}>
                  <CardBody>
                    <View
                      testID={`payment-${payment.id}`}
                      style={styles.paymentRow}
                    >
                      <View style={styles.paymentInfo}>
                        <Text style={styles.paymentPatient}>
                          {payment.patientName}
                        </Text>
                        <Text style={styles.paymentDate}>
                          {new Date(payment.paidAt).toLocaleDateString()}
                        </Text>
                        {payment.reference && (
                          <Text style={styles.paymentRef}>
                            Ref: {payment.reference}
                          </Text>
                        )}
                      </View>
                      <View style={styles.paymentRight}>
                        <Text style={styles.paymentAmount}>
                          {formatCurrency(
                            payment.amount,
                            payment.currency
                          )}
                        </Text>
                        <Badge
                          variant={
                            PAYMENT_BADGE_VARIANT[payment.status] ?? "secondary"
                          }
                        >
                          {payment.status}
                        </Badge>
                      </View>
                    </View>
                  </CardBody>
                </Card>
              ))
            )}
          </ScrollView>
        )}

        <View style={styles.refreshContainer}>
          <Button
            title="Refresh"
            variant="outline"
            onPress={load}
            testID="refresh-finance"
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
  scrollArea: {
    flex: 1,
  },
  scrollContent: {
    gap: 12,
    paddingBottom: 16,
  },
  revenueGrid: {
    flexDirection: "row",
    justifyContent: "space-between",
    gap: 8,
  },
  revenueItem: {
    flex: 1,
    alignItems: "center",
    paddingVertical: 8,
  },
  revenueValue: {
    fontSize: 18,
    fontWeight: "900",
  },
  revenueLabel: {
    fontSize: 12,
    color: "#6B7280",
    marginTop: 4,
  },
  claimsSummary: {
    flexDirection: "row",
    justifyContent: "space-around",
    paddingVertical: 8,
  },
  claimsStat: {
    alignItems: "center",
  },
  claimsCount: {
    fontSize: 32,
    fontWeight: "900",
    color: "#F59E0B",
  },
  claimsTotal: {
    fontSize: 18,
    fontWeight: "800",
    color: "#F59E0B",
  },
  claimsLabel: {
    fontSize: 12,
    color: "#6B7280",
    marginTop: 4,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
    marginTop: 8,
  },
  paymentRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  paymentInfo: {
    flex: 1,
    gap: 2,
  },
  paymentPatient: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
  },
  paymentDate: {
    fontSize: 12,
    color: "#6B7280",
  },
  paymentRef: {
    fontSize: 11,
    color: "#9CA3AF",
  },
  paymentRight: {
    alignItems: "flex-end",
    gap: 4,
  },
  paymentAmount: {
    fontSize: 15,
    fontWeight: "800",
    color: "#111827",
  },
  refreshContainer: {
    marginTop: 8,
  },
});
