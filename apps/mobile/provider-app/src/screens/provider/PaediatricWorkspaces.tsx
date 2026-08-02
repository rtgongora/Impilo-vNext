/**
 * Paediatric decision support on a phone — IMNCI classification, the EPI immunisation forecast,
 * growth interpretation and the danger-sign screen.
 *
 * The app had no paediatric surface at all: the four engines were deployed, live-proven and
 * reachable only by curl, and a nurse doing under-five care in a rural clinic — which is where
 * most under-five care in Zimbabwe happens, and where the phone is the only device — could reach
 * none of them.
 *
 * **Parity means the same clinical safety properties, not the same layout.** Nothing here
 * evaluates anything; every judgement comes from the same governed content the web shell calls,
 * through the same BFF routes. The four properties that must survive onto a small screen, where
 * the temptation to compress is strongest:
 *
 *   1. An unresolved classification reads as unresolved, naming the observation it needs. It is
 *      never the green row, and on a phone it also sorts above the treat-here rows so it is not
 *      pushed below the fold.
 *   2. A provisional classification reads as provisional.
 *   3. A dose that would not immunise the child never appears in the give-now list.
 *      BLOCKED_BY_INTERVAL carries the date the gate opens; AGED_OUT carries the safety reason.
 *   4. When growth interpretation is blocked the clinical signals are suppressed, exactly as the
 *      engine suppresses them.
 *
 * And the one that governs all four: an unreachable engine is never rendered as a well child. A
 * 502 produces "could not ask", never an empty list.
 */

import React, { useState } from "react";
import { View, Text, TextInput, ScrollView, Pressable, StyleSheet, ActivityIndicator } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";
import { colors } from "@impilo/mobile-design-system";
import { getPatient } from "../../services/patientService";
import {
  classifyImnci,
  evaluateDangerSigns,
  forecastImmunisation,
  giveableToday,
  interpretGrowth,
  isEngineUnavailable,
  unassessedSigns,
  withheldToday,
  ageDaysFromDateOfBirth,
  UNDER_FIVE_MAX_AGE_DAYS,
  YOUNG_INFANT_MAX_AGE_DAYS,
  type DangerSignAssessment,
  type ForecastDose,
  type GrowthInterpretation,
  type ImnciAssessment,
  type ImnciOutcome,
  type ImmunisationForecast,
  type RecordedDose,
} from "../../services/paediatricService";

export interface PaediatricWorkspacesProps {
  patientId: string;
  ageDays: number | null;
  dateOfBirth: string | null;
  sex?: string | null;
  /** Findings recorded at this contact, keyed as the engines name them. Absent = not assessed. */
  findings?: Record<string, unknown>;
  /** Doses already on the record. PCT owns them; the forecast engine is stateless. */
  recordedDoses?: RecordedDose[];
  /** Growth measurements carrying the z-scores PCT stamped at the time. */
  growthMeasurements?: Array<Record<string, unknown>>;
}

type Tab = "danger" | "imnci" | "immunisation" | "growth";

const TABS: Array<{ key: Tab; label: string }> = [
  { key: "danger", label: "Danger signs" },
  { key: "imnci", label: "Classify" },
  { key: "immunisation", label: "Immunisation" },
  { key: "growth", label: "Growth" },
];

