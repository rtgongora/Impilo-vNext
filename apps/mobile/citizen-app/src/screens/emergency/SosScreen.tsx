/**
 * SosScreen — Citizen one-tap SOS. Raises a real emergency request via the Daidzai BFF
 * and navigates to tracking. Life-safety, low friction; no payment is ever requested.
 */
import React, { useState } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Screen, Header, Card, CardBody, Button, TextField, ErrorState } from "@impilo/mobile-design-system";
import { captureCurrentLocation } from "@impilo/mobile-ndila";
import { createSos, type CreateSosInput } from "../../services/emergencyService";

const CATEGORIES = ["MEDICAL", "CARDIAC", "RESPIRATORY", "TRAUMA", "OBSTETRIC", "MENTAL_HEALTH", "OTHER"];

/**
 * Best-effort GPS capture — NEVER blocks a life-safety SOS. If the device denies location or
 * times out, the request still goes out (with the free-text location the person typed).
 */
async function bestEffortLocation(): Promise<{ lat?: number; lng?: number }> {
  try {
    const fix = await captureCurrentLocation({ highAccuracy: true, timeoutMs: 6000 });
    return { lat: fix.coordinate.latitude, lng: fix.coordinate.longitude };
  } catch {
    return {};
  }
}

export function SosScreen({ onTrack }: { onTrack?: (requestId: string) => void }) {
  const [forSelf, setForSelf] = useState(true);
  const [category, setCategory] = useState("MEDICAL");
  const [severity, setSeverity] = useState("CRITICAL");
  const [where, setWhere] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const send = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const { lat, lng } = await bestEffortLocation();
      const input: CreateSosInput = {
        forSelf,
        emergencyCategory: category,
        severity,
        locationDescription: where || undefined,
        lat,
        lng,
      };
      const req = await createSos(input);
      if (req?.id) onTrack?.(req.id);
      else setError("Your SOS was sent but no reference was returned. Please call for help.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not send your SOS. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Screen>
      <Header title="Send SOS" subtitle="Help is dispatched without payment" />
      <ScrollView contentContainerStyle={styles.body}>
        <Card>
          <CardBody>
            <View style={styles.row}>
              <Button title="For me" variant={forSelf ? "primary" : "secondary"} onPress={() => setForSelf(true)} />
              <Button title="For someone else" variant={!forSelf ? "primary" : "secondary"} onPress={() => setForSelf(false)} />
            </View>

            <Text style={styles.label}>What kind of emergency?</Text>
            <View style={styles.chips}>
              {CATEGORIES.map((c) => (
                <Button
                  key={c}
                  title={c.replace(/_/g, " ")}
                  size="sm"
                  variant={c === category ? "destructive" : "outline"}
                  onPress={() => setCategory(c)}
                  testID={`sos-category-${c.toLowerCase()}`}
                />
              ))}
            </View>

            <Text style={styles.label}>How serious is it?</Text>
            <View style={styles.chips}>
              {["CRITICAL", "HIGH", "MODERATE", "LOW"].map((s) => (
                <Button
                  key={s}
                  title={s}
                  size="sm"
                  variant={s === severity ? "destructive" : "outline"}
                  onPress={() => setSeverity(s)}
                  testID={`sos-severity-${s.toLowerCase()}`}
                />
              ))}
            </View>

            <Text style={styles.label}>Where are you? (optional)</Text>
            <TextField value={where} onChangeText={setWhere} placeholder="e.g. near the bus rank" />

            {error ? <ErrorState message={error} /> : null}

            <Button
              title={submitting ? "Sending SOS…" : "Send SOS now"}
              variant="destructive"
              loading={submitting}
              disabled={submitting}
              onPress={send}
            />
          </CardBody>
        </Card>
        <Text style={styles.help}>
          Stay with the person if you can and keep them calm. The response team may call you back on this number.
        </Text>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { padding: 16, gap: 12 },
  row: { flexDirection: "row", gap: 8, marginBottom: 12 },
  label: { fontWeight: "600", marginTop: 12, marginBottom: 6 },
  chips: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  help: { color: "#64748b", fontSize: 12, marginTop: 8 },
});
