/**
 * ReportEquipmentFaultScreen — report an equipment fault from the field.
 * Safety-flagged faults route to Rito; routine faults spawn a corrective maintenance task.
 */

import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView, Switch } from "react-native";
import { Card, CardHeader, CardBody, Button, Badge, TextField, Select } from "@impilo/mobile-design-system";
import { reportFault, type FaultSeverity } from "../../services/equipmentService";

const SEVERITY_OPTIONS = [
  { label: "Minor", value: "MINOR" },
  { label: "Major", value: "MAJOR" },
  { label: "Safety", value: "SAFETY" },
];

export function ReportEquipmentFaultScreen({ equipmentId }: { equipmentId: string }) {
  const [summary, setSummary] = useState("");
  const [detail, setDetail] = useState("");
  const [severity, setSeverity] = useState<FaultSeverity>("MINOR");
  const [safety, setSafety] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [result, setResult] = useState<Record<string, unknown> | null>(null);

  const submit = useCallback(async () => {
    setSubmitting(true);
    setError(null);
    try {
      const r = await reportFault({
        equipmentId,
        summary,
        detail: detail || undefined,
        severity,
        safetyConcern: safety || severity === "SAFETY",
      });
      setResult(r);
      setSummary("");
      setDetail("");
    } catch (e) {
      setError(e as Error);
    } finally {
      setSubmitting(false);
    }
  }, [equipmentId, summary, detail, severity, safety]);

  if (result) {
    return (
      <View style={styles.container}>
        <Card>
          <CardHeader title="Fault reported" />
          <CardBody>
            <Badge label={String(result.status ?? "OPEN")} />
            <Text style={styles.meta}>
              {result.status === "LINKED"
                ? "Routed to Rito for safety review."
                : "A corrective maintenance task has been opened."}
            </Text>
            <Button title="Report another" onPress={() => setResult(null)} />
          </CardBody>
        </Card>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      <Card>
        <CardHeader title="Report equipment fault" />
        <CardBody>
          <TextField label="Summary" value={summary} onChangeText={setSummary} placeholder="What is wrong?" />
          <TextField label="Detail" value={detail} onChangeText={setDetail} placeholder="Optional detail" multiline />
          <Select label="Severity" value={severity} onChange={(v) => setSeverity(v as FaultSeverity)} options={SEVERITY_OPTIONS} />
          <View style={styles.row}>
            <Text>Safety concern (routes to Rito)</Text>
            <Switch value={safety} onValueChange={setSafety} />
          </View>
          {error ? <Text style={styles.error}>Submit failed. Try again.</Text> : null}
          <Button title={submitting ? "Submitting…" : "Submit fault"} onPress={submit} disabled={submitting || !summary.trim()} />
        </CardBody>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 12 },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginVertical: 8 },
  meta: { color: "#6b7280", marginVertical: 8 },
  error: { color: "#b91c1c", marginVertical: 6 },
});