export function PaediatricWorkspaces({
  patientId,
  ageDays,
  dateOfBirth,
  sex,
  findings = {},
  recordedDoses = [],
  growthMeasurements = [],
}: PaediatricWorkspacesProps) {
  const [tab, setTab] = useState<Tab>("danger");

  if (ageDays === null) {
    return (
      <Card tone="blocked" testID="paed-no-age">
        <Text style={styles.cardTitle}>No date of birth on the record</Text>
        <Text style={styles.body}>
          Every paediatric engine here is age-routed — IMNCI between the young-infant and child
          charts, the danger-sign rules by age band, and every gate in the immunisation schedule.
          Without a date of birth none of them can run.
        </Text>
      </Card>
    );
  }

  if (ageDays > UNDER_FIVE_MAX_AGE_DAYS) {
    return (
      <Card tone="plain" testID="paed-out-of-range">
        <Text style={styles.cardTitle}>Outside under-five care</Text>
        <Text style={styles.body}>
          These charts cover birth to five years. This person is {Math.floor(ageDays / 365)} years
          old, so nothing here has been run.
        </Text>
      </Card>
    );
  }

  return (
    <View style={styles.root} testID="paediatric-workspaces">
      <View style={styles.chartBanner} testID="paed-chart-banner">
        <Text style={styles.cardTitle}>
          {ageDays <= YOUNG_INFANT_MAX_AGE_DAYS ? "Sick young infant chart" : "Sick child chart"}
        </Text>
        <Text style={styles.hint}>
          {ageDays} days old.{" "}
          {ageDays <= YOUNG_INFANT_MAX_AGE_DAYS
            ? "Under two months — this chart looks for possible serious bacterial infection, which the child chart does not."
            : "Two months or older."}
        </Text>
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.tabRow}>
        {TABS.map((t) => (
          <Pressable
            key={t.key}
            onPress={() => setTab(t.key)}
            style={[styles.tab, tab === t.key && styles.tabActive]}
            testID={`paed-tab-${t.key}`}
            accessibilityRole="tab"
            accessibilityState={{ selected: tab === t.key }}
          >
            <Text style={[styles.tabText, tab === t.key && styles.tabTextActive]}>{t.label}</Text>
          </Pressable>
        ))}
      </ScrollView>

      {tab === "danger" && (
        <DangerSignsTab patientId={patientId} ageDays={ageDays} findings={findings} />
      )}
      {tab === "imnci" && <ImnciTab patientId={patientId} ageDays={ageDays} findings={findings} />}
      {tab === "immunisation" && (
        <ImmunisationTab
          patientId={patientId}
          dateOfBirth={dateOfBirth}
          sex={sex}
          doses={recordedDoses}
        />
      )}
      {tab === "growth" && <GrowthTab patientId={patientId} measurements={growthMeasurements} />}
    </View>
  );
}

// --- danger signs ---------------------------------------------------------------------------

function DangerSignsTab({
  patientId,
  ageDays,
  findings,
}: {
  patientId: string;
  ageDays: number;
  findings: Record<string, unknown>;
}) {
  const query = useQuery<DangerSignAssessment>({
    queryKey: ["paed-danger", patientId, ageDays, JSON.stringify(findings)],
    queryFn: () => evaluateDangerSigns(ageDays, findings),
    retry: 1,
  });

  if (query.isLoading) return <Loading label="Evaluating the danger-sign rules…" />;
  if (query.isError) {
    return (
      <Unavailable
        testID="paed-danger-unavailable"
        title="Danger-sign evaluation unavailable"
        body={
          isEngineUnavailable(query.error)
            ? "The rules could not be reached. No sign was checked, which is not the same as no sign being present. Check the emergency and general danger signs directly."
            : "The danger-sign evaluation failed. No sign was checked."
        }
        onRetry={() => query.refetch()}
      />
    );
  }

  const a = query.data!;
  const alerts = a.alerts ?? [];
  const unassessed = unassessedSigns(a);

  return (
    <View testID="paed-danger">
      {alerts.map((alert) => {
        const id = alert.rule_id ?? alert.code ?? "";
        return (
          <Card tone="critical" key={id} testID={`paed-danger-alert-${id}`}>
            <Text style={styles.cardTitle}>{alert.name ?? id}</Text>
            {(alert.required_action ?? alert.action) && (
              <Text style={styles.bodyStrong}>{alert.required_action ?? alert.action}</Text>
            )}
            {alert.overridable === false && (
              <Text style={styles.criticalNote}>This alert cannot be overridden.</Text>
            )}
          </Card>
        );
      })}

      {/* Rendered whether or not alerts fired, and never replaced by an all-clear. A screen that
          raised nothing is only reassurance if the questions were asked. */}
      {a.incomplete ? (
        <Card tone="blocked" testID="paed-danger-incomplete">
          <Text style={styles.cardTitle}>
            {alerts.length > 0 ? "Screen incomplete" : "No danger sign raised — but the screen is incomplete"}
          </Text>
          <Text style={styles.body}>
            {unassessed.length > 0
              ? `${unassessed.length} sign${unassessed.length === 1 ? " was" : "s were"} not assessed: ${unassessed.join(", ")}.`
              : "Some signs were not assessed."}
          </Text>
          <Text style={styles.body}>
            {alerts.length > 0
              ? "Completing these may raise further alerts."
              : "This is not an all-clear. A danger sign nobody looked for has not been ruled out."}
          </Text>
        </Card>
      ) : (
        alerts.length === 0 && (
          <Card tone="well" testID="paed-danger-clear">
            <Text style={styles.cardTitle}>No danger sign present</Text>
            <Text style={styles.body}>
              Every danger sign in the rule set was assessed and none is present.
            </Text>
          </Card>
        )
      )}
    </View>
  );
}

