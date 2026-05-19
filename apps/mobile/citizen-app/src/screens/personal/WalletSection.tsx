/**
 * WalletSection — Health wallet balance and transactions.
 *
 * Talks to the canonical Mushe Wallet plane through experience-bff
 * (`GET /internal/v1/wallet/me` and `/me/transactions`). The wallet owner is
 * derived from the `x-actor-id` trust header injected by the mobile API client.
 *
 * Audit gap: `G-3` (closed in Stage 3.4B). The previous implementation called
 * the wellness-service proxy with a `patientId` query parameter and silently
 * displayed `balance = 0` when upstream failed. Both behaviours are gone:
 * - Routes resolve through Mushe Wallet.
 * - Upstream failures surface as a typed `ErrorState`; the citizen sees an
 *   honest "wallet unavailable" message instead of a fabricated zero balance.
 */
import React from "react";
import { View, Text, StyleSheet, FlatList } from "react-native";
import { ErrorState, LoadingSpinner } from "@impilo/mobile-design-system";
import { ApiError } from "@impilo/mobile-api-client";
import { useQuery } from "@tanstack/react-query";
import { fetchWallet, fetchTransactions } from "../../services/walletService";
import { useAppStore } from "../../stores/appStore";

export function WalletSection() {
  const profile = useAppStore((s) => s.profile);
  const enabled = !!profile;

  const walletQuery = useQuery({
    queryKey: ["wallet", "me"],
    queryFn: fetchWallet,
    enabled,
  });
  const txnQuery = useQuery({
    queryKey: ["wallet", "me", "transactions"],
    queryFn: fetchTransactions,
    enabled,
  });

  if (walletQuery.isLoading || txnQuery.isLoading) return <LoadingSpinner />;

  const failure = walletQuery.error ?? txnQuery.error;
  if (failure) {
    const { title, message } = describeError(failure);
    return (
      <ErrorState
        title={title}
        message={message}
        correlationId={failure instanceof ApiError ? failure.correlationId : undefined}
        onRetry={() => {
          walletQuery.refetch();
          txnQuery.refetch();
        }}
      />
    );
  }

  const wallet = walletQuery.data;
  const transactions = txnQuery.data ?? [];

  return (
    <View style={styles.container}>
      <View style={styles.balanceCard}>
        <Text style={styles.balanceLabel}>HEALTH WALLET</Text>
        <Text style={styles.balanceAmount}>
          {wallet?.currency ?? "USD"} {(wallet?.balance ?? 0).toFixed(2)}
        </Text>
        <Text style={styles.balanceStatus}>{wallet?.status ?? "ACTIVE"}</Text>
      </View>
      <Text style={styles.sectionTitle}>Transactions</Text>
      {transactions.length === 0 ? (
        <Text style={styles.empty}>No transactions yet</Text>
      ) : (
        <FlatList
          data={transactions}
          keyExtractor={(item) => item.id}
          scrollEnabled={false}
          renderItem={({ item }) => (
            <View style={styles.txnRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.txnDesc}>{item.description || item.transactionType}</Text>
                <Text style={styles.txnDate}>{new Date(item.createdAt).toLocaleDateString()}</Text>
              </View>
              <Text
                style={[
                  styles.txnAmount,
                  { color: item.transactionType === "CREDIT" ? "#16A34A" : "#DC2626" },
                ]}
              >
                {item.transactionType === "CREDIT" ? "+" : "-"}
                {item.currency} {Math.abs(item.amount).toFixed(2)}
              </Text>
            </View>
          )}
        />
      )}
    </View>
  );
}

/**
 * Maps a wallet/transactions error onto a user-facing `(title, message)` pair.
 *
 * The `WALLET_UPSTREAM_UNAVAILABLE` stable code is emitted by the BFF when the
 * Mushe Wallet service is unreachable and `impilo.wallet.allow-local-fallback`
 * is `false` (see `docs/doctrine/mushex-gateway-neutrality.md`). Branching on
 * the code lets us tell the citizen explicitly that the wallet service is
 * temporarily down instead of showing a generic error.
 */
function describeError(err: unknown): { title: string; message: string } {
  if (err instanceof ApiError && err.code === "WALLET_UPSTREAM_UNAVAILABLE") {
    return {
      title: "Wallet temporarily unavailable",
      message: "We can't reach the wallet service right now. Please try again in a few minutes.",
    };
  }
  if (err instanceof Error) {
    return { title: "Wallet unavailable", message: err.message };
  }
  return { title: "Wallet unavailable", message: String(err) };
}

const styles = StyleSheet.create({
  container: { gap: 16 },
  balanceCard: { backgroundColor: "#009739", borderRadius: 16, padding: 24, alignItems: "center", gap: 4 },
  balanceLabel: { color: "#A7F3D0", fontSize: 11, fontWeight: "700", letterSpacing: 2 },
  balanceAmount: { color: "#FFFFFF", fontSize: 32, fontWeight: "700" },
  balanceStatus: { color: "#6EE7B7", fontSize: 12 },
  sectionTitle: { fontSize: 16, fontWeight: "700", color: "#111827" },
  empty: { color: "#9CA3AF", fontSize: 14, textAlign: "center", paddingVertical: 20 },
  txnRow: { flexDirection: "row", alignItems: "center", paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: "#F3F4F6" },
  txnDesc: { fontSize: 14, fontWeight: "500", color: "#374151" },
  txnDate: { fontSize: 11, color: "#9CA3AF" },
  txnAmount: { fontSize: 14, fontWeight: "700" },
});
