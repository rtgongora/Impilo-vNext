/**
 * FinanceSection — Balance, pending charges, and transaction history.
 *
 * Audit gap: `G-3` (closed in Stage 3.4B). Balance and transactions now come
 * from the canonical Mushe Wallet plane via `financeService` (no more dead
 * `/internal/v1/mobile/citizen/finance/**` calls). Pending charges remain a
 * read-only "no pending charges" surface until a COSTA-backed route is wired
 * in a future stage.
 *
 * Doctrine: `docs/doctrine/mushex-gateway-neutrality.md`.
 */
import React, { useState, useEffect, useCallback } from "react";
import { View, Text, RefreshControl, StyleSheet, FlatList, ScrollView } from "react-native";
import {
  Card,
  CardBody,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { ApiError } from "@impilo/mobile-api-client";
import {
  fetchBalance,
  fetchPendingCharges,
  fetchTransactions,
} from "../../services/financeService";
import type { Transaction, Balance, PendingCharge } from "../../types";

export function FinanceSection() {
  const [balance, setBalance] = useState<Balance | null>(null);
  const [charges, setCharges] = useState<PendingCharge[]>([]);
  const [chargesBlocked, setChargesBlocked] = useState(false);
  const [chargesBlockedReason, setChargesBlockedReason] = useState<string | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [bal, pending, txns] = await Promise.all([
        fetchBalance(),
        fetchPendingCharges(),
        fetchTransactions({ size: 50 }),
      ]);
      setBalance(bal);
      setCharges(pending.charges);
      setChargesBlocked(pending.blocked);
      setChargesBlockedReason(pending.blockedReason ?? null);
      setTransactions(txns);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleRefresh = useCallback(() => {
    setRefreshing(true);
    load();
  }, [load]);

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) {
    const { title, message } = describeError(error);
    return (
      <ErrorState
        title={title}
        message={message}
        correlationId={error instanceof ApiError ? error.correlationId : undefined}
        onRetry={load}
      />
    );
  }

  return (
    <ScrollView
      testID="finance-section"
      style={styles.scrollView}
      contentContainerStyle={styles.container}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
      }
    >
      {/* Balance card */}
      <View style={styles.balanceCard}>
        <Text style={styles.balanceLabel}>ACCOUNT BALANCE</Text>
        <Text style={styles.balanceAmount}>
          {balance?.currency ?? "ZAR"} {(balance?.amount ?? 0).toFixed(2)}
        </Text>
        <Text style={styles.balanceUpdated}>
          Updated {balance?.updatedAt ? new Date(balance.updatedAt).toLocaleDateString() : "—"}
        </Text>
      </View>

      {/* Pending charges */}
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Pending Charges</Text>
        {charges.length > 0 ? (
          <Badge variant="destructive">{String(charges.length)}</Badge>
        ) : null}
      </View>
      {chargesBlocked ? (
        <Card>
          <CardBody>
            <View testID="costa-blocked-state" style={styles.blockedCard}>
              <Badge variant="warning">Costa unavailable</Badge>
              <Text style={styles.blockedTitle}>Pending charges not yet on mobile</Text>
              <Text style={styles.blockedMessage}>
                {chargesBlockedReason ??
                  "Citizen billing quotes and pending charges require a COSTA BFF route that is not yet published."}
              </Text>
            </View>
          </CardBody>
        </Card>
      ) : charges.length === 0 ? (
        <Text style={styles.emptyText}>No pending charges</Text>
      ) : (
        charges.map((charge) => (
          <Card key={charge.id}>
            <CardBody>
              <View testID={`charge-${charge.id}`} style={styles.chargeRow}>
                <View style={styles.chargeInfo}>
                  <Text style={styles.chargeDesc}>{charge.description}</Text>
                  {charge.facilityName ? (
                    <Text style={styles.chargeFacility}>{charge.facilityName}</Text>
                  ) : null}
                  <Text style={styles.chargeDate}>
                    {new Date(charge.chargeDate).toLocaleDateString()}
                  </Text>
                </View>
                <Text style={styles.chargeAmount}>
                  {charge.currency} {charge.amount.toFixed(2)}
                </Text>
              </View>
            </CardBody>
          </Card>
        ))
      )}

      {/* Transaction list */}
      <Text style={styles.sectionTitle}>Transaction History</Text>
      {transactions.length === 0 ? (
        <EmptyState
          title="No transactions"
          message="Your transaction history will appear here"
        />
      ) : (
        transactions.map((txn) => (
          <Card key={txn.id}>
            <CardBody>
              <View testID={`txn-${txn.id}`} style={styles.txnRow}>
                <View style={styles.txnInfo}>
                  <View style={styles.txnHeaderRow}>
                    <Text style={styles.txnDesc}>{txn.description}</Text>
                    <Badge
                      variant={txn.status === "COMPLETED" ? "default" : "outline"}
                    >
                      {txn.status}
                    </Badge>
                  </View>
                  <Text style={styles.txnDate}>
                    {new Date(txn.date).toLocaleDateString()}
                  </Text>
                  {txn.reference ? (
                    <Text style={styles.txnRef}>Ref: {txn.reference}</Text>
                  ) : null}
                </View>
                <Text
                  style={[
                    styles.txnAmount,
                    { color: txn.type === "CREDIT" ? "#16A34A" : "#DC2626" },
                  ]}
                >
                  {txn.type === "CREDIT" ? "+" : "-"}
                  {txn.currency} {Math.abs(txn.amount).toFixed(2)}
                </Text>
              </View>
            </CardBody>
          </Card>
        ))
      )}
    </ScrollView>
  );
}