// --- IMNCI ---------------------------------------------------------------------------------

/** Unresolved before colour: the status is the authority on whether a conclusion was reached. */
export function imnciTone(o: ImnciOutcome): "critical" | "treat" | "well" | "unresolved" | "plain" {
  if (o.status === "INDETERMINATE") return "unresolved";
  if (o.status === "NOT_APPLICABLE") return "plain";
  if (!o.classification) return "unresolved";
  switch ((o.colour ?? "").toUpperCase()) {
    case "PINK":
      return "critical";
    case "YELLOW":
      return "treat";
    case "GREEN":
      return "well";
    default:
      return "unresolved";
  }
}

/**
 * Critical first, then unresolved — above treat-here, because an unresolved row carries an
 * outstanding action on the clinician and a phone screen buries whatever is below the fold.
 */
export function imnciRank(o: ImnciOutcome): number {
  const tone = imnciTone(o);
  return tone === "critical" ? 0 : tone === "unresolved" ? 1 : tone === "treat" ? 2 : tone === "well" ? 3 : 4;
}

function ImnciTab({
  patientId,
  ageDays,
  findings,
}: {
  patientId: string;
  ageDays: number;
  findings: Record<string, unknown>;
}) {
  const query = useQuery<ImnciAssessment>({
    queryKey: ["paed-imnci", patientId, ageDays, JSON.stringify(findings)],
    queryFn: () => classifyImnci(ageDays, findings),
    retry: 1,
  });

  if (query.isLoading) return <Loading label="Running the assess-and-classify tables…" />;
  if (query.isError) {
    return (
      <Unavailable
        testID="paed-imnci-unavailable"
        title="IMNCI classification unavailable"
        body="The classification tables could not be run. This is not a classification of no illness — no chart was run at all. Assess and classify on paper."
        onRetry={() => query.refetch()}
      />
    );
  }

  const a = query.data!;
  const rows = [...a.classifications].sort((x, y) => imnciRank(x) - imnciRank(y));
  const outstanding = a.outstanding_assessments ?? [];

  return (
    <View testID="paed-imnci">
      {a.urgent_referral_required ? (
        <Card tone="critical" testID="paed-imnci-headline">
          <Text style={styles.cardTitle}>Urgent referral required</Text>
          {a.disposition ? <Text style={styles.bodyStrong}>{a.disposition}</Text> : null}
        </Card>
      ) : a.incomplete ? (
        // An incomplete assessment never summarises as a disposition: the disposition was computed
        // over a partial picture.
        <Card tone="blocked" testID="paed-imnci-headline">
          <Text style={styles.cardTitle}>Assessment incomplete</Text>
          <Text style={styles.body}>
            {outstanding.length > 0
              ? `Record ${outstanding.join(", ")} to complete this assessment. The rows below are what could be reached without them.`
              : "Some charts could not be classified from what has been recorded."}
          </Text>
        </Card>
      ) : (
        <Card tone="well" testID="paed-imnci-headline">
          <Text style={styles.cardTitle}>{a.disposition ?? "Assessment complete"}</Text>
        </Card>
      )}

      {rows.map((o) => {
        const tone = imnciTone(o);
        const missing = o.missing_inputs ?? [];
        return (
          <Card tone={tone} key={o.table_id} testID={`paed-imnci-row-${o.table_id}`}>
            <Text style={styles.cardTitle}>
              {tone === "unresolved"
                ? `${o.axis ?? o.table_id} — not classified`
                : (o.classification_name ?? o.classification ?? o.axis ?? o.table_id)}
            </Text>

            {tone === "unresolved" ? (
              <Text style={styles.body}>
                {missing.length > 0
                  ? `Cannot be classified until ${missing.join(", ")} ${missing.length === 1 ? "is" : "are"} recorded. It is not a finding that the child is well.`
                  : "Could not be classified. It is not a finding that the child is well."}
              </Text>
            ) : (
              <>
                {o.colour ? (
                  <Text style={styles.colourLabel} testID={`paed-imnci-colour-${o.table_id}`}>
                    {o.colour.toUpperCase() === "PINK"
                      ? "Pink — urgent referral"
                      : o.colour.toUpperCase() === "YELLOW"
                        ? "Yellow — treat at this facility"
                        : "Green — home care and advice"}
                  </Text>
                ) : null}
                {o.provisional && (
                  <Text style={styles.provisional} testID={`paed-imnci-provisional-${o.table_id}`}>
                    Provisional — a more severe classification on this chart could not be excluded.
                    Treat this as the least this child has, not the most.
                  </Text>
                )}
                {o.action ? <Text style={styles.body}>{o.action}</Text> : null}
              </>
            )}
          </Card>
        );
      })}

      <Text style={styles.provenance}>
        {a.content_source ?? "IMNCI classification tables"}
        {a.content_version ? ` · ${a.content_version}` : ""} · a classification is a management
        category, not a diagnosis.
      </Text>
    </View>
  );
}

