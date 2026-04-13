/**
 * BillingScreen — Encounter charge capture and billing.
 */
import React, { useState } from "react";
import { View, Text, TextInput, FlatList, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchCharges, captureCharge } from "../../services/queueService";

export function BillingScreen() {
  const queryClient = useQueryClient();
  const { data: charges = [], isLoading } = useQuery({ queryKey: ["billing-charges"], queryFn: () => fetchCharges() });
  const [form, setForm] = useState({ encounterId: "", itemCode: "", description: "", quantity: "1", unitPrice: "0", total: "0" });

  const mutation = useMutation({
    mutationFn: () => captureCharge({ ...form, quantity: parseInt(form.quantity), unitPrice: parseFloat(form.unitPrice), total: parseFloat(form.total) }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["billing-charges"] }); setForm({ encounterId: "", itemCode: "", description: "", quantity: "1", unitPrice: "0", total: "0" }); Alert.alert("Saved", "Charge captured"); },
  });

  return (
    <Screen><Header title="Billing & Charges" />
      <View style={styles.container}>
        <Text style={styles.title}>Capture Charge</Text>
        <TextInput style={styles.input} placeholder="Encounter ID" value={form.encounterId} onChangeText={(v) => setForm({ ...form, encounterId: v })} />
        <TextInput style={styles.input} placeholder="Item code" value={form.itemCode} onChangeText={(v) => setForm({ ...form, itemCode: v })} />
        <TextInput style={styles.input} placeholder="Description" value={form.description} onChangeText={(v) => setForm({ ...form, description: v })} />
        <View style={styles.row}>
          <TextInput style={[styles.input, { flex: 1 }]} placeholder="Qty" value={form.quantity} onChangeText={(v) => setForm({ ...form, quantity: v })} keyboardType="numeric" />
          <TextInput style={[styles.input, { flex: 1 }]} placeholder="Unit price" value={form.unitPrice} onChangeText={(v) => setForm({ ...form, unitPrice: v, total: String(parseFloat(v || "0") * parseInt(form.quantity || "1")) })} keyboardType="numeric" />
          <TextInput style={[styles.input, { flex: 1 }]} placeholder="Total" value={form.total} editable={false} />
        </View>
        <Button title="Capture Charge" onPress={() => mutation.mutate()} disabled={!form.encounterId || mutation.isPending} />
        <Text style={[styles.title, { marginTop: 16 }]}>Recent Charges ({(charges as unknown[]).length})</Text>
        {isLoading ? <LoadingSpinner /> : <FlatList data={charges as Array<Record<string, unknown>>} keyExtractor={(item) => String(item.id)}
          scrollEnabled={false} renderItem={({ item }) => (
            <View style={styles.chargeRow}>
              <Text style={styles.chargeDesc}>{String(item.description ?? item.item_code)}</Text>
              <Text style={styles.chargeAmount}>${String(item.total ?? 0)}</Text>
            </View>
          )} />}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16, gap: 8 },
  title: { fontSize: 16, fontWeight: "700", color: "#111827" },
  input: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 10, fontSize: 14 },
  row: { flexDirection: "row", gap: 8 },
  chargeRow: { flexDirection: "row", justifyContent: "space-between", paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: "#F3F4F6" },
  chargeDesc: { fontSize: 13, color: "#374151" },
  chargeAmount: { fontSize: 13, fontWeight: "700", color: "#111827" },
});
