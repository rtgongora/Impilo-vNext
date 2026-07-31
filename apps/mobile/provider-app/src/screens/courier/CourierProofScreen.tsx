/**
 * CourierProofScreen — capture proof of delivery & custody events.
 *
 * OTP uses a recipient-entered code. Signature and facility-stamp require a
 * real camera/library image URI as evidence_ref — never fabricated literals.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, TextInput, Alert, TouchableOpacity } from "react-native";
import * as ImagePicker from "expo-image-picker";
import { captureCurrentLocation } from "@impilo/mobile-ndila";
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
  type NhumeProofPayload,
} from "../../services/nhumeService";

async function pickEvidencePhoto(): Promise<string | null> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) {
    const library = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!library.granted) {
      Alert.alert("Permission needed", "Camera or photo library access is required to capture proof.");
      return null;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ["images"],
      quality: 0.7,
    });
    if (result.canceled || !result.assets?.[0]?.uri) return null;
    return result.assets[0].uri;
  }
  const result = await ImagePicker.launchCameraAsync({
    mediaTypes: ["images"],
    quality: 0.7,
  });
  if (result.canceled || !result.assets?.[0]?.uri) return null;
  return result.assets[0].uri;
}

async function optionalGps(): Promise<{ captured_lat?: number; captured_lng?: number }> {
  try {
    const fix = await captureCurrentLocation({ highAccuracy: true, timeoutMs: 5000 });
    if (fix?.coordinate?.latitude != null && fix?.coordinate?.longitude != null) {
      return { captured_lat: fix.coordinate.latitude, captured_lng: fix.coordinate.longitude };
    }
  } catch {
    // GPS is additive for PoD — do not block proof on location failure.
  }
  return {};
}

export function CourierProofScreen() {
  const [rows, setRows] = useState<NhumeCourierDelivery[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [otp, setOtp] = useState("");
  const [recipient, setRecipient] = useState("");
  const [busy, setBusy] = useState(false);
  const [evidenceUri, setEvidenceUri] = useState<string | null>(null);

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
    const gps = await optionalGps();
    const ok = await captureProof(activeId, {
      method: "OTP",
      proof_stage: "DELIVERY",
      mark_delivered: true,
      otp_code: otp,
      recipient_name: recipient || undefined,
      ...gps,
    });
    setBusy(false);
    if (ok) {
      Alert.alert("Delivered", "Proof captured and delivery marked as delivered.");
      setOtp("");
      setRecipient("");
      setActiveId(null);
      setEvidenceUri(null);
      await load();
    } else {
      Alert.alert("Couldn't capture proof", "Please verify the OTP and try again.");
    }
  }, [activeId, otp, recipient, load]);

  const captureEvidence = useCallback(async () => {
    const uri = await pickEvidencePhoto();
    if (uri) setEvidenceUri(uri);
  }, []);

  const submitPhotoProof = useCallback(async (method: "RECIPIENT_SIGNATURE" | "FACILITY_STAMP") => {
    if (!activeId) return;
    if (!evidenceUri) {
      Alert.alert("Photo required", "Capture a photo of the signature or facility stamp before submitting.");
      return;
    }
    setBusy(true);
    const gps = await optionalGps();
    const payload: NhumeProofPayload = {
      method,
      proof_stage: "DELIVERY",
      mark_delivered: true,
      recipient_name: recipient || undefined,
      evidence_ref: evidenceUri,
      ...gps,
    };
    const ok = await captureProof(activeId, payload);
    setBusy(false);
    if (ok) {
      Alert.alert("Delivered", "Proof recorded and delivery marked as delivered.");
      setActiveId(null);
      setEvidenceUri(null);
      setRecipient("");
      await load();
    } else {
      Alert.alert("Couldn't capture proof", "Please retry.");
    }
  }, [activeId, evidenceUri, recipient, load]);

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
        onBack={() => { setActiveId(null); setEvidenceUri(null); }}
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
          <CardHeader title="Photo proof (signature / stamp)" />
          <CardBody>
            <Text style={styles.muted}>
              Capture a photo of the recipient signature or facility stamp. A photo is required —
              fabricated evidence references are not accepted.
            </Text>
            <View style={{ gap: 8, marginTop: 8 }}>
              <Button variant="outline" onPress={captureEvidence} title={evidenceUri ? "Retake evidence photo" : "Capture evidence photo"} />
              {evidenceUri ? (
                <Text style={styles.muted} numberOfLines={1}>Evidence ready: {evidenceUri}</Text>
              ) : null}
              <Button
                variant="outline"
                onPress={() => void submitPhotoProof("RECIPIENT_SIGNATURE")}
                disabled={!evidenceUri || busy}
                title="Submit as recipient signature"
              />
              <Button
                variant="outline"
                onPress={() => void submitPhotoProof("FACILITY_STAMP")}
                disabled={!evidenceUri || busy}
                title="Submit as facility stamp"
              />
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
