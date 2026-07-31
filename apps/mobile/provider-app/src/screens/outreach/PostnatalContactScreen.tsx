/**
 * PostnatalContactScreen — CHW community postnatal contact (Outreach mode).
 *
 * A day-3/week-6-style postnatal visit at home, in the community, or virtually — the contact the
 * WHO PNC schedule is worst at capturing, and until pct `V436` there was nowhere on this app to
 * record it. See docs/clinical/rmnp/chw-community-postnatal-mobile-contract.md §4/§8 for the
 * behaviours this screen exists to preserve; the short version, enforced below rather than left to
 * discipline:
 *
 *   1. "No danger signs" only renders behind an explicit, completed screen. Leaving the
 *      danger-sign screen unanswered (or answering "No" to "was it completed") disables the
 *      danger-sign finding entirely and shows "Not screened" — never a green all-clear.
 *   2. A referral carries its reason — required inline before this screen will submit one.
 *   3. Breastfeeding offers `NOT_ASSESSED` as its own answer, distinct from "not breastfeeding"
 *      and from leaving the question blank.
 *   4. `contactSetting` starts blank and is never defaulted to FACILITY; HOME, COMMUNITY and
 *      VIRTUAL are chosen, not assumed.
 *   5. One `clientOfflineId` per visit, generated once and reused across every retry of that same
 *      visit (including a PCT_UNAVAILABLE queue-and-sync) — see `postnatalContactService.ts`.
 *      "Record another visit" is the only action that mints a new one.
 */

import React, { useMemo, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  TextField,
  Select,
  Badge,
  ErrorState,
} from "@impilo/mobile-design-system";
import { generateId } from "@impilo/mobile-trust";
import { ApiError } from "@impilo/mobile-api-client";
import { useSyncEngine } from "@impilo/mobile-offline";
import {
  recordPostnatalContact,
  getPostnatalContacts,
  type PostnatalContactRecord,
  type PostnatalContactSetting,
  type PostnatalContactTiming,
  type BreastfeedingStatus,
} from "../../services/postnatalContactService";

const TIMING_OPTIONS: { value: PostnatalContactTiming; label: string }[] = [
  { value: "WITHIN_24H", label: "Within 24 hours" },
  { value: "DAY_3", label: "Day 3" },
  { value: "DAY_7_14", label: "Day 7–14" },
  { value: "WEEK_6", label: "Week 6" },
  { value: "ADDITIONAL", label: "Additional / unscheduled" },
];

// FACILITY is offered because the schema allows it, but this screen never pre-selects it — a CHW
// visiting at home, in the community, or virtually must choose one of those explicitly.
const SETTING_OPTIONS: { value: PostnatalContactSetting; label: string }[] = [
  { value: "HOME", label: "Home" },
  { value: "COMMUNITY", label: "Community" },
  { value: "VIRTUAL", label: "Virtual" },
  { value: "FACILITY", label: "Facility" },
];

const BREASTFEEDING_OPTIONS: { value: BreastfeedingStatus; label: string }[] = [
  { value: "EXCLUSIVE", label: "Exclusive breastfeeding" },
  { value: "PREDOMINANT", label: "Predominant breastfeeding" },
  { value: "PARTIAL", label: "Partial / mixed feeding" },
  { value: "NONE", label: "Not breastfeeding" },
  { value: "NOT_ASSESSED", label: "Not assessed — asked but could not establish" },
];

/** A three-state Yes/No control that can be retracted to "not answered" — the same law the near-miss
 * and partograph/CTG workspaces already carry: a blank is a different clinical statement than either
 * button, and clicking the selected button again must return to blank rather than doing nothing. */
function YesNoGate({
  label,
  hint,
  value,
  onChange,
  disabled,
  testIdBase,
}: {
  label: string;
  hint?: string;
  value: boolean | undefined;
  onChange: (v: boolean | undefined) => void;
  disabled?: boolean;
  testIdBase: string;
}) {
  return (
    <View style={styles.gate}>
      <Text style={[styles.gateLabel, disabled && styles.gateLabelDisabled]}>{label}</Text>
      {hint ? <Text style={styles.gateHint}>{hint}</Text> : null}
      <View style={styles.gateButtons}>
        <Button
          title="Yes"
          variant={value === true ? "primary" : "outline"}
          size="sm"
          disabled={disabled}
          onPress={() => onChange(value === true ? undefined : true)}
          testID={`${testIdBase}-yes`}
        />
        <Button
          title="No"
          variant={value === false ? "primary" : "outline"}
          size="sm"
          disabled={disabled}
          onPress={() => onChange(value === false ? undefined : false)}
          testID={`${testIdBase}-no`}
        />
        {value === undefined && !disabled && <Text style={styles.notAnswered}>not answered</Text>}
      </View>
    </View>
  );
}