/**
 * Maps a finance/wallet error onto a user-facing `(title, message)` pair.
 * Branches on the BFF's stable `WALLET_UPSTREAM_UNAVAILABLE` code so the
 * citizen sees an explicit "wallet unavailable" message rather than a
 * generic failure (see `docs/doctrine/mushex-gateway-neutrality.md`).
 */
function describeError(err: unknown): { title: string; message: string } {
  if (err instanceof ApiError && err.code === "WALLET_UPSTREAM_UNAVAILABLE") {
    return {
      title: "Wallet temporarily unavailable",
      message: "We can't reach the wallet service right now. Please try again in a few minutes.",
    };
  }
  if (err instanceof Error) {
    return { title: "Error", message: err.message };
  }
  return { title: "Error", message: String(err) };
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    gap: 12,
  },
  balanceCard: {
    backgroundColor: "#231F20",
    borderRadius: 16,
    padding: 24,
    alignItems: "center",
    gap: 4,
  },
  balanceLabel: {
    color: "#93C5FD",
    fontSize: 11,
    fontWeight: "700",
    letterSpacing: 2,
  },
  balanceAmount: {
    color: "#FFFFFF",
    fontSize: 32,
    fontWeight: "700",
  },
  balanceUpdated: {
    color: "#BFDBFE",
    fontSize: 12,
  },
  sectionHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
  },
  emptyText: {
    color: "#9CA3AF",
    fontSize: 14,
    textAlign: "center",
    paddingVertical: 12,
  },
  chargeRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  chargeInfo: {
    flex: 1,
  },
  chargeDesc: {
    fontSize: 14,
    fontWeight: "500",
    color: "#374151",
  },
  chargeFacility: {
    fontSize: 13,
    color: "#6B7280",
    marginTop: 2,
  },
  chargeDate: {
    fontSize: 11,
    color: "#9CA3AF",
    marginTop: 2,
  },
  chargeAmount: {
    fontSize: 14,
    fontWeight: "700",
    color: "#DC2626",
  },
  blockedCard: {
    gap: 8,
  },
  blockedTitle: {
    fontSize: 15,
    fontWeight: "600",
    color: "#111827",
  },
  blockedMessage: {
    fontSize: 13,
    color: "#6B7280",
    lineHeight: 18,
  },
  txnRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  txnInfo: {
    flex: 1,
    marginRight: 12,
  },
  txnHeaderRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginBottom: 4,
  },
  txnDesc: {
    fontSize: 14,
    fontWeight: "500",
    color: "#374151",
    flex: 1,
  },
  txnDate: {
    fontSize: 11,
    color: "#9CA3AF",
  },
  txnRef: {
    fontSize: 11,
    color: "#6B7280",
    marginTop: 2,
  },
  txnAmount: {
    fontSize: 14,
    fontWeight: "700",
  },
});
