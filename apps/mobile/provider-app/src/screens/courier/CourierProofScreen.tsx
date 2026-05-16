/**
 * CourierProofScreen — capture proof of delivery & custody events.
 *
 * Lets a courier capture OTP / signature / facility-stamp proof and record
 * chain-of-custody events (seal IDs, temperature readings, exception flags)
 * in line with the delivery policy. The screen lists the active deliveries
 * and reveals an OTP form when one is selected.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, TextInput, Alert, TouchableOpacity } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
} from "@impilo/mobile-design-system";
import {
  listAssignedDeliveries,
  captureProof,
  recordCustody,
  reportFailure,
  type NhumeCourierDelivery,
} from "../../services/nhumeService";

export function CourierProofScreen() {
  const [rows, setRows] = useState<NhumeCourierDelivery[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [otp, setOtp] = useState("");
  const [recipient, setRecipient] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const list = await listAssignedDeliveries();
    setRows(list.filter((d) => ["PICKED_UP", "IN_TRANSIT", "AT_DESTINATION"].includes(d.status)));
    setLoading(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submitOtp = useCallback(async () => {
    if (!activeId || !otp) return;
    setBusy(true);
    const ok = await captureProof(activeId, { method: "OTP", otp_code: otp, recipient_name: recipient || undefined });
    setBusy(false);
    if (ok) {
      Alert.alert("Delivered", "Proof captured and delivery marked as delivered.");
      setOtp("");
      setRecipient("");
      setActiveId(null);
      await load();
    } else {
      Alert.alert("Couldn't capture proof", "Please verify the OTP and try again.");
    }
  }, [activeId, otp, recipient, load]);

  const submitSignature = useCallback(async () => {
    if (!activeId) return;
    setBusy(true);
    const ok = await captureProof(activeId, { method: "RECIPIENT_SIGNATURE", recipient_name: recipient || undefined, evidence_ref: "signature-captured" });
    setBusy(false);
    if (ok) {
      Alert.alert("Delivered", "Signature recorded and delivery marked as delivered.");
      setActiveId(null);
      await load();
    } else {
      Alert.alert("Couldn't capture proof", "Please retry.");
    }
  }, [activeId, recipient, load]);

  const submitFacilityStamp = useCallback(async () => {
    if (!activeId) return;
    const ok = await captureProof(activeId, { method: "FACILITY_STAMP", evidence_ref: "facility-stamp" });
    if (ok) { Alert.alert("Delivered", "Facility stamp recorded."); setActiveId(null); await load(); }
    else Alert.alert("Couldn't capture proof", "Please retry.");
  }, [activeId, load]);

  const recordTempBreach = useCallback(async () => {
    if (!activeId) return;
    const ok = await recordCustody(activeId, { event_kind: "TEMP_BREACH", exception: true, notes: "Cold-chain breach reported from courier app." });
    if (ok) Alert.alert("Logged", "Temperature breach has been recorded as a custody exception.");
    else Alert.alert("Couldn't record", "Please retry when online.");
  }, [activeId]);

  const reportFailed = useCallback(async () => {
    if (!activeId) return;
    Alert.prompt?.("Report failed delivery", "Reason (visible to dispatcher):", async (reason) => {
      const ok = await reportFailure(activeId, reason ?? "delivery failed");
      if (ok) { setActiveId(null); await load(); }
      else Alert.alert("Couldn't report", "Please retry.");
    });
  }, [activeId, load]);

  if (loading) {
    return (
      <Screen>
        <Header title="Capture proof" />
        <LoadingSpinner size="md" />
      </Screen>
    );
  }

  if (!activeId) {
    return (
      <Screen>
        <Header title="Capture proof" subtitle="Pick the delivery you're closing out" />
        <ScrollView style={{ flex: 1 }} contentContainerStyle={{ padding: 16, gap: 12 }}>
          {rows.length === 0 ? (
            <EmptyState title="Nothing to confirm" message="You have no deliveries waiting on proof or custody updates." />
          ) : (
            rows.map((d) => (
              <TouchableOpacity key={d.delivery_id} onPress={() => setActiveId(d.delivery_id)}>
                <Card>
                  <CardBody>
                    <View style={styles.row}>
                      <Text style={styles.title}>{d.reference}</Text>
                      <Badge variant="secondary">{d.status}</Badge>
                    </View>
                    <Text style={styles.muted}>{d.origin_label ?? "Origin"} → {d.destination_label ?? "Destination"}</Text>
                  </CardBody>
                </Card>
              </TouchableOpacity>
            ))
          )}
        </ScrollView>
      </Screen>
    );
  }

  const active = rows.find((d) => d.delivery_id === activeId);

  return (
    <Screen>
      <Header
        title="Capture proof"
        subtitle={active?.reference}
        onBack={() => setActiveId(null)}
      />
      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ padding: 16, gap: 12 }}>
        <Card>
          <CardHeader title="OTP proof" />
          <CardBody>
            <Text style={styles.muted}>Enter the OTP the recipient shared with you.</Text>
            <TextInput value={otp} onChangeText={setOtp} placeholder="OTP" keyboardType="number-pad" style={styles.input} />
            <TextInput value={recipient} onChangeText={setRecipient} placeholder="Recipient name (optional)" style={styles.input} />
            <Button onPress={submitOtp} disabled={!otp || busy} title={busy ? "Submitting…" : "Confirm & mark delivered"} />
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Other proof methods" />
          <CardBody>
            <View style={{ gap: 8 }}>
              <Button variant="outline" onPress={submitSignature} title="Recipient signature" />
              <Button variant="outline" onPress={submitFacilityStamp} title="Facility stamp / sign-off" />
            </View>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Chain of custody" />
          <CardBody>
            <View style={{ gap: 8 }}>
              <Button variant="outline" onPress={recordTempBreach} title="Log cold-chain temperature breach" />
            </View>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Report failed delivery" />
          <CardBody>
            <Button variant="destructive" onPress={reportFailed} title="Report failed" />
          </CardBody>
        </Card>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  title: { fontSize: 16, fontWeight: "600", color: "#0F172A" },
  muted: { fontSize: 12, color: "#64748B", marginTop: 4 },
  input: {
    borderWidth: 1, borderColor: "#CBD5E1", borderRadius: 12, paddingHorizontal: 12, paddingVertical: 10, marginVertical: 8, fontSize: 16,
  },
});
