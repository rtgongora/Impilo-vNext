import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { fetchBalance, fetchPendingCharges, fetchTransactions } from "../../services/financeService";
import type { Balance, PendingCharge, Transaction } from "../../types";
import {
  USE_SEED_DATA, SEED_BALANCE, SEED_PENDING_CHARGES, SEED_TRANSACTIONS,
} from "../../data/seedHealthRecords";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const CATEGORY_CFG: Record<string, { icon: React.ComponentProps<typeof Ionicons>["name"]; color: string }> = {
  Consultation: { icon: "person-circle-outline", color: APP_GREEN      },
  Laboratory:   { icon: "flask-outline",          color: APP_GOLD       },
  Insurance:    { icon: "shield-outline",          color: APP_GREEN_DARK },
  Admission:    { icon: "bed-outline",             color: APP_RED        },
  Imaging:      { icon: "scan-outline",            color: APP_GOLD       },
};

export function FinanceSection() {
  const [balance, setBalance]           = useState<Balance | null>(null);
  const [charges, setCharges]           = useState<PendingCharge[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setBalance(SEED_BALANCE);
        setCharges(SEED_PENDING_CHARGES);
        setTransactions(SEED_TRANSACTIONS);
      } else {
        const [bal, ch, txns] = await Promise.all([fetchBalance(), fetchPendingCharges(), fetchTransactions({ size: 50 })]);
        setBalance(bal);
        setCharges(ch);
        setTransactions(txns);
      }
    } catch {
      // silent
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  const totalPending = charges.reduce((sum, c) => sum + c.amount, 0);
  const totalPaid    = transactions.filter((t) => t.type === "DEBIT").reduce((sum, t) => sum + t.amount, 0);
  const totalRecvd   = transactions.filter((t) => t.type === "CREDIT").reduce((sum, t) => sum + t.amount, 0);

  return (
    <ScrollView
      style={s.root}
      contentContainerStyle={s.content}
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
    >
      {/* Balance hero */}
      <View style={s.balanceCard}>
        <View style={s.balanceDecor1} />
        <View style={s.balanceDecor2} />
        <Text style={s.balanceLabel}>ACCOUNT BALANCE</Text>
        <Text style={s.balanceAmount}>
          {balance?.currency ?? "ZWL"} {(balance?.amount ?? 0).toLocaleString("en-ZW", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </Text>
        <Text style={s.balanceUpdated}>
          Updated {balance ? new Date(balance.updatedAt).toLocaleDateString() : "—"}
        </Text>
        {/* Mini stats */}
        <View style={s.balanceStats}>
          <View style={s.balanceStat}>
            <Text style={s.balanceStatVal}>-{totalPaid.toFixed(2)}</Text>
            <Text style={s.balanceStatLbl}>Paid</Text>
          </View>
          <View style={s.balanceStatDivider} />
          <View style={s.balanceStat}>
            <Text style={[s.balanceStatVal, { color: "#6EE7B7" }]}>+{totalRecvd.toFixed(2)}</Text>
            <Text style={s.balanceStatLbl}>Received</Text>
          </View>
          <View style={s.balanceStatDivider} />
          <View style={s.balanceStat}>
            <Text style={[s.balanceStatVal, { color: "#FCD34D" }]}>{totalPending.toFixed(2)}</Text>
            <Text style={s.balanceStatLbl}>Pending</Text>
          </View>
        </View>
      </View>

      {/* Pending charges */}
      {charges.length > 0 ? (
        <>
          <Text style={s.sectionLabel}>Pending Charges</Text>
          <View style={s.listCard}>
            {charges.map((c, idx) => (
              <View key={c.id} style={[s.chargeRow, idx < charges.length - 1 && s.rowDivider]}>
                <View style={[s.chargeIcon, { backgroundColor: APP_GOLD_LIGHT }]}>
                  <Ionicons name="receipt-outline" size={16} color={APP_GOLD} />
                </View>
                <View style={s.chargeInfo}>
                  <Text style={s.chargeDesc}>{c.description}</Text>
                  {c.facilityName ? <Text style={s.chargeFacility}>{c.facilityName}</Text> : null}
                  <Text style={s.chargeDate}>{new Date(c.chargeDate).toLocaleDateString()}</Text>
                </View>
                <Text style={s.chargeAmount}>{c.currency} {c.amount.toFixed(2)}</Text>
              </View>
            ))}
          </View>
        </>
      ) : null}

      {/* Transaction history */}
      <Text style={s.sectionLabel}>Transaction History</Text>
      {transactions.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="receipt-outline" size={40} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyText}>No transactions yet</Text>
        </View>
      ) : (
        <View style={s.listCard}>
          {transactions.map((txn, idx) => {
            const cfg    = CATEGORY_CFG[txn.category ?? ""] ?? { icon: "cash-outline" as const, color: APP_TEXT_2 };
            const isCredit = txn.type === "CREDIT";
            return (
              <View key={txn.id} style={[s.txnRow, idx < transactions.length - 1 && s.rowDivider]}>
                <View style={[s.txnIcon, { backgroundColor: isCredit ? APP_GREEN_XLIGHT : "#FFF9F9" }]}>
                  <Ionicons name={cfg.icon} size={16} color={isCredit ? APP_GREEN : APP_TEXT_2} />
                </View>
                <View style={s.txnInfo}>
                  <Text style={s.txnDesc}>{txn.description}</Text>
                  <Text style={s.txnDate}>{new Date(txn.date).toLocaleDateString()}</Text>
                </View>
                <Text style={[s.txnAmount, { color: isCredit ? APP_GREEN_DARK : APP_TEXT }]}>
                  {isCredit ? "+" : "-"}{txn.currency} {txn.amount.toFixed(2)}
                </Text>
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

  balanceCard: {
    backgroundColor: APP_GREEN, borderRadius: 22, padding: 22, overflow: "hidden",
    shadowColor: APP_GREEN_DARK, shadowOffset: { width: 0, height: 8 }, shadowOpacity: 0.3, shadowRadius: 16, elevation: 10,
  },
  balanceDecor1: { position: "absolute", width: 180, height: 180, borderRadius: 90, borderWidth: 28, borderColor: "rgba(255,255,255,0.07)", top: -60, right: -50 },
  balanceDecor2: { position: "absolute", width: 100, height: 100, borderRadius: 50, borderWidth: 18, borderColor: "rgba(255,255,255,0.05)", bottom: -30, left: -20 },
  balanceLabel: { fontSize: 10, fontWeight: "700", color: "rgba(255,255,255,0.65)", letterSpacing: 1.5, textTransform: "uppercase", marginBottom: 6 },
  balanceAmount: { fontSize: 34, fontWeight: "900", color: "#FFFFFF" },
  balanceUpdated: { fontSize: 11, color: "rgba(255,255,255,0.55)", marginTop: 4, marginBottom: 18 },
  balanceStats: {
    flexDirection: "row", backgroundColor: "rgba(255,255,255,0.12)",
    borderRadius: 14, padding: 12,
  },
  balanceStat: { flex: 1, alignItems: "center" },
  balanceStatVal: { fontSize: 14, fontWeight: "700", color: "#FFFFFF" },
  balanceStatLbl: { fontSize: 10, color: "rgba(255,255,255,0.6)", marginTop: 2 },
  balanceStatDivider: { width: 1, backgroundColor: "rgba(255,255,255,0.2)" },

  sectionLabel: { fontSize: 12, fontWeight: "700", color: APP_TEXT_2, textTransform: "uppercase", letterSpacing: 0.8, paddingHorizontal: 4 },
  listCard: {
    backgroundColor: APP_SURFACE, borderRadius: 18, overflow: "hidden",
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.05, shadowRadius: 8, elevation: 3,
  },
  chargeRow: { flexDirection: "row", alignItems: "center", gap: 12, paddingHorizontal: 16, paddingVertical: 14 },
  chargeIcon: { width: 38, height: 38, borderRadius: 11, alignItems: "center", justifyContent: "center" },
  chargeInfo: { flex: 1 },
  chargeDesc: { fontSize: 13, fontWeight: "600", color: APP_TEXT },
  chargeFacility: { fontSize: 11, color: APP_TEXT_2, marginTop: 1 },
  chargeDate: { fontSize: 11, color: APP_TEXT_3 },
  chargeAmount: { fontSize: 13, fontWeight: "700", color: APP_GOLD },
  txnRow: { flexDirection: "row", alignItems: "center", gap: 12, paddingHorizontal: 16, paddingVertical: 12 },
  txnIcon: { width: 36, height: 36, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  txnInfo: { flex: 1 },
  txnDesc: { fontSize: 13, fontWeight: "500", color: APP_TEXT },
  txnDate: { fontSize: 11, color: APP_TEXT_3, marginTop: 1 },
  txnAmount: { fontSize: 13, fontWeight: "700" },
  rowDivider: { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER },

  empty: { alignItems: "center", paddingVertical: 32, gap: 8 },
  emptyText: { fontSize: 13, color: APP_TEXT_3 },
});
