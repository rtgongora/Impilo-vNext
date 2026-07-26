/**
 * FeedbackScreen — Citizen client voice (Rito: Quality, Safety & Client Voice).
 *
 * Submit a complaint, compliment, suggestion, or safety concern to the
 * rito-quality-safety-service via the experience-bff persona endpoint.
 * On success, surfaces the assigned case reference and offers to track it.
 */

import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Card, CardHeader, CardBody, Button, Badge, TextField, Select, Switch, colors } from "@impilo/mobile-design-system";
import { submitFeedback } from "../../services/ritoFeedbackService";
import type { RitoCase, RitoCaseType } from "../../services/ritoFeedbackService";
import { appStore } from "../../stores/appStore";

const CASE_TYPE_OPTIONS = [
  { label: "Complaint", value: "COMPLAINT" },
  { label: "Compliment", value: "COMPLIMENT" },
  { label: "Suggestion", value: "SUGGESTION" },
  { label: "Safety concern", value: "SAFETY_CONCERN" },
];

export function FeedbackScreen() {
  const [caseType, setCaseType] = useState<RitoCaseType>("COMPLAINT");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [anonymous, setAnonymous] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [submitted, setSubmitted] = useState<RitoCase | null>(null);

  const resetForm = useCallback(() => {
    setCaseType("COMPLAINT");
    setTitle("");
    setDescription("");
    setAnonymous(false);
  }, []);

  const handleSubmit = useCallback(async () => {
    setSubmitting(true);
    setError(null);
    try {
      const result = await submitFeedback({ caseType, title, description, anonymous });
      setSubmitted(result);
      resetForm();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [caseType, title, description, anonymous, resetForm]);

  const handleTrack = useCallback(() => {
    if (!submitted) return;
    appStore.getState().setRitoTrackReference(submitted.caseReference);
    appStore.getState().setPersonalSectionRequest("rito-track");
  }, [submitted]);

  if (submitted) {
    return (
      <ScrollView testID="rito-feedback-success" style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.container}>
          <Card>
            <CardHeader title="Feedback submitted" />
            <CardBody>
              <View style={styles.successBody}>
                <Text style={styles.successText}>
                  Thank you. Your feedback has been received and logged for review.
                </Text>
                <View style={styles.referenceRow}>
                  <Text style={styles.referenceLabel}>Case reference</Text>
                  <Badge variant="primary">{submitted.caseReference}</Badge>
                </View>
                <View style={styles.referenceRow}>
                  <Text style={styles.referenceLabel}>Status</Text>
                  <Badge variant="info">{submitted.status}</Badge>
                </View>
                <Button
                  title="Track this case"
                  variant="primary"
                  onPress={handleTrack}
                  testID="rito-track-submitted"
                />
                <Button
                  title="Submit more feedback"
                  variant="ghost"
                  onPress={() => setSubmitted(null)}
                  testID="rito-submit-another"
                />
              </View>
            </CardBody>
          </Card>
        </View>
      </ScrollView>
    );
  }

  return (
    <ScrollView testID="rito-feedback-screen" style={styles.scrollView} showsVerticalScrollIndicator={false}>
      <View style={styles.container}>
        <Text style={styles.heading}>Share your feedback</Text>
        <Text style={styles.subText}>
          Tell us about your experience — a complaint, compliment, suggestion, or safety concern.
        </Text>

        <Card>
          <CardBody>
            <View style={styles.formContainer}>
              <Select
                label="Type of feedback"
                value={caseType}
                onChange={(value) => setCaseType(value as RitoCaseType)}
                options={CASE_TYPE_OPTIONS}
                testID="rito-case-type"
              />
              <TextField
                label="Title"
                value={title}
                onChange={setTitle}
                placeholder="A short summary"
                testID="rito-title"
              />
              <TextField
                label="Details"
                value={description}
                onChange={setDescription}
                placeholder="Describe what happened in your own words"
                multiline
                numberOfLines={5}
                testID="rito-description"
              />
              <Switch
                label="Submit anonymously"
                value={anonymous}
                onValueChange={setAnonymous}
                testID="rito-anonymous"
              />

              {error ? (
                <Text accessibilityRole="alert" style={styles.errorText} testID="rito-feedback-error">
                  {error.message}
                </Text>
              ) : null}

              <Button
                title={submitting ? "Submitting..." : "Submit feedback"}
                variant="primary"
                onPress={handleSubmit}
                disabled={submitting || !title.trim() || !description.trim()}
                testID="rito-submit-feedback"
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
    fontSize: 18,
    fontWeight: "600",
  },
  subText: {
    fontSize: 13,
    color: colors.gray[500],
  },
  formContainer: {
    gap: 14,
  },
  errorText: {
    fontSize: 13,
    color: colors.ui.error.main,
  },
  successBody: {
    gap: 14,
  },
  successText: {
    fontSize: 14,
    color: colors.gray[700],
  },
  referenceRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  referenceLabel: {
    fontSize: 13,
    color: colors.gray[500],
    fontWeight: "500",
  },
});
