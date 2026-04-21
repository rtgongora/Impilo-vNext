/**
 * WalletSection — Health wallet balance and transactions.
 */
import React from "react";
import { View, Text, StyleSheet, FlatList } from "react-native";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { fetchWallet, fetchTransactions } from "../../services/walletService";
import { useAppStore } from "../../stores/appStore";

export function WalletSection() {
  const profile = useAppStore((s) => s.profile);
  const patientId = profile?.cpid ?? "";

  const { data: wallet, isLoading: wLoading } = useQuery({
    queryKey: ["wallet", patientId], queryFn: () => fetchWallet(patientId), enabled: !!patientId,
  });
  const { data: transactions = [], isLoading: tLoading } = useQuery({
    queryKey: ["wallet-txns", patientId], queryFn: () => fetchTransactions(patientId), enabled: !!patientId,
  });

  if (wLoading || tLoading) return <LoadingSpinner />;

  return (
    <View style={styles.container}>
      <View style={styles.balanceCard}>
        <Text style={styles.balanceLabel}>HEALTH WALLET</Text>
        <Text style={styles.balanceAmount}>{wallet?.currency ?? "ZWL"} {(wallet?.balance ?? 0).toFixed(2)}</Text>
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
              <Text style={[styles.txnAmount, { color: item.transactionType === "CREDIT" ? "#16A34A" : "#DC2626" }]}>
                {item.transactionType === "CREDIT" ? "+" : "-"}{item.currency} {Math.abs(item.amount).toFixed(2)}
              </Text>
            </View>
          )}
        />
      )}
    </View>
  );
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