function dangerSignsCopy(danger: boolean | null | undefined, screened: boolean | undefined): string {
  if (screened !== true) return "Not screened";
  if (danger === true) return "Danger signs present";
  if (danger === false) return "No danger signs found";
  return "Screened — no danger-sign answer recorded";
}

function breastfeedingCopy(status: string | null | undefined): string {
  const found = BREASTFEEDING_OPTIONS.find((o) => o.value === status);
  if (found) return found.label;
  return "Not recorded";
}

export function PostnatalContactScreen() {
  const { pendingCount } = useSyncEngine();

  const [clientOfflineId, setClientOfflineId] = useState(() => generateId());

  const [motherCpid, setMotherCpid] = useState("");
  const [contactTiming, setContactTiming] = useState("");
  const [contactSetting, setContactSetting] = useState("");
  const [daysSinceBirth, setDaysSinceBirth] = useState("");
  const [screeningComplete, setScreeningComplete] = useState<boolean | undefined>(undefined);
  const [dangerSignsPresent, setDangerSignsPresent] = useState<boolean | undefined>(undefined);
  const [breastfeedingStatus, setBreastfeedingStatus] = useState("");
  const [referralMade, setReferralMade] = useState<boolean | undefined>(undefined);
  const [referralReason, setReferralReason] = useState("");

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<{ message: string; correlationId?: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const [outcome, setOutcome] = useState<{ record?: PostnatalContactRecord; queued: boolean } | null>(null);

  const [historyMotherCpid, setHistoryMotherCpid] = useState("");
  const [history, setHistory] = useState<PostnatalContactRecord[] | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);

  function setScreened(v: boolean | undefined) {
    setScreeningComplete(v);
    // The danger-sign finding cannot outlive the screen it depends on — chk_pnc_screening_gate
    // refuses it at pct, and carrying a stale answer here would just make that a confusing
    // round-trip failure instead of an impossible state in the UI.
    if (v !== true) setDangerSignsPresent(undefined);
  }

  function handleReferralMadeChange(v: boolean | undefined) {
    setReferralMade(v);
    if (v !== true) setReferralReason("");
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!motherCpid.trim()) errors.motherCpid = "Required to anchor the visit to a mother.";
    if (!contactTiming) errors.contactTiming = "Choose where this visit falls in the PNC schedule.";
    if (!contactSetting) {
      errors.contactSetting =
        "Choose where this happened. HOME, COMMUNITY and VIRTUAL are real settings — this is never assumed.";
    }
    if (referralMade === true && !referralReason.trim()) {
      errors.referralReason = "A referral must carry its reason.";
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit() {
    setSubmitError(null);
    setOutcome(null);
    if (!validate()) return;

    setSaving(true);
    try {
      const result = await recordPostnatalContact({
        motherCpid: motherCpid.trim(),
        contactTiming: contactTiming as PostnatalContactTiming,
        contactSetting: contactSetting as PostnatalContactSetting,
        daysSinceBirth: daysSinceBirth.trim() ? Number(daysSinceBirth) : undefined,
        // Sent exactly as answered — `undefined` for "not answered", never coerced to false/NONE.
        screeningComplete,
        dangerSignsPresent: screeningComplete === true ? dangerSignsPresent : undefined,
        breastfeedingStatus: breastfeedingStatus ? (breastfeedingStatus as BreastfeedingStatus) : undefined,
        referralMade,
        referralReason: referralMade === true ? referralReason.trim() : undefined,
        clientOfflineId,
      });
      setOutcome(result);
    } catch (err) {
      if (err instanceof ApiError) {
        setSubmitError({ message: err.message, correlationId: err.correlationId });
      } else {
        setSubmitError({ message: err instanceof Error ? err.message : "Could not record this contact." });
      }
      // The clientOfflineId is deliberately NOT regenerated here — a 422 fix-and-resubmit and a
      // manual retry after a transport failure both need the same id so a later success is
      // recognised as this same visit, not a second one.
    } finally {
      setSaving(false);
    }
  }

  function handleStartNewVisit() {
    setClientOfflineId(generateId());
    setMotherCpid("");
    setContactTiming("");
    setContactSetting("");
    setDaysSinceBirth("");
    setScreeningComplete(undefined);
    setDangerSignsPresent(undefined);
    setBreastfeedingStatus("");
    setReferralMade(undefined);
    setReferralReason("");
    setFieldErrors({});
    setSubmitError(null);
    setOutcome(null);
  }

  async function handleLoadHistory() {
    if (!historyMotherCpid.trim()) return;
    setHistoryLoading(true);
    setHistoryError(null);
    setHistory(null);
    try {
      const contacts = await getPostnatalContacts(historyMotherCpid.trim());
      setHistory(contacts);
    } catch (err) {
      setHistoryError(err instanceof ApiError ? err.message : "Could not read postnatal contacts.");
    } finally {
      setHistoryLoading(false);
    }
  }

  const dangerGateDisabled = screeningComplete !== true;

  const outcomeBanner = useMemo(() => {
    if (!outcome) return null;
    if (outcome.queued) {
      return {
        variant: "warning" as const,
        title: "Saved offline",
        body: "This visit is captured on this device and will sync automatically. Do not re-enter it — it will resend with the same identifier, not create a second visit.",
      };
    }
    if (outcome.record?.replayed) {
      return {
        variant: "success" as const,
        title: "Already recorded",
        body: "This visit had already synced. Nothing new was created.",
      };
    }
    return { variant: "success" as const, title: "Contact recorded", body: "The postnatal visit was saved." };
  }, [outcome]);

  return (
    <Screen>
      <Header title="Postnatal Contacts" />
      <ScrollView testID="postnatal-contact-screen" style={styles.container}>
        {pendingCount > 0 && (
          <Badge variant="secondary" testID="postnatal-pending-badge">{`${pendingCount} pending sync`}</Badge>
        )}

        <Card style={styles.card}>
          <CardBody>
            <Text style={styles.sectionTitle}>Who and when</Text>
            <TextField
              label="Mother's Health ID or CPID"
              value={motherCpid}
              onChange={setMotherCpid}
              error={fieldErrors.motherCpid}
              testID="postnatal-mother-cpid"
            />
            <Select
              label="Contact timing"
              value={contactTiming}
              options={TIMING_OPTIONS}
              onChange={setContactTiming}
              placeholder="Choose the PNC schedule point"
              required
              error={fieldErrors.contactTiming}
              testID="postnatal-contact-timing"
            />
            <Select
              label="Where this happened"
              value={contactSetting}
              options={SETTING_OPTIONS}
              onChange={setContactSetting}
              placeholder="Choose a setting — never assumed"
              required
              error={fieldErrors.contactSetting}
              testID="postnatal-contact-setting"
            />
            <TextField
              label="Days since birth"
              value={daysSinceBirth}
              onChange={setDaysSinceBirth}
              keyboardType="numeric"
              testID="postnatal-days-since-birth"
            />
          </CardBody>
        </Card>

        <Card style={styles.card}>
          <CardBody>
            <Text style={styles.sectionTitle}>Danger-sign screen</Text>
            <YesNoGate
              label="Was the danger-sign screen completed?"
              value={screeningComplete}
              onChange={setScreened}
              testIdBase="postnatal-screening-complete"
            />
            <YesNoGate
              label="Any danger signs found?"
              hint={
                dangerGateDisabled
                  ? "Complete the screen above first — an unscreened contact cannot be marked clear."
                  : undefined
              }
              value={dangerSignsPresent}
              onChange={setDangerSignsPresent}
              disabled={dangerGateDisabled}
              testIdBase="postnatal-danger-signs"
            />
            <Text
              style={[
                styles.gateSummary,
                dangerGateDisabled ? styles.gateSummaryWarning : undefined,
              ]}
              testID="postnatal-danger-signs-summary"
            >
              {dangerSignsCopy(dangerSignsPresent, screeningComplete)}
            </Text>
          </CardBody>
        </Card>

        <Card style={styles.card}>
          <CardBody>
            <Text style={styles.sectionTitle}>Breastfeeding</Text>
            <Select
              label="Breastfeeding status"
              value={breastfeedingStatus}
              options={BREASTFEEDING_OPTIONS}
              onChange={setBreastfeedingStatus}
              placeholder="Not recorded — distinct from 'not breastfeeding'"
              testID="postnatal-breastfeeding-status"
            />
          </CardBody>
        </Card>

        <Card style={styles.card}>
          <CardBody>
            <Text style={styles.sectionTitle}>Referral</Text>
            <YesNoGate
              label="Was a referral made?"
              value={referralMade}
              onChange={handleReferralMadeChange}
              testIdBase="postnatal-referral-made"
            />
            {referralMade === true && (
              <TextField
                label="Referral reason"
                value={referralReason}
                onChange={setReferralReason}
                required
                multiline
                error={fieldErrors.referralReason}
                placeholder="Why she is being sent onward"
                testID="postnatal-referral-reason"
              />
            )}
          </CardBody>
        </Card>

        <Button
          title="Record contact"
          onPress={handleSubmit}
          loading={saving}
          fullWidth
          testID="postnatal-submit"
        />
        <Button
          title="Record another visit"
          variant="outline"
          onPress={handleStartNewVisit}
          fullWidth
          testID="postnatal-start-new-visit"
        />

        {submitError && (
          <ErrorState
            title="Could not record this contact"
            message={submitError.message}
            correlationId={submitError.correlationId}
            onRetry={handleSubmit}
            testID="postnatal-submit-error"
          />
        )}

        {outcomeBanner && (
          <View
            style={[
              styles.banner,
              outcomeBanner.variant === "warning" ? styles.bannerWarning : styles.bannerSuccess,
            ]}
            testID="postnatal-outcome-banner"
          >
            <Text style={styles.bannerTitle}>{outcomeBanner.title}</Text>
            <Text style={styles.bannerBody}>{outcomeBanner.body}</Text>
          </View>
        )}

        <Card style={styles.card}>
          <CardBody>
            <Text style={styles.sectionTitle}>Previous contacts for this mother</Text>
            <TextField
              label="Mother's Health ID or CPID"
              value={historyMotherCpid}
              onChange={setHistoryMotherCpid}
              testID="postnatal-history-mother-cpid"
            />
            <Button
              title="Load history"
              onPress={handleLoadHistory}
              loading={historyLoading}
              variant="outline"
              testID="postnatal-load-history"
            />
            {historyError && <ErrorState message={historyError} testID="postnatal-history-error" />}
            {history && history.length === 0 && (
              <Text style={styles.historyEmpty} testID="postnatal-history-empty">
                No contacts are shown here. This is not proof she has had no postnatal contact — a
                withheld record and an absent one look the same on purpose.
              </Text>
            )}
            {history && history.length > 0 && (
              <View testID="postnatal-history-list">
                {history.map((c) => (
                  <View key={c.postnatal_contact_id} style={styles.historyItem} testID={`postnatal-history-item-${c.postnatal_contact_id}`}>
                    <Text style={styles.historyLine}>
                      {TIMING_OPTIONS.find((t) => t.value === c.contact_timing)?.label ?? c.contact_timing} ·{" "}
                      {c.contact_setting} · {c.contacted_at ?? "unknown date"}
                    </Text>
                    <Text style={styles.historyLine}>{dangerSignsCopy(c.danger_signs_present, c.screening_complete ?? undefined)}</Text>
                    <Text style={styles.historyLine}>{breastfeedingCopy(c.breastfeeding_status)}</Text>
                    {c.referral_made && (
                      <Text style={styles.historyLine}>Referred: {c.referral_reason ?? "no reason recorded"}</Text>
                    )}
                  </View>
                ))}
              </View>
            )}
          </CardBody>
        </Card>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16 },
  card: { marginBottom: 12 },
  sectionTitle: { fontSize: 14, fontWeight: "700", color: "#212121", marginBottom: 8 },
  gate: { marginBottom: 12 },
  gateLabel: { fontSize: 14, fontWeight: "600", color: "#212121" },
  gateLabelDisabled: { color: "#9E9E9E" },
  gateHint: { fontSize: 12, color: "#B45309", marginTop: 2 },
  gateButtons: { flexDirection: "row", gap: 8, marginTop: 6, alignItems: "center" },
  notAnswered: { fontSize: 12, color: "#9E9E9E", marginLeft: 4 },
  gateSummary: { fontSize: 13, fontWeight: "600", color: "#374151", marginTop: 4 },
  gateSummaryWarning: { color: "#B45309" },
  banner: { borderRadius: 10, padding: 12, marginTop: 12, borderWidth: 1 },
  bannerWarning: { backgroundColor: "#FEF3C7", borderColor: "#F59E0B" },
  bannerSuccess: { backgroundColor: "#D1FAE5", borderColor: "#22C55E" },
  bannerTitle: { fontSize: 14, fontWeight: "700", color: "#212121" },
  bannerBody: { fontSize: 12, color: "#374151", marginTop: 4 },
  historyEmpty: { fontSize: 12, color: "#6B7280", marginTop: 8 },
  historyItem: { borderTopWidth: 1, borderColor: "#E5E7EB", paddingVertical: 8, marginTop: 4 },
  historyLine: { fontSize: 12, color: "#374151" },
});