// --- immunisation --------------------------------------------------------------------------

function ImmunisationTab({
  patientId,
  dateOfBirth,
  sex,
  doses,
}: {
  patientId: string;
  dateOfBirth: string | null;
  sex?: string | null;
  doses: RecordedDose[];
}) {
  const query = useQuery<ImmunisationForecast>({
    queryKey: ["paed-forecast", patientId, dateOfBirth, doses.length],
    queryFn: () => forecastImmunisation({ dateOfBirth: dateOfBirth!, sex, doses }),
    enabled: !!dateOfBirth,
    retry: 1,
  });

  if (!dateOfBirth) {
    return (
      <Card tone="blocked" testID="paed-forecast-no-dob">
        <Text style={styles.cardTitle}>No date of birth</Text>
        <Text style={styles.body}>
          Every gate in the schedule — the minimum age for a dose, the interval since the last one,
          and the age a window closes — is a function of age. No dose can be forecast.
        </Text>
      </Card>
    );
  }

  if (query.isLoading) return <Loading label="Running the national schedule…" />;
  if (query.isError) {
    return (
      <Unavailable
        testID="paed-forecast-unavailable"
        title="Immunisation forecast unavailable"
        body="The schedule could not be run. Do not read this as the child being up to date — no dose was evaluated. Check the child health card."
        onRetry={() => query.refetch()}
      />
    );
  }

  const f = query.data!;
  const give = giveableToday(f);
  const withheld = withheldToday(f);

  return (
    <View testID="paed-forecast">
      <Card tone={give.length > 0 ? "well" : "plain"} testID="paed-forecast-headline">
        <Text style={styles.cardTitle}>
          {give.length > 0
            ? `${give.length} dose${give.length === 1 ? "" : "s"} to give now`
            : "No dose can be given at this contact"}
        </Text>
        {give.length === 0 && (
          <Text style={styles.body}>
            This is the schedule&apos;s answer for today, not a statement that this child&apos;s
            immunisation is complete.
          </Text>
        )}
        {f.provisional && (
          <Text style={styles.provisional} testID="paed-forecast-provisional">
            Provisional forecast
            {(f.provisional_reasons ?? []).length > 0
              ? `: ${(f.provisional_reasons ?? []).join("; ")}`
              : "."}
          </Text>
        )}
      </Card>

      {give.map((d) => (
        <DoseCard key={`${d.series}-${d.dose_number}`} dose={d} tone="well" />
      ))}

      {withheld.length > 0 && (
        <>
          <Text style={styles.sectionTitle}>Not giveable today</Text>
          {withheld.map((d) => (
            <DoseCard key={`${d.series}-${d.dose_number}`} dose={d} tone="blocked" />
          ))}
        </>
      )}
    </View>
  );
}

