/**
 * AssessmentsSection — Baseline wellness assessment (multi-step wizard).
 * Backed by the Simba sovereign wellness assessment + risk scoring via
 * `wellnessAssessmentService` (start/resume -> save step -> complete). Mirrors the web wizard
 * at /wellness/assessment. Replaces the previous dead "Start Assessment" empty-state button.
 *
 * NOTE: apps/mobile uses the pnpm workspace protocol; the vitest runner does not execute in this
 * environment. This screen is type-checked (tsc --noEmit) against @impilo/mobile-design-system +
 * the shared api client; no runtime unit test was run here (documented honest partial).
 */

import React, { useMemo, useState } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import {
  Screen,
  Header,
  Button,
  Card,
  CardBody,
  Select,
  TextField,
  Progress,
  Badge,
  EmptyState,
} from "@impilo/mobile-design-system";
import { useAppStore } from "../../stores/appStore";
import {
  startOrResumeAssessment,
  saveAssessmentStep,
  completeAssessment,
  type Assessment,
  type AssessmentResponseInput,
  type AssessmentResult,
} from "../../services/wellnessAssessmentService";

type Step = "INTRO" | "LIFESTYLE" | "MEASUREMENTS" | "RISK" | "SUMMARY" | "RESULT";
const STEP_ORDER: Step[] = ["INTRO", "LIFESTYLE", "MEASUREMENTS", "RISK", "SUMMARY", "RESULT"];

const BAND_LABEL: Record<string, { label: string; tone: "default" | "secondary" | "destructive" }> = {
  LOW: { label: "Low wellness risk", tone: "default" },
  MODERATE: { label: "Moderate wellness risk", tone: "secondary" },
  HIGH: { label: "High wellness risk", tone: "destructive" },
  NEEDS_CLINICAL_REVIEW: { label: "Needs clinical review", tone: "destructive" },
  URGENT_RED_FLAG: { label: "Urgent — please seek support", tone: "destructive" },
};

