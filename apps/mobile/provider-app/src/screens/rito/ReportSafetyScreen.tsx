/**
 * ReportSafetyScreen — Provider safety reporting (Rito: Quality, Safety &
 * Client Voice). Report a clinical-service safety incident, near-miss,
 * concern, or adverse event from the provider workspace.
 */

import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import {
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  TextField,
  Select,
} from "@impilo/mobile-design-system";
import { reportIncident } from "../../services/ritoSafetyService";
import type {
  RitoSafetyCase,
  RitoSafetyCaseType,
  RitoSeverity,
} from "../../services/ritoSafetyService";

const CASE_TYPE_OPTIONS = [
  { label: "Safety incident", value: "SAFETY_INCIDENT" },
  { label: "Near miss", value: "NEAR_MISS" },
  { label: "Safety concern", value: "SAFETY_CONCERN" },
  { label: "Adverse event", value: "ADVERSE_EVENT" },
];

const SEVERITY_OPTIONS = [
  { label: "Low", value: "LOW" },
  { label: "Moderate", value: "MODERATE" },
  { label: "High", value: "HIGH" },
  { label: "Critical", value: "CRITICAL" },
];

export function ReportSafetyScreen() {
  const [caseType, setCaseType] = useState<RitoSafetyCaseType>("SAFETY_INCIDENT");
  const [severity, setSeverity] = useState<RitoSeverity>("MODERATE");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [submitted, setSubmitted] = useState<RitoSafetyCase | null>(null);

  const resetForm = useCallback(() => {
    setCaseType("SAFETY_INCIDENT");
    setSeverity("MODERATE");
    setTitle("");
    setDescription("");
  }, []);

  const handleSubmit = useCallback(async () => {
    setSubmitting(true);
    setError(null);
    try {
      const result = await reportIncident({ caseType, severity, title, description });
      setSubmitted(result);
      resetForm();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [caseType, severity, title, description, resetForm]);

  if (submitted) {
    return (
      <View style={styles.container} testID="rito-report-success">
        <Card>
          <CardHeader title="Safety report logged" />
          <CardBody>
            <View style={styles.successBody}>
              <Text style={styles.successText}>
                The report has been recorded and routed for quality and safety review.
              </Text>
              <View style={styles.row}>
                <Text style={styles.rowLabel}>Reference</Text>
                <Badge variant="primary">{submitted.caseReference}</Badge>
              </View>
              <View style={styles.row}>
                <Text style={styles.rowLabel}>Status</Text>
                <Badge variant="info">{submitted.status}</Badge>
              </View>
              <Button
                title="Report another"
                variant="ghost"
                onPress={() => setSubmitted(null)}
                testID="rito-report-another"
              />
            </View>
          </CardBody>
        </Card>
      </View>
    );
  }

  return (
    <ScrollView testID="rito-report-screen" style={styles.scrollView} showsVerticalScrollIndicator={false}>
      <View style={styles.container}>
        <Text style={styles.heading}>Report a safety event</Text>
        <Text style={styles.subText}>
          Record an incident, near-miss, concern, or adverse event affecting clinical service safety.
        </Text>

        <Card>
          <CardBody>
            <View style={styles.formContainer}>
              <Select
                label="Event type"
                value={caseType}
                onChange={(value) => setCaseType(value as RitoSafetyCaseType)}
                options={CASE_TYPE_OPTIONS}
                testID="rito-report-type"
              />
              <Select
                label="Severity"
                value={severity}
                onChange={(value) => setSeverity(value as RitoSeverity)}
                options={SEVERITY_OPTIONS}
                testID="rito-report-severity"
              />
              <TextField
                label="Title"
                value={title}
                onChange={setTitle}
                placeholder="Short summary of the event"
                testID="rito-report-title"
              />
              <TextField
                label="Details"
                value={description}
                onChange={setDescription}
                placeholder="What happened, where, and any immediate action taken"
                multiline
                numberOfLines={5}
                testID="rito-report-description"
              />

              {error ? (
                <Text accessibilityRole="alert" style={styles.errorText} testID="rito-report-error">
                  {error.message}
                </Text>
              ) : null}

              <Button
                title={submitting ? "Submitting..." : "Submit report"}
                variant="primary"
                onPress={handleSubmit}
                disabled={submitting || !title.trim() || !description.trim()}
                testID="rito-report-submit"
              />
            </View>
          </CardBody>
        </Card>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    gap: 12,
    paddingBottom: 24,
  },
  heading: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
  },
  subText: {
    fontSize: 13,
    color: "#6B7280",
  },
  formContainer: {
    gap: 14,
  },
  errorText: {
    fontSize: 13,
    color: "#DC2626",
  },
  successBody: {
    gap: 14,
  },
  successText: {
    fontSize: 14,
    color: "#374151",
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  rowLabel: {
    fontSize: 13,
    color: "#6B7280",
    fontWeight: "500",
  },
});