function DoseCard({ dose, tone }: { dose: ForecastDose; tone: "well" | "blocked" }) {
  const name = `${dose.antigen ?? dose.series}${dose.dose_number !== null ? ` dose ${dose.dose_number}` : ""}`;
  return (
    <Card
      tone={dose.status === "OVERDUE" ? "critical" : tone}
      testID={`paed-dose-${dose.series}-${dose.dose_number}`}
    >
      <Text style={styles.cardTitle}>{name}</Text>
      <Text style={styles.body}>{doseDetail(dose)}</Text>
    </Card>
  );
}

/** The reason, always. "Not due" with no reason is what brings a child back tomorrow. */
export function doseDetail(dose: ForecastDose): string {
  switch (dose.status) {
    case "OVERDUE":
      return `Overdue${dose.days_overdue !== null ? ` by ${dose.days_overdue} day${dose.days_overdue === 1 ? "" : "s"}` : ""} — give at this contact.`;
    case "DUE":
      return "Due now — give at this contact.";
    case "BLOCKED_BY_INTERVAL":
      return dose.earliest_date
        ? `Old enough, but the minimum interval since the previous dose has not elapsed — giving it today would not immunise them. Due from ${dose.earliest_date}.`
        : "The minimum interval since the previous dose has not elapsed. Giving it today would not immunise them.";
    case "AGED_OUT":
      return (
        dose.rationale ??
        `The window closed${dose.latest_useful_date ? ` after ${dose.latest_useful_date}` : ""} and this dose will not be given. This is a safety limit, not a scheduling one.`
      );
    case "AWAITING_PREVIOUS_DOSE":
      return dose.rationale ?? "The previous dose in this series has not been given.";
    case "INVALID_REPEAT_REQUIRED":
      return dose.rationale ?? "Given before it could immunise the child; it does not count and must be repeated.";
    default:
      return dose.rationale ?? dose.status.replace(/_/g, " ").toLowerCase();
  }
}

// --- growth --------------------------------------------------------------------------------

