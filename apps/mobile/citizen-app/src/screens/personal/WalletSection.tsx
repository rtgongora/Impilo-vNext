import React from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { fetchWallet, fetchTransactions } from "../../services/walletService";
import { useAppStore } from "../../stores/appStore";
import { USE_SEED_DATA, SEED_WALLET, SEED_WALLET_TRANSACTIONS } from "../../data/seedHealthRecords";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

export function WalletSection() {
  const profile   = useAppStore((s) => s.profile);
  const patientId = profile?.cpid ?? "";

  const { data: wallet, isLoading: wLoading } = useQuery({
    queryKey: ["wallet", patientId, USE_SEED_DATA],
    queryFn: async () => USE_SEED_DATA
      ? (await new Promise<void>((r) => setTimeout(r, 350)), SEED_WALLET)
      : fetchWallet(patientId),
    enabled: USE_SEED_DATA || !!patientId,
  });

  const { data: transactions = [], isLoading: tLoading } = useQuery({
    queryKey: ["wallet-txns", patientId, USE_SEED_DATA],
    queryFn: async () => USE_SEED_DATA
      ? (await new Promise<void>((r) => setTimeout(r, 350)), SEED_WALLET_TRANSACTIONS)
      : fetchTransactions(patientId),
    enabled: USE_SEED_DATA || !!patientId,
  });

  if (wLoading || tLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  const credits = transactions.filter((t) => t.transactionType === "CREDIT").reduce((sum, t) => sum + t.amount, 0);
  const debits  = transactions.filter((t) => t.transactionType === "DEBIT").reduce((sum, t) => sum + t.amount, 0);

  return (
    <ScrollView style={s.root} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>
      {/* Wallet hero card */}
      <View style={s.walletCard}>
        <View style={s.walletDecor1} />
        <View style={s.walletDecor2} />
        <View style={s.walletTop}>
          <View style={s.walletIconWrap}>
            <Ionicons name="wallet" size={22} color={APP_GREEN} />
          </View>
          <View style={[s.walletStatus, { backgroundColor: wallet?.status === "ACTIVE" ? "rgba(52,211,153,0.2)" : "rgba(255,255,255,0.15)" }]}>
            <View style={[s.walletStatusDot, { backgroundColor: wallet?.status === "ACTIVE" ? "#34D399" : "#9CA3AF" }]} />
            <Text style={s.walletStatusText}>{wallet?.status ?? "ACTIVE"}</Text>
          </View>
        </View>
        <Text style={s.walletLabel}>HEALTH WALLET BALANCE</Text>
        <Text style={s.walletAmount}>
          {wallet?.currency ?? "ZWL"} {(wallet?.balance ?? 0).toLocaleString("en-ZW", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </Text>
        <View style={s.walletStats}>
          <View style={s.walletStat}>
            <Text style={[s.walletStatVal, { color: "#6EE7B7" }]}>+{credits.toFixed(2)}</Text>
            <Text style={s.walletStatLbl}>Top-ups</Text>
          </View>
          <View style={s.walletStatDivider} />
          <View style={s.walletStat}>
            <Text style={s.walletStatVal}>-{debits.toFixed(2)}</Text>
            <Text style={s.walletStatLbl}>Spent</Text>
          </View>
        </View>
      </View>

      {/* Quick actions */}
      <View style={s.actionsRow}>
        {[
          { label: "Top Up",    icon: "add-circle-outline" as const, color: APP_GREEN      },
          { label: "Pay",       icon: "send-outline"       as const, color: APP_GREEN_DARK },
          { label: "History",   icon: "time-outline"       as const, color: APP_TEXT_2     },
        ].map((a) => (
          <View key={a.label} style={s.actionBtn}>
            <View style={[s.actionIcon, { backgroundColor: a.color + "18" }]}>
              <Ionicons name={a.icon} size={22} color={a.color} />
            </View>
            <Text style={s.actionLabel}>{a.label}</Text>
          </View>
        ))}
      </View>

      {/* Transactions */}
      <Text style={s.sectionLabel}>Recent Transactions</Text>
      {transactions.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="receipt-outline" size={40} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyText}>No transactions yet</Text>
        </View>
      ) : (
        <View style={s.txnCard}>
          {transactions.map((txn, idx) => {
            const isCredit = txn.transactionType === "CREDIT";
            return (
              <View key={txn.id} style={[s.txnRow, idx < transactions.length - 1 && s.txnDivider]}>
                <View style={[s.txnIcon, { backgroundColor: isCredit ? APP_GREEN_XLIGHT : "#FFF9F9" }]}>
                  <Ionicons
                    name={isCredit ? "arrow-down-circle-outline" : "arrow-up-circle-outline"}
                    size={20}
                    color={isCredit ? APP_GREEN_DARK : APP_TEXT_2}
                  />
                </View>
                <View style={s.txnInfo}>
                  <Text style={s.txnDesc}>{txn.description ?? txn.transactionType}</Text>
                  {txn.reference ? <Text style={s.txnRef}>{txn.reference}</Text> : null}
                  <Text style={s.txnDate}>{new Date(txn.createdAt).toLocaleDateString([], { day: "numeric", month: "short", year: "numeric" })}</Text>
                </View>
                <View style={s.txnRight}>
                  <Text style={[s.txnAmount, { color: isCredit ? APP_GREEN_DARK : APP_TEXT }]}>
                    {isCredit ? "+" : "-"}{txn.currency} {txn.amount.toFixed(2)}
                  </Text>
                  <View style={[s.txnStatusPill, { backgroundColor: txn.status === "COMPLETED" ? APP_GREEN_XLIGHT : "#FFF9F9" }]}>
                    <Text style={[s.txnStatusText, { color: txn.status === "COMPLETED" ? APP_GREEN_DARK : APP_TEXT_3 }]}>
                      {txn.status}
                    </Text>
                  </View>
                </View>
              </View>
            );
          })}
        </View>
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 14, paddingBottom: 40 },
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },

  walletCard: {
    backgroundColor: APP_GREEN, borderRadius: 24, padding: 22, overflow: "hidden",
    shadowColor: APP_GREEN_DARK, shadowOffset: { width: 0, height: 8 }, shadowOpacity: 0.3, shadowRadius: 16, elevation: 10,
  },
  walletDecor1: { position: "absolute", width: 180, height: 180, borderRadius: 90, borderWidth: 28, borderColor: "rgba(255,255,255,0.07)", top: -60, right: -50 },
  walletDecor2: { position: "absolute", width: 100, height: 100, borderRadius: 50, borderWidth: 18, borderColor: "rgba(255,255,255,0.05)", bottom: -30, left: -20 },
  walletTop: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 16 },
  walletIconWrap: { width: 44, height: 44, borderRadius: 14, backgroundColor: "rgba(255,255,255,0.9)", alignItems: "center", justifyContent: "center" },
  walletStatus: { flexDirection: "row", alignItems: "center", gap: 5, paddingHorizontal: 10, paddingVertical: 5, borderRadius: 20 },
  walletStatusDot: { width: 6, height: 6, borderRadius: 3 },
  walletStatusText: { fontSize: 11, fontWeight: "700", color: "#FFFFFF" },
  walletLabel: { fontSize: 10, fontWeight: "700", color: "rgba(255,255,255,0.65)", letterSpacing: 1.5, textTransform: "uppercase", marginBottom: 4 },
  walletAmount: { fontSize: 34, fontWeight: "900", color: "#FFFFFF", marginBottom: 16 },
  walletStats: { flexDirection: "row", backgroundColor: "rgba(255,255,255,0.12)", borderRadius: 14, padding: 12 },
  walletStat: { flex: 1, alignItems: "center" },
  walletStatVal: { fontSize: 14, fontWeight: "700", color: "#FFFFFF" },
  walletStatLbl: { fontSize: 10, color: "rgba(255,255,255,0.6)", marginTop: 2 },
  walletStatDivider: { width: 1, backgroundColor: "rgba(255,255,255,0.2)" },

  actionsRow: { flexDirection: "row", gap: 10 },
  actionBtn: { flex: 1, backgroundColor: APP_SURFACE, borderRadius: 16, padding: 14, alignItems: "center", gap: 8, shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 4, elevation: 2 },
  actionIcon: { width: 46, height: 46, borderRadius: 14, alignItems: "center", justifyContent: "center" },
  actionLabel: { fontSize: 12, fontWeight: "600", color: APP_TEXT },

  sectionLabel: { fontSize: 12, fontWeight: "700", color: APP_TEXT_2, textTransform: "uppercase", letterSpacing: 0.8, paddingHorizontal: 4 },
  txnCard: { backgroundColor: APP_SURFACE, borderRadius: 18, overflow: "hidden", shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.05, shadowRadius: 8, elevation: 3 },
  txnRow: { flexDirection: "row", alignItems: "center", gap: 12, paddingHorizontal: 16, paddingVertical: 14 },
  txnDivider: { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER },
  txnIcon: { width: 40, height: 40, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  txnInfo: { flex: 1 },
  txnDesc: { fontSize: 13, fontWeight: "600", color: APP_TEXT },
  txnRef: { fontSize: 10, color: APP_TEXT_3, fontFamily: "monospace", marginTop: 1 },
  txnDate: { fontSize: 11, color: APP_TEXT_3, marginTop: 2 },
  txnRight: { alignItems: "flex-end", gap: 4 },
  txnAmount: { fontSize: 14, fontWeight: "800" },
  txnStatusPill: { paddingHorizontal: 7, paddingVertical: 2, borderRadius: 8 },
  txnStatusText: { fontSize: 10, fontWeight: "600" },

  empty: { alignItems: "center", paddingVertical: 32, gap: 8 },
  emptyText: { fontSize: 13, color: APP_TEXT_3 },
});
