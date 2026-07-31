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
  fetchCurrentContraception,
  fetchMyReproductiveIntention,
  recordMyReproductiveIntention,
  newClientOfflineId,
  isPregnancyConflict,
  isPregnancyUndatable,
  type DatingMethod,
  type PregnancyEpisode,
  type ContraceptionCoverage,
  type ReproductiveIntention,
} from "../../services/pregnancyService";
import { ApiError } from "@impilo/mobile-api-client";

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

function ContraceptionCard({
  methods,
  unavailable,
}: {
  methods: ContraceptionCoverage[];
  unavailable: boolean;
}) {
  if (unavailable) {
    return (
      <Card testID="contraception-unavailable">
        <CardBody>
          <Text accessibilityRole="alert" style={styles.errorText}>
            Your contraception record could not be retrieved — this is not the same as having no method on file.
          </Text>
        </CardBody>
      </Card>
    );
  }

  return (
    <Card testID="contraception-section">
      <CardHeader title="Your contraception" />
      <CardBody>
        {methods.length === 0 ? (
          <Text style={styles.subText}>
            Nothing visible right now. That may mean no method is recorded, or the record is not visible to you.
          </Text>
        ) : (
          methods.map((m) => (
            <View key={m.contraceptiveEpisodeId} style={{ marginBottom: 8 }}>
              <Text style={styles.value}>
                {(m.methodCode ?? m.methodClass ?? "Method").replace(/_/g, " ").toLowerCase()}
              </Text>
              <Text style={styles.subText}>
                Coverage: {(m.coverageStatus ?? "unknown").replace(/_/g, " ").toLowerCase()}
              </Text>
            </View>
          ))
        )}
      </CardBody>
    </Card>
  );
}

const INTENTION_CHOICES = [
  { code: "WANTS_PREGNANCY_NOW", label: "I want a pregnancy now" },
  { code: "WANTS_PREGNANCY_LATER", label: "I want one later" },
  { code: "WANTS_NO_MORE_CHILDREN", label: "No (more) children" },
  { code: "UNDECIDED", label: "Undecided" },
  { code: "DECLINED_TO_STATE", label: "I'd rather not say" },
] as const;

function IntentionCard({
  intention,
  unavailable,
  onRecorded,
}: {
  intention: ReproductiveIntention | null;
  unavailable: boolean;
  onRecorded: () => void;
}) {
  const [choice, setChoice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(false);

  if (unavailable) {
    return (
      <Card testID="intention-unavailable">
        <CardBody>
          <Text accessibilityRole="alert" style={styles.errorText}>
            Your reproductive intention could not be retrieved — this is not the same as having none
            on file.
          </Text>
        </CardBody>
      </Card>
    );
  }

  const save = async () => {
    if (!choice || saving) return;
    setSaving(true);
    setSaveError(false);
    try {
      await recordMyReproductiveIntention(choice);
      setChoice(null);
      onRecorded();
    } catch {
      setSaveError(true);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card testID="intention-section">
      <CardHeader title="Your reproductive intention" />
      <CardBody>
        <Text style={styles.subText}>
          What you want for your own fertility — only what you choose to record, never inferred.
        </Text>
        {intention ? (
          <Text style={styles.value} testID="intention-current">
            Currently recorded: {(intention.intention ?? "not stated").replace(/_/g, " ").toLowerCase()}
          </Text>
        ) : (
          <Text style={styles.subText}>
            Nothing visible right now — that may mean nothing is recorded, or that it is not visible
            to you here.
          </Text>
        )}

        <View style={styles.methodRow2}>
          {INTENTION_CHOICES.map((opt) => (
            <TouchableOpacity
              key={opt.code}
              style={[styles.methodChip, choice === opt.code && styles.methodChipActive]}
              onPress={() => setChoice(opt.code)}
              testID={`intention-choice-${opt.code}`}
            >
              <Text style={[styles.methodChipText, choice === opt.code && styles.methodChipTextActive]}>
                {opt.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {saveError ? (
          <Text accessibilityRole="alert" style={styles.errorText} testID="intention-save-error">
            We couldn&apos;t save this right now. Please try again in a moment.
          </Text>
        ) : null}

        <Button
          title={saving ? "Saving…" : "Save my intention"}
          variant="secondary"
          onPress={save}
          disabled={!choice || saving}
          loading={saving}
          testID="intention-save"
        />
      </CardBody>
    </Card>
  );
}

export function PregnancySection() {
  const [loading, setLoading] = useState(true);
  const [current, setCurrent] = useState<PregnancyEpisode | null>(null);
  const [history, setHistory] = useState<PregnancyEpisode[]>([]);
  const [contraception, setContraception] = useState<ContraceptionCoverage[]>([]);
  const [contraceptionUnavailable, setContraceptionUnavailable] = useState(false);
  const [intention, setIntention] = useState<ReproductiveIntention | null>(null);
  const [intentionUnavailable, setIntentionUnavailable] = useState(false);
  const [loadError, setLoadError] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    setContraceptionUnavailable(false);
    setIntentionUnavailable(false);
    try {
      const [currentEpisode, historyEpisodes, contraceptionRows, intentionRow] = await Promise.all([
        fetchCurrentPregnancy(),
        fetchPregnancyHistory(),
        fetchCurrentContraception().catch((err) => {
          if (err instanceof ApiError && err.code === "PCT_UNAVAILABLE") {
            setContraceptionUnavailable(true);
            return [] as ContraceptionCoverage[];
          }
          throw err;
        }),
        fetchMyReproductiveIntention().catch((err) => {
          if (err instanceof ApiError && err.code === "PCT_UNAVAILABLE") {
            setIntentionUnavailable(true);
            return null;
          }
          throw err;
        }),
      ]);
      setCurrent(currentEpisode);
      setHistory(historyEpisodes);
      setContraception(contraceptionRows);
      setIntention(intentionRow);
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

        <ContraceptionCard methods={contraception} unavailable={contraceptionUnavailable} />

        <IntentionCard intention={intention} unavailable={intentionUnavailable} onRecorded={load} />

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
  methodRow2: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginVertical: 10 },
  methodChip: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 14, backgroundColor: colors.gray[100] },
  methodChipActive: { backgroundColor: colors.ui.success.light },
  methodChipText: { fontSize: 12, color: colors.gray[500] },
  methodChipTextActive: { fontWeight: "700", color: "#059669" },
  historyRow: { flexDirection: "row", justifyContent: "space-between", paddingVertical: 6 },
});