function GrowthTab({
  patientId,
  measurements,
}: {
  patientId: string;
  measurements: Array<Record<string, unknown>>;
}) {
  const query = useQuery<GrowthInterpretation>({
    queryKey: ["paed-growth", patientId, measurements.length],
    queryFn: () => interpretGrowth(measurements),
    retry: 1,
  });

  if (query.isLoading) return <Loading label="Interpreting the growth series…" />;
  if (query.isError) {
    return (
      <Unavailable
        testID="paed-growth-unavailable"
        title="Growth interpretation unavailable"
        body="The growth series could not be interpreted. An absence of signals is not a finding that this child is growing normally — nothing was assessed."
        onRetry={() => query.refetch()}
      />
    );
  }

  const g = query.data!;

  // Checked before every branch that would describe a clinical reading. When the interpretation is
  // blocked there is no clinical reading to describe.
  if (g.interpretation_blocked) {
    return (
      <Card tone="blocked" testID="paed-growth-blocked">
        <Text style={styles.cardTitle}>Growth not interpreted — measurement in question</Text>
        <Text style={styles.body}>
          {g.not_assessable_reason ??
            "One of these measurements is implausible or was taken differently from the others, so the growth signals have been withheld."}
        </Text>
        <Text style={styles.body}>
          The clinical signals are deliberately not shown. Check the measurement and correct or
          repeat it before this child&apos;s growth is interpreted.
        </Text>
      </Card>
    );
  }

  if (!g.assessable) {
    return (
      <Card tone="blocked" testID="paed-growth-not-assessable">
        <Text style={styles.cardTitle}>Growth not yet assessable</Text>
        <Text style={styles.body}>
          {g.not_assessable_reason ??
            "Two measurements are the minimum. A child weighed once has not been shown to be growing well."}
        </Text>
      </Card>
    );
  }

  if (g.signals.length === 0) {
    return (
      <Card tone={g.z_trend_derivable ? "well" : "blocked"} testID="paed-growth-none">
        <Text style={styles.cardTitle}>No growth concern detected</Text>
        <Text style={styles.body}>
          {g.z_trend_derivable
            ? `Compared across ${g.contacts_considered ?? 0} contacts, no faltering or excessive gain was detected.`
            : "No signal was raised, but the trend between contacts could not be derived, so this is not a comparison that found nothing."}
        </Text>
      </Card>
    );
  }

  return (
    <View testID="paed-growth">
      {g.signals.map((s) => (
        <Card
          tone={s.referral_required || (s.severity ?? "").toUpperCase() === "SEVERE" ? "critical" : "treat"}
          key={s.code}
          testID={`paed-growth-signal-${s.code}`}
        >
          <Text style={styles.cardTitle}>{s.name ?? s.code}</Text>
          {s.action ? <Text style={styles.bodyStrong}>{s.action}</Text> : null}
          {s.rationale ? <Text style={styles.body}>{s.rationale}</Text> : null}
        </Card>
      ))}
    </View>
  );
}

// --- shared presentation -------------------------------------------------------------------

type Tone = "critical" | "treat" | "well" | "unresolved" | "blocked" | "plain";

const TONE_STYLE: Record<Tone, { bg: string; border: string }> = {
  critical: { bg: colors.ui.error.light, border: colors.ui.error.main },
  treat: { bg: colors.ui.warning.light, border: colors.ui.warning.main },
  well: { bg: colors.ui.success.light, border: colors.ui.success.main },
  // Unresolved and blocked share a deliberately non-green, non-quiet neutral. On a phone the
  // temptation is to make "nothing to report" disappear; an outstanding job must not.
  unresolved: { bg: colors.gray[100], border: colors.gray[500] },
  blocked: { bg: colors.gray[100], border: colors.gray[500] },
  plain: { bg: "#FFFFFF", border: colors.gray[200] },
};

function Card({
  children,
  tone,
  testID,
}: {
  children: React.ReactNode;
  tone: Tone;
  testID?: string;
}) {
  const t = TONE_STYLE[tone];
  return (
    <View
      style={[styles.card, { backgroundColor: t.bg, borderColor: t.border }]}
      testID={testID}
      accessibilityLabel={testID}
    >
      {children}
    </View>
  );
}

function Loading({ label }: { label: string }) {
  return (
    <View style={styles.loading} testID="paed-loading">
      <ActivityIndicator />
      <Text style={styles.hint}>{label}</Text>
    </View>
  );
}