export function AssessmentsSection() {
  const cpid = useAppStore((s) => s.profile?.cpid);

  const [step, setStep] = useState<Step>("INTRO");
  const [assessment, setAssessment] = useState<Assessment | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [result, setResult] = useState<AssessmentResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const progress = useMemo(() => STEP_ORDER.indexOf(step) / (STEP_ORDER.length - 1), [step]);

  function set(key: string, value: string) {
    setAnswers((a) => ({ ...a, [key]: value }));
  }

  async function begin() {
    if (!cpid) {
      setError("We could not find your health profile. Please sign in again.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const a = await startOrResumeAssessment(cpid);
      setAssessment(a);
      setStep("LIFESTYLE");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not start the assessment.");
    } finally {
      setBusy(false);
    }
  }

  async function saveAndAdvance(
    section: AssessmentResponseInput["section"],
    keys: string[],
    next: Step,
  ) {
    if (!assessment) return;
    setBusy(true);
    setError(null);
    try {
      const responses: AssessmentResponseInput[] = keys
        .filter((k) => answers[k] != null && answers[k] !== "")
        .map((k) => {
          const numeric = Number(answers[k]);
          const isNum = section === "MEASUREMENT" && !Number.isNaN(numeric);
          return {
            section,
            question_code: k,
            value_text: isNum ? null : answers[k],
            value_numeric: isNum ? numeric : null,
          };
        });
      const updated = await saveAssessmentStep(assessment.assessmentId, section, undefined, responses);
      setAssessment(updated);
      setStep(next);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save your answers.");
    } finally {
      setBusy(false);
    }
  }

  async function submit() {
    if (!assessment) return;
    setBusy(true);
    setError(null);
    try {
      const r = await completeAssessment(assessment.assessmentId);
      setResult(r);
      setStep("RESULT");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not calculate your results.");
    } finally {
      setBusy(false);
    }
  }

  function restart() {
    setStep("INTRO");
    setAssessment(null);
    setAnswers({});
    setResult(null);
    setError(null);
  }

  return (
    <Screen>
      <Header title="Wellness assessment" />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        {step !== "INTRO" && step !== "RESULT" && (
          <Progress value={progress} max={1} size="sm" colorScheme="default" />
        )}

        {error ? (
          <Text accessibilityRole="alert" style={styles.error} testID="assessment-error">
            {error}
          </Text>
        ) : null}

        {step === "INTRO" && (
          <EmptyState
            title="Baseline wellness assessment"
            description="A short, private check-in that gives you a personalised wellness picture. It is a wellness check-in, not a medical diagnosis — if anything urgent shows up we will help you connect with care."
            actionLabel={busy ? "Starting…" : "Start assessment"}
            onAction={begin}
          />
        )}

        {step === "LIFESTYLE" && (
          <Card>
            <CardBody>
              <View style={styles.form}>
                <Text style={styles.stepTitle}>Lifestyle</Text>
                <Select
                  label="Do you use tobacco?"
                  value={answers.tobacco_use ?? ""}
                  onChange={(v: string) => set("tobacco_use", v)}
                  options={[
                    { label: "Never", value: "NEVER" },
                    { label: "Former", value: "FORMER" },
                    { label: "Weekly", value: "WEEKLY" },
                    { label: "Daily", value: "DAILY" },
                  ]}
                  testID="assessment-tobacco"
                />
                <Button
                  title="Next"
                  variant="primary"
                  disabled={busy}
                  onPress={() => saveAndAdvance("LIFESTYLE", ["tobacco_use"], "MEASUREMENTS")}
                  testID="assessment-next-lifestyle"
                />
              </View>
            </CardBody>
          </Card>
        )}

        {step === "MEASUREMENTS" && (
          <Card>
            <CardBody>
              <View style={styles.form}>
                <Text style={styles.stepTitle}>Measurements</Text>
                <TextField
                  label="Systolic blood pressure (mmHg)"
                  value={answers.systolic_bp ?? ""}
                  onChange={(v: string) => set("systolic_bp", v)}
                  keyboardType="numeric"
                  testID="assessment-systolic"
                />
                <TextField
                  label="Body mass index (BMI)"
                  value={answers.bmi ?? ""}
                  onChange={(v: string) => set("bmi", v)}
                  keyboardType="numeric"
                  testID="assessment-bmi"
                />
                <TextField
                  label="Fasting glucose (mmol/L)"
                  value={answers.fasting_glucose ?? ""}
                  onChange={(v: string) => set("fasting_glucose", v)}
                  keyboardType="numeric"
                  testID="assessment-glucose"
                />
                <TextField
                  label="Waist circumference (cm)"
                  value={answers.waist_cm ?? ""}
                  onChange={(v: string) => set("waist_cm", v)}
                  keyboardType="numeric"
                  testID="assessment-waist"
                />
                <Button
                  title="Next"
                  variant="primary"
                  disabled={busy}
                  onPress={() =>
                    saveAndAdvance(
                      "MEASUREMENT",
                      ["systolic_bp", "bmi", "fasting_glucose", "waist_cm"],
                      "RISK",
                    )
                  }
                  testID="assessment-next-measurements"
                />
              </View>
            </CardBody>
          </Card>
        )}

        {step === "RISK" && (
          <Card>
            <CardBody>
              <View style={styles.form}>
                <Text style={styles.stepTitle}>Wellbeing</Text>
                <TextField
                  label="Over the last 2 weeks, rate your low-mood level (0 calm – 27 severe)"
                  value={answers.mood_score ?? ""}
                  onChange={(v: string) => set("mood_score", v)}
                  keyboardType="numeric"
                  testID="assessment-mood"
                />
                <Select
                  label="Have you had thoughts of harming yourself?"
                  value={answers.self_harm_thoughts ?? ""}
                  onChange={(v: string) => set("self_harm_thoughts", v)}
                  options={[
                    { label: "No", value: "NO" },
                    { label: "Yes", value: "YES" },
                  ]}
                  testID="assessment-self-harm"
                />
                <Button
                  title="Next"
                  variant="primary"
                  disabled={busy}
                  onPress={() =>
                    saveAndAdvance("RISK_QUESTION", ["mood_score", "self_harm_thoughts"], "SUMMARY")
                  }
                  testID="assessment-next-risk"
                />
              </View>
            </CardBody>
          </Card>
        )}

        {step === "SUMMARY" && (
          <Card>
            <CardBody>
              <View style={styles.form}>
                <Text style={styles.stepTitle}>Ready to see your results?</Text>
                <Text style={styles.body}>
                  We will calculate your wellness picture from your answers and suggest next steps.
                </Text>
                <Button
                  title={busy ? "Calculating…" : "Calculate my results"}
                  variant="primary"
                  disabled={busy}
                  onPress={submit}
                  testID="assessment-submit"
                />
              </View>
            </CardBody>
          </Card>
        )}

        {step === "RESULT" && result && (
          <Card>
            <CardBody>
              <View style={styles.form}>
                <Badge variant={BAND_LABEL[result.riskBand]?.tone ?? "default"}>
                  {BAND_LABEL[result.riskBand]?.label ?? result.riskBand}
                </Badge>
                {result.careLinkageRouted ? (
                  <Text style={styles.careNote} testID="assessment-care-note">
                    A provider follow-up has been created for you. Someone from your wellness care
                    team will reach out.
                  </Text>
                ) : null}
                {result.nextActions.length > 0 && (
                  <View>
                    <Text style={styles.stepTitle}>Suggested next steps</Text>
                    {result.nextActions.map((a) => (
                      <Text key={a} style={styles.action}>
                        • {a}
                      </Text>
                    ))}
                  </View>
                )}
                <Button
                  title="Retake assessment"
                  variant="secondary"
                  onPress={restart}
                  testID="assessment-restart"
                />
              </View>
            </CardBody>
          </Card>
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { flex: 1 },
  contentContainer: { padding: 16, gap: 16 },
  form: { gap: 12 },
  stepTitle: { fontSize: 16, fontWeight: "600", color: "#0f172a" },
  body: { fontSize: 14, color: "#475569" },
  error: { color: "#dc2626", fontSize: 14 },
  careNote: { color: "#92400e", fontSize: 14 },
  action: { color: "#475569", fontSize: 14, marginTop: 4 },
});
