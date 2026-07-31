/**
 * PregnancySection — her current pregnancy, and booking one (RMNP W12).
 *
 * Status semantics this screen must get right (see pregnancyService.ts):
 *   - a 201 (created) or 200 (replayed offline packet) response is success either way — never a
 *     duplicate warning;
 *   - a 409 conflict (she already has an open pregnancy) is a reconciliation outcome, shown with
 *     the existing episode reference, not a generic failure banner;
 *   - a 422 (undatable) asks for one more fact (LMP or scan), not a generic error;
 *   - `risk_status` is rendered exactly as recorded — NOT_ASSESSED is never shown as "low risk".
 *
 * `clientOfflineId` is generated once when the form opens and resent unchanged if she retries
 * after a failed attempt, so a flaky-connectivity resubmit comes back as a 200 replay of the same
 * episode rather than a second booking.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from "react-native";
import { Card, CardHeader, CardBody, Button, Badge, TextField, Switch, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import {
  bookPregnancy,
  fetchCurrentPregnancy,
  fetchPregnancyHistory,
  newClientOfflineId,
  isPregnancyConflict,
  isPregnancyUndatable,
  type DatingMethod,
  type PregnancyEpisode,
} from "../../services/pregnancyService";

function statusLabel(status: string): string {
  switch (status) {
    case "ONGOING":
      return "Ongoing";
    case "DELIVERED":
      return "Delivered";
    case "LOST":
      return "Ended in loss";
    case "TERMINATED":
      return "Ended";
    default:
      return status.replace(/_/g, " ").toLowerCase();
  }
}

function riskStatusNotice(riskStatus: string | null): string | null {
  if (!riskStatus || riskStatus === "NOT_ASSESSED") {
    return "Your pregnancy risk has not been assessed yet — this is not the same as being told you are low-risk.";
  }
  return null;
}

function CurrentPregnancyCard({ episode }: { episode: PregnancyEpisode }) {
  const risk = riskStatusNotice(episode.riskStatus);
  return (
    <Card testID="pregnancy-current-card">
      <CardHeader
        title={`Your current pregnancy — ${statusLabel(episode.status)}`}
        rightElement={<Badge variant="primary">{episode.status}</Badge>}
      />
      <CardBody>
        <View style={styles.rowGrid}>
          <View style={styles.rowItem}>
            <Text style={styles.label}>Estimated delivery date</Text>
            <Text style={styles.value}>
              {episode.estimatedDeliveryDate
                ? new Date(episode.estimatedDeliveryDate).toLocaleDateString()
                : "Not yet estimated"}
            </Text>
          </View>
          <View style={styles.rowItem}>
            <Text style={styles.label}>Dated from</Text>
            <Text style={styles.value}>
              {episode.datingMethod ? episode.datingMethod.replace(/_/g, " ").toLowerCase() : "Not recorded"}
            </Text>
          </View>
          <View style={styles.rowItem}>
            <Text style={styles.label}>Pregnancy start</Text>
            <Text style={styles.value}>
              {episode.pregnancyStartDate
                ? new Date(episode.pregnancyStartDate).toLocaleDateString()
                : "Not recorded"}
            </Text>
          </View>
        </View>

        {risk ? (
          <Text style={styles.warningText} testID="pregnancy-risk-notice">
            {risk}
          </Text>
        ) : (
          <Text style={styles.subText}>
            Recorded risk status: {episode.riskStatus?.replace(/_/g, " ").toLowerCase()}.
          </Text>
        )}
      </CardBody>
    </Card>
  );
}

type BookingOutcome =
  | { kind: "success"; episode: PregnancyEpisode }
  | { kind: "conflict"; existingId?: string; task?: string; message: string }
  | { kind: "undatable"; message: string }
  | { kind: "error"; message: string };

function BookPregnancyForm({ onBooked }: { onBooked: () => void }) {
  const [method, setMethod] = useState<DatingMethod>("LMP");
  const [lmpDate, setLmpDate] = useState("");
  const [lmpCertain, setLmpCertain] = useState(true);
  const [scanDate, setScanDate] = useState("");
  const [scanGaDays, setScanGaDays] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [outcome, setOutcome] = useState<BookingOutcome | null>(null);
  // Stable across retries of the same attempt — a resubmit after a failure must replay, not duplicate.
  const [clientOfflineId, setClientOfflineId] = useState(() => newClientOfflineId());

  const canSubmit =
    !submitting && (method === "LMP" ? !!lmpDate : !!scanDate && !!scanGaDays);

  const submit = useCallback(async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setOutcome(null);

    const datingBases =
      method === "LMP"
        ? [{ method: "LMP" as const, lastMenstrualPeriod: lmpDate, lastMenstrualPeriodCertain: lmpCertain }]
        : [
            {
              method: "ULTRASOUND" as const,
              observedOn: scanDate,
              measuredGestationalAgeDays: Number(scanGaDays),
            },
          ];

    try {
      const episode = await bookPregnancy({ datingBases, clientOfflineId });
      setOutcome({ kind: "success", episode });
      onBooked();
    } catch (err) {
      if (isPregnancyConflict(err)) {
        setOutcome({
          kind: "conflict",
          existingId: err.existingPregnancyEpisodeId,
          task: err.reconciliationTask,
          message: err.message,
        });
      } else if (isPregnancyUndatable(err)) {
        setOutcome({ kind: "undatable", message: err.message });
      } else {
        setOutcome({
          kind: "error",
          message: "We couldn't book this right now. Please try again in a moment.",
        });
        // A fresh id for a genuinely new attempt after an unrelated failure; a conflict/undatable
        // outcome above is not retried with a new id, since nothing about the identity of the
        // attempt changed.
        setClientOfflineId(newClientOfflineId());
      }
    } finally {
      setSubmitting(false);
    }
  }, [canSubmit, method, lmpDate, lmpCertain, scanDate, scanGaDays, clientOfflineId, onBooked]);

  if (outcome?.kind === "success") {
    return (
      <Card testID="pregnancy-booking-success">
        <CardBody>
          <Text style={styles.successHeading}>
            {outcome.episode.replayed ? "Already recorded" : "Pregnancy recorded"}
          </Text>
          <Text style={styles.subText}>
            {outcome.episode.replayed
              ? "This booking had already been saved — nothing new was created, and no information was lost."
              : "Your pregnancy has been recorded. Your care team will use this to plan your antenatal visits."}
          </Text>
        </CardBody>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title="Record a pregnancy" />
      <CardBody>
        <View style={styles.formContainer}>
          <Text style={styles.subText}>
            Tell us either your last menstrual period, or the date and result of a dating scan —
            we need at least one to estimate your delivery date.
          </Text>

          {outcome?.kind === "conflict" ? (
            <View style={styles.noticeBox} testID="pregnancy-booking-conflict">
              <Text style={styles.noticeHeading}>You already have a pregnancy on record</Text>
              <Text style={styles.noticeText}>{outcome.message}</Text>
              {outcome.task ? <Text style={styles.noticeSubText}>{outcome.task}</Text> : null}
              {outcome.existingId ? (
                <Text style={styles.noticeSubText}>Reference: {outcome.existingId}</Text>
              ) : null}
            </View>
          ) : null}

          {outcome?.kind === "undatable" ? (
            <View style={styles.noticeBox} testID="pregnancy-booking-undatable">
              <Text style={styles.noticeHeading}>We need one more detail</Text>
              <Text style={styles.noticeText}>{outcome.message}</Text>
            </View>
          ) : null}

          {outcome?.kind === "error" ? (
            <Text accessibilityRole="alert" style={styles.errorText} testID="pregnancy-booking-error">
              {outcome.message}
            </Text>
          ) : null}

          <View style={styles.methodRow}>
            <TouchableOpacity
              style={[styles.methodChip, method === "LMP" && styles.methodChipActive]}
              onPress={() => setMethod("LMP")}
              testID="pregnancy-method-lmp"
            >
              <Text style={[styles.methodChipText, method === "LMP" && styles.methodChipTextActive]}>
                Last menstrual period
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.methodChip, method === "ULTRASOUND" && styles.methodChipActive]}
              onPress={() => setMethod("ULTRASOUND")}
              testID="pregnancy-method-ultrasound"
            >
              <Text style={[styles.methodChipText, method === "ULTRASOUND" && styles.methodChipTextActive]}>
                Dating scan
              </Text>
            </TouchableOpacity>
          </View>

          {method === "LMP" ? (
            <>
              <TextField
                label="First day of your last period (YYYY-MM-DD)"
                value={lmpDate}
                onChangeText={setLmpDate}
                placeholder="YYYY-MM-DD"
                testID="pregnancy-lmp-date"
              />
              <Switch
                label="I'm fairly certain about this date"
                value={lmpCertain}
                onValueChange={setLmpCertain}
                testID="pregnancy-lmp-certain"
              />
            </>
          ) : (
            <>
              <TextField
                label="Scan date (YYYY-MM-DD)"
                value={scanDate}
                onChangeText={setScanDate}
                placeholder="YYYY-MM-DD"
                testID="pregnancy-scan-date"
              />
              <TextField
                label="Gestational age on scan (days)"
                value={scanGaDays}
                onChangeText={setScanGaDays}
                placeholder="e.g. 84"
                keyboardType="numeric"
                helperText="Ask your clinician or check your scan report for this number."
                testID="pregnancy-scan-ga-days"
              />
            </>
          )}

          <Button
            title={submitting ? "Recording…" : "Record this pregnancy"}
            variant="primary"
            onPress={submit}
            disabled={!canSubmit}
            loading={submitting}
            testID="pregnancy-booking-submit"
          />
        </View>
      </CardBody>
    </Card>
  );
}

function PregnancyHistory({ episodes }: { episodes: PregnancyEpisode[] }) {
  const past = episodes.filter((e) => e.status !== "ONGOING");
  if (past.length === 0) return null;
  return (
    <Card testID="pregnancy-history-card">
      <CardHeader title="Previous pregnancies" />
      <CardBody>
        {past.map((e) => (
          <View key={e.pregnancyEpisodeId} style={styles.historyRow}>
            <Text style={styles.value}>{statusLabel(e.status)}</Text>
            <Text style={styles.subText}>
              {e.endedOn
                ? new Date(e.endedOn).toLocaleDateString()
                : e.pregnancyStartDate
                  ? `Started ${new Date(e.pregnancyStartDate).toLocaleDateString()}`
                  : ""}
            </Text>
          </View>
        ))}
      </CardBody>
    </Card>
  );
}

export function PregnancySection() {
  const [loading, setLoading] = useState(true);
  const [current, setCurrent] = useState<PregnancyEpisode | null>(null);
  const [history, setHistory] = useState<PregnancyEpisode[]>([]);
  const [loadError, setLoadError] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const [currentEpisode, historyEpisodes] = await Promise.all([
        fetchCurrentPregnancy(),
        fetchPregnancyHistory(),
      ]);
      setCurrent(currentEpisode);
      setHistory(historyEpisodes);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <ScrollView testID="pregnancy-section" style={styles.scrollView}>
        <View style={styles.container}>
          <LoadingSpinner size="md" />
        </View>
      </ScrollView>
    );
  }

  return (
    <ScrollView testID="pregnancy-section" style={styles.scrollView} showsVerticalScrollIndicator={false}>
      <View style={styles.container}>
        <Text style={styles.heading}>My pregnancy</Text>
        <Text style={styles.subText}>
          Your current pregnancy, and recording a new one — explained in plain language.
        </Text>

        {loadError ? (
          <Card>
            <CardBody>
              <Text accessibilityRole="alert" style={styles.errorText} testID="pregnancy-load-error">
                We couldn&apos;t load your pregnancy record right now. Please try again in a moment.
              </Text>
              <Button title="Try again" variant="secondary" onPress={load} testID="pregnancy-retry" />
            </CardBody>
          </Card>
        ) : current ? (
          <CurrentPregnancyCard episode={current} />
        ) : (
          <BookPregnancyForm onBooked={load} />
        )}

        <PregnancyHistory episodes={history} />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scrollView: { flex: 1 },
  container: { gap: 12, paddingBottom: 24 },
  heading: { fontSize: 18, fontWeight: "600" },
  subText: { fontSize: 13, color: colors.gray[500] },
  value: { fontSize: 14, color: colors.gray[900], fontWeight: "500" },
  label: { fontSize: 11, color: colors.gray[400], textTransform: "uppercase", letterSpacing: 0.4 },
  formContainer: { gap: 14 },
  rowGrid: { flexDirection: "row", flexWrap: "wrap", gap: 16, marginBottom: 8 },
  rowItem: { minWidth: 140, gap: 2 },
  successHeading: { fontSize: 15, fontWeight: "700", color: "#065F46", marginBottom: 4 },
  warningText: { fontSize: 13, color: "#92400E", backgroundColor: "#FEF3C7", borderRadius: 8, padding: 10 },
  errorText: { fontSize: 13, color: "#991B1B" },
  noticeBox: { backgroundColor: "#FEF3C7", borderRadius: 8, padding: 10, gap: 4 },
  noticeHeading: { fontSize: 13, fontWeight: "700", color: "#92400E" },
  noticeText: { fontSize: 13, color: "#92400E" },
  noticeSubText: { fontSize: 12, color: "#92400E" },
  methodRow: { flexDirection: "row", gap: 8 },
  methodChip: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 14, backgroundColor: colors.gray[100] },
  methodChipActive: { backgroundColor: colors.ui.success.light },
  methodChipText: { fontSize: 12, color: colors.gray[500] },
  methodChipTextActive: { fontWeight: "700", color: "#059669" },
  historyRow: { flexDirection: "row", justifyContent: "space-between", paddingVertical: 6 },
});