function Unavailable({
  title,
  body,
  onRetry,
  testID,
}: {
  title: string;
  body: string;
  onRetry: () => void;
  testID: string;
}) {
  return (
    <Card tone="critical" testID={testID}>
      <Text style={styles.cardTitle}>{title}</Text>
      <Text style={styles.body}>{body}</Text>
      <Pressable onPress={onRetry} style={styles.retry} accessibilityRole="button">
        <Text style={styles.retryText}>Try again</Text>
      </Pressable>
    </Card>
  );
}


// --- the registry-facing surface -----------------------------------------------------------

/**
 * The surface `specialtyToolRegistry` points at.
 *
 * Resolves the child, their recorded doses and their growth series, then hands them to
 * `PaediatricWorkspaces`. Nothing is fetched for the engines to evaluate that the engines could
 * evaluate themselves — the doses and the stamped z-scores are clinical record owned by
 * pct-service, and the engines are stateless by design so that the same evaluation runs unchanged
 * against an offline copy.
 *
 * Each source degrades on its own. In particular the forecast is held back until the dose history
 * has actually been read: a forecast over an unread history would report the child as due for
 * doses they have already had, which is a confident wrong answer rather than an honest gap.
 */
export function PaediatricWorkspace() {
  const [patientIdInput, setPatientIdInput] = useState("");
  const patientId = patientIdInput.trim();

  const patient = useQuery({
    queryKey: ["paed-patient", patientId],
    queryFn: () => getPatient(patientId),
    enabled: patientId.length > 0,
  });

  const immunisations = useQuery({
    queryKey: ["paed-immunisations", patientId],
    queryFn: async () => {
      const r = await apiClient.get<{ data: Array<{ attributes: Record<string, unknown> }> }>(
        `/internal/v1/immunizations?patient_id=${encodeURIComponent(patientId)}`,
      );
      return (r.data?.data ?? []).map((e) => ({
        vaccine_code:
          (e.attributes?.vaccineCode as string | null) ?? (e.attributes?.vaccineName as string | null) ?? null,
        dose_number: (e.attributes?.doseNumber as number | null) ?? null,
        occurrence_at: (e.attributes?.administeredAt as string | null) ?? null,
        status: (e.attributes?.status as string | null) ?? null,
      })) as RecordedDose[];
    },
    enabled: patientId.length > 0,
  });

  const growth = useQuery({
    queryKey: ["paed-growth-series", patientId],
    queryFn: async () => {
      const r = await apiClient.get<{ data: Array<{ attributes: Record<string, any> }> }>(
        `/internal/v1/growth?patient_id=${encodeURIComponent(patientId)}`,
      );
      return (r.data?.data ?? [])
        .map((e: { attributes?: Record<string, any> }) => e.attributes ?? {})
        .sort(
          (a: Record<string, any>, b: Record<string, any>) =>
            new Date(String(a.measured_at ?? 0)).getTime() - new Date(String(b.measured_at ?? 0)).getTime(),
        )
        .map((a: Record<string, any>) => ({
          age_days: a.derived?.age_days ?? null,
          weight_kg: a.weight_kg ?? null,
          weight_for_age_z: a.derived?.weight_for_age?.z_score ?? null,
          length_height_for_age_z: a.derived?.length_height_for_age?.z_score ?? null,
          head_circumference_for_age_z: a.derived?.head_circumference_for_age?.z_score ?? null,
          measurement_mode: a.derived?.normalized_stature_mode ?? a.measurement_mode ?? null,
          standard: a.derived?.standard ?? null,
          postmenstrual_age_weeks: a.derived?.postmenstrual_age_weeks ?? null,
        }));
    },
    enabled: patientId.length > 0,
  });

  return (
    <ScrollView testID="paediatric-workspace">
      <View style={styles.root}>
        <Text style={styles.label}>Patient ID</Text>
        <TextInput
          value={patientIdInput}
          onChangeText={setPatientIdInput}
          placeholder="CPID"
          autoCapitalize="none"
          style={styles.input}
          testID="paed-patient-input"
        />
      </View>

      {patientId.length === 0 ? (
        <View style={styles.root}>
          <Text style={styles.hint}>Enter a patient ID to run the paediatric charts.</Text>
        </View>
      ) : patient.isLoading ? (
        <Loading label="Loading the child's record…" />
      ) : patient.isError ? (
        <View style={styles.root}>
          <Card tone="critical" testID="paed-patient-unavailable">
            <Text style={styles.cardTitle}>Patient record unavailable</Text>
            <Text style={styles.body}>
              The child&apos;s record could not be read, so their age is unknown and no chart can be
              selected. Nothing has been assessed.
            </Text>
          </Card>
        </View>
      ) : (
        <>
          {immunisations.isError && (
            <View style={styles.root}>
              <Card tone="blocked" testID="paed-doses-unavailable">
                <Text style={styles.cardTitle}>Dose history unreadable</Text>
                <Text style={styles.body}>
                  The doses already given could not be read, so the immunisation tab is running over
                  an empty history and may report doses this child has already had. Check the child
                  health card before giving anything.
                </Text>
              </Card>
            </View>
          )}
          <PaediatricWorkspaces
            patientId={patientId}
            ageDays={ageDaysFromDateOfBirth(patient.data?.dateOfBirth ?? null)}
            dateOfBirth={patient.data?.dateOfBirth ?? null}
            sex={patient.data?.sex ?? null}
            recordedDoses={immunisations.data ?? []}
            growthMeasurements={growth.data ?? []}
          />
        </>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { padding: 12 },
  chartBanner: {
    borderWidth: 1,
    borderColor: colors.gray[200],
    borderRadius: 10,
    padding: 12,
    marginBottom: 10,
    backgroundColor: "#FFFFFF",
  },
  tabRow: { flexDirection: "row", marginBottom: 10 },
  tab: {
    minHeight: 44,
    justifyContent: "center",
    paddingHorizontal: 14,
    borderRadius: 8,
    marginRight: 8,
    backgroundColor: colors.gray[100],
  },
  tabActive: { backgroundColor: colors.gray[900] },
  tabText: { fontSize: 13, fontWeight: "600", color: colors.gray[700] },
  tabTextActive: { color: "#FFFFFF" },
  card: { borderWidth: 1, borderRadius: 10, padding: 12, marginBottom: 10 },
  cardTitle: { fontSize: 14, fontWeight: "700", color: colors.gray[900], marginBottom: 4 },
  body: { fontSize: 13, color: colors.gray[800], marginTop: 2 },
  bodyStrong: { fontSize: 13, fontWeight: "600", color: colors.gray[900], marginTop: 2 },
  colourLabel: { fontSize: 12, fontWeight: "700", color: colors.gray[900], marginTop: 2 },
  provisional: { fontSize: 12, color: colors.ui.warning.text, marginTop: 4 },
  criticalNote: { fontSize: 12, fontWeight: "700", color: colors.ui.error.text, marginTop: 4 },
  sectionTitle: {
    fontSize: 12,
    fontWeight: "700",
    color: colors.gray[600],
    textTransform: "uppercase",
    marginTop: 6,
    marginBottom: 6,
  },
  provenance: { fontSize: 11, color: colors.gray[500], marginTop: 6 },
  hint: { fontSize: 12, color: colors.gray[500], marginTop: 6 },
  loading: { padding: 20, alignItems: "center" },
  retry: {
    minHeight: 44,
    justifyContent: "center",
    alignItems: "center",
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.ui.error.main,
    marginTop: 10,
  },
  retryText: { fontSize: 13, fontWeight: "600", color: colors.ui.error.text },
  label: { fontSize: 12, color: colors.gray[700], marginBottom: 3 },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    paddingHorizontal: 10,
    minHeight: 44,
    fontSize: 14,
    color: colors.gray[900],
  },
});

export default PaediatricWorkspaces;
