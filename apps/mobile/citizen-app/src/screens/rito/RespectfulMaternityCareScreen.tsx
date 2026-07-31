/**
 * RespectfulMaternityCareScreen — Citizen respectful maternity care survey (RMNP W12).
 *
 * Fetches the governed measure set from the BFF (`respectfulCareService.fetchInstrument`) and
 * renders it — the prompts, scale captions and reverse-scored polarity all come from the server.
 * This screen never carries a hardcoded copy of the questions: if the instrument cannot be
 * fetched it shows an honest "unavailable" state instead of falling back to a stale local copy
 * (docs/frontend/GAP_CLOSURE_RULES.md).
 *
 * Anonymous by default: `identifyMe` is an explicit opt-in. Raw answers only — reverse-scoring is
 * CKP's job, never this screen's.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from "react-native";
import { Card, CardHeader, CardBody, Button, Badge, TextField, Switch, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import {
  fetchInstrument,
  submitRespectfulCareFeedback,
  type RespectfulCareInstrument,
  type RespectfulCareMeasure,
  type RespectfulCareScale,
  type RespectfulCareFeedbackResult,
} from "../../services/respectfulCareService";

function LikertRow({
  measure,
  scale,
  value,
  onChange,
}: {
  measure: RespectfulCareMeasure;
  scale: RespectfulCareScale;
  value: number | undefined;
  onChange: (n: number) => void;
}) {
  const options: number[] = [];
  for (let n = scale.low; n <= scale.high; n += 1) options.push(n);

  return (
    <View style={styles.likertRow} testID={`rmc-row-${measure.measure}`}>
      <Text style={styles.likertPrompt}>{measure.prompt}</Text>
      <View style={styles.likertOptions}>
        {options.map((n) => {
          const selected = value === n;
          return (
            <TouchableOpacity
              key={n}
              onPress={() => onChange(n)}
              testID={`rmc-answer-${measure.measure}-${n}`}
              accessibilityRole="radio"
              accessibilityState={{ selected }}
              style={[styles.likertOption, selected ? styles.likertOptionSelected : undefined]}
            >
              <Text style={[styles.likertOptionText, selected ? styles.likertOptionTextSelected : undefined]}>
                {n}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>
      <View style={styles.likertCaptions}>
        <Text style={styles.likertCaption}>{scale.low} = {scale.lowMeans}</Text>
        <Text style={styles.likertCaption}>{scale.high} = {scale.highMeans}</Text>
      </View>
    </View>
  );
}

export function RespectfulMaternityCareScreen() {
  const [instrument, setInstrument] = useState<RespectfulCareInstrument | null>(null);
  const [loading, setLoading] = useState(true);
  const [instrumentUnavailable, setInstrumentUnavailable] = useState(false);

  const [answers, setAnswers] = useState<Record<string, number>>({});
  const [narrative, setNarrative] = useState("");
  const [facilityId, setFacilityId] = useState("");
  const [identifyMe, setIdentifyMe] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [result, setResult] = useState<RespectfulCareFeedbackResult | null>(null);

  const loadInstrument = useCallback(async () => {
    setLoading(true);
    setInstrumentUnavailable(false);
    try {
      const loaded = await fetchInstrument();
      setInstrument(loaded);
    } catch {
      // No fallback to a local copy of the questions: the reverse-scoring polarity is governed
      // content, and a stale copy would store an answer backwards without anyone noticing.
      setInstrument(null);
      setInstrumentUnavailable(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadInstrument();
  }, [loadInstrument]);

  const setAnswer = (measure: string, n: number) => {
    setAnswers((a) => ({ ...a, [measure]: n }));
  };

  const answeredCount = Object.keys(answers).length;
  const canSubmit = (answeredCount > 0 || narrative.trim().length > 0) && !submitting;

  const handleSubmit = useCallback(async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const reportingPeriod = new Date().toISOString().slice(0, 7);
      const submitted = await submitRespectfulCareFeedback({
        facilityId: facilityId.trim() || undefined,
        reportingPeriod,
        answers: answeredCount > 0 ? answers : undefined,
        narrative: narrative.trim() || undefined,
        identifyMe,
      });
      setResult(submitted);
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : "Could not submit your feedback.");
    } finally {
      setSubmitting(false);
    }
  }, [canSubmit, facilityId, answeredCount, answers, narrative, identifyMe]);

  if (loading) {
    return (
      <ScrollView testID="rmc-screen" style={styles.scrollView}>
        <View style={styles.container}>
          <LoadingSpinner size="md" />
        </View>
      </ScrollView>
    );
  }

  if (instrumentUnavailable) {
    return (
      <ScrollView testID="rmc-screen" style={styles.scrollView}>
        <View style={styles.container}>
          <Card>
            <CardBody>
              <View style={styles.formContainer}>
                <Text style={styles.heading}>The questions could not be loaded</Text>
                <Text style={styles.subText} testID="rmc-instrument-unavailable">
                  We could not retrieve the respectful maternity care questions right now. We do
                  not show a stored copy of them — an old copy could score your answers backwards.
                  Please try again in a moment.
                </Text>
                <Button title="Try again" variant="primary" onPress={loadInstrument} testID="rmc-retry" />
              </View>
            </CardBody>
          </Card>
        </View>
      </ScrollView>
    );
  }

  if (result) {
    const claimCode = result.narrative?.claimCode;
    return (
      <ScrollView testID="rmc-screen" style={styles.scrollView}>
        <View style={styles.container}>
          {result.narrative?.filed && claimCode ? (
            <Card>
              <CardHeader title="Your report was filed" />
              <CardBody>
                <View style={styles.formContainer}>
                  <Text style={styles.subText}>
                    Save this claim code somewhere safe now — it is shown only once and cannot be
                    recovered later. It is the only way to check on your report without giving
                    your identity.
                  </Text>
                  <View style={styles.claimCodeRow}>
                    <Text style={styles.claimCode} testID="rmc-claim-code">
                      {claimCode}
                    </Text>
                  </View>
                  {result.narrative.caseReference ? (
                    <View style={styles.referenceRow}>
                      <Text style={styles.referenceLabel}>Case reference</Text>
                      <Badge variant="primary">{result.narrative.caseReference}</Badge>
                    </View>
                  ) : null}
                </View>
              </CardBody>
            </Card>
          ) : result.narrative && !result.narrative.filed ? (
            <Card>
              <CardBody>
                <Text accessibilityRole="alert" style={styles.errorText} testID="rmc-narrative-failed">
                  {result.narrative.message ??
                    "Your written report was not filed. Keep what you wrote and try again."}
                </Text>
              </CardBody>
            </Card>
          ) : null}

          {result.scores ? (
            <Card>
              <CardBody>
                {result.scores.stored ? (
                  <Text style={styles.successText} testID="rmc-scores-stored">
                    {`${result.scores.measuresStored ?? 0} answer${result.scores.measuresStored === 1 ? "" : "s"} saved.`}
                  </Text>
                ) : (
                  <Text style={styles.warningText} testID="rmc-scores-not-stored">
                    {(result.scores.message ?? "Your ratings were not saved this time.") +
                      (result.narrative?.filed
                        ? " Your written report above was filed and is not affected by this."
                        : "")}
                  </Text>
                )}
              </CardBody>
            </Card>
          ) : null}

          <Text style={styles.subText}>
            {result.anonymous
              ? "You were not identified. Your written report and your ratings are kept separate on purpose."
              : "You chose to be identified with this submission."}
          </Text>
        </View>
      </ScrollView>
    );
  }

  return (
    <ScrollView testID="rmc-screen" style={styles.scrollView} showsVerticalScrollIndicator={false}>
      <View style={styles.container}>
        <Text style={styles.heading}>Respectful maternity care</Text>
        <Text style={styles.subText}>
          Tell us how you were treated during labour, birth or right after — anonymously by
          default. Every question is optional.
        </Text>

        <Card>
          <CardBody>
            <View style={styles.formContainer}>
              {instrument?.measures.map((m) => (
                <LikertRow
                  key={m.measure}
                  measure={m}
                  scale={instrument.scale}
                  value={answers[m.measure]}
                  onChange={(n) => setAnswer(m.measure, n)}
                />
              ))}

              <TextField
                label="Facility (optional)"
                value={facilityId}
                onChange={setFacilityId}
                placeholder="Facility name or ID"
                testID="rmc-facility-id"
              />
              <TextField
                label="In your own words (optional)"
                value={narrative}
                onChange={setNarrative}
                placeholder="Tell us what happened"
                multiline
                numberOfLines={5}
                testID="rmc-narrative"
              />
              <Switch
                label="Identify me with this report"
                value={identifyMe}
                onValueChange={setIdentifyMe}
                testID="rmc-identify-me"
              />

              {submitError ? (
                <Text accessibilityRole="alert" style={styles.errorText} testID="rmc-submit-error">
                  {submitError}
                </Text>
              ) : null}

              <Button
                title={submitting ? "Submitting..." : "Submit"}
                variant="primary"
                onPress={handleSubmit}
                disabled={!canSubmit}
                testID="rmc-submit"
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
  successText: {
    fontSize: 14,
    color: "#059669",
  },
  warningText: {
    fontSize: 13,
    color: "#92400e",
  },
  likertRow: {
    gap: 8,
    borderBottomWidth: 1,
    borderBottomColor: colors.gray[100],
    paddingBottom: 12,
  },
  likertPrompt: {
    fontSize: 14,
    fontWeight: "500",
    color: colors.gray[900],
  },
  likertOptions: {
    flexDirection: "row",
    gap: 8,
  },
  likertOption: {
    width: 34,
    height: 34,
    borderRadius: 17,
    borderWidth: 1,
    borderColor: colors.gray[300],
    alignItems: "center",
    justifyContent: "center",
  },
  likertOptionSelected: {
    borderColor: "#059669",
    backgroundColor: "#059669",
  },
  likertOptionText: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.gray[500],
  },
  likertOptionTextSelected: {
    color: "#FFFFFF",
  },
  likertCaptions: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  likertCaption: {
    fontSize: 11,
    color: colors.gray[400],
  },
  claimCodeRow: {
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.gray[200],
    padding: 12,
    alignItems: "center",
  },
  claimCode: {
    fontSize: 18,
    fontWeight: "700",
    letterSpacing: 1,
    color: colors.gray[900],
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
