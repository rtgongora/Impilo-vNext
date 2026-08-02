"use client";

import React from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { usePatient } from "@/hooks/queries/usePatients";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { clinicalFormPatientContextFromPatient } from "@/lib/clinical-forms/patient-context";
import { GovernedEncounterFormPanel } from "@/features/maternity/journeys/GovernedEncounterFormPanel";
import { ImnciClassificationPanel } from "@/features/paediatrics/imnci/ImnciClassificationPanel";
import { DangerSignEnginePanel } from "@/features/paediatrics/imnci/DangerSignEnginePanel";
import { IMNCI_STEPS } from "@/features/paediatrics/imnci/imnci-journey";
import { ageFactsFor, SICK_YOUNG_INFANT_MAX_AGE_DAYS } from "@/features/paediatrics/workspace/paediatric-age";

/**
 * IMNCI assess and classify.
 *
 * Three engines meet on this page and they are deliberately not merged into one verdict: the
 * governed form captures, the danger-sign rules screen, and the classification tables classify.
 * The danger-sign layer and the classification layer are held consistent by a test in the
 * knowledge platform that asserts across eight presentations that they never disagree about the
 * same baby — but they answer different questions, and a clinician needs both sentences.
 *
 * **Which chart runs is chosen by age and is shown, not implied.** The sick young infant chart
 * covers birth to two months and the sick child chart two months to five years; they ask different
 * questions and reach different classifications, so running the wrong one is not a near-miss. The
 * engine routes for itself and reports the chart it used; this page routes the *form* the same way
 * so the questions asked match the chart that will read them.
 */

const CHILD_FORM_KEY = "impilo.child.imci.v1";
const YOUNG_INFANT_FORM_KEY = "impilo.young-infant.imci.v1";

export default function ImnciAssessmentPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data: patientData, isLoading: patientLoading, isError: patientError } = usePatient(patientId);
  const { data: encountersData, isError: encountersError } = useEncounters(patientId);

  // The findings the engines evaluate. Populated when the governed form is submitted, because a
  // classification is a statement about what was recorded — running it over half-typed values
  // would show a clinician a classification that changes under them as they work.
  const [findings, setFindings] = React.useState<Record<string, unknown> | null>(null);

  const attributes = (patientData?.data as { attributes?: Record<string, unknown> } | undefined)?.attributes;
  const dateOfBirth = typeof attributes?.dateOfBirth === "string" ? attributes.dateOfBirth : null;
  const age = ageFactsFor(dateOfBirth);
  const isYoungInfant = age.ageDays !== null && age.ageDays <= SICK_YOUNG_INFANT_MAX_AGE_DAYS;
  const formKey = isYoungInfant ? YOUNG_INFANT_FORM_KEY : CHILD_FORM_KEY;

  const activeEncounter = (encountersData?.data ?? []).find((encounter) => encounter.attributes.isOpen);
  const encounterId = activeEncounter?.id ?? "";

  const formPatient = React.useMemo(
    () => (patientData?.data ? clinicalFormPatientContextFromPatient(patientData.data, {}) : null),
    [patientData],
  );

  return (
    <EHRLayout>
      <PageShell
        title="IMNCI assessment"
        subtitle="Assess, classify and plan — age-routed between the sick child and sick young infant charts."
      >
        <div className="space-y-4">
          {patientLoading ? (
            <p className="text-sm text-muted-foreground" data-testid="imnci-page-loading">
              Loading the child&apos;s record…
            </p>
          ) : patientError ? (
            <section
              className="rounded-lg border border-rose-400 bg-rose-50 p-4 dark:border-rose-600 dark:bg-rose-950/40"
              data-testid="imnci-page-patient-error"
            >
              <h2 className="text-sm font-semibold text-foreground">Patient record unavailable</h2>
              <p className="mt-1 text-sm text-foreground">
                The child&apos;s record could not be read, so their age is unknown and neither IMNCI
                chart can be selected. Nothing has been assessed.
              </p>
            </section>
          ) : (
            <>
              <ChartBanner ageDisplay={age.display} ageDays={age.ageDays} isYoungInfant={isYoungInfant} />

              {!age.isUnderFive && age.ageDays !== null && (
                <section
                  className="rounded-lg border border-border bg-muted/40 p-4"
                  data-testid="imnci-page-out-of-range"
                >
                  <p className="text-sm text-foreground">
                    IMNCI covers children from birth to five years. This child is {age.display} old,
                    so the charts do not apply and nothing below has been run.
                  </p>
                </section>
              )}

              {age.isUnderFive && (
                <>
                  {/* The screen runs against whatever has been recorded, including nothing at all —
                      which is the case it most needs to handle honestly, since a child nobody has
                      examined must report as unassessed rather than as well. */}
                  <DangerSignEnginePanel
                    patientId={patientId}
                    ageDays={age.ageDays}
                    findings={findings ?? {}}
                  />

                  <StepGuide />

                  {encountersError ? (
                    <section
                      className="rounded-lg border border-rose-400 bg-rose-50 p-4 dark:border-rose-600 dark:bg-rose-950/40"
                      data-testid="imnci-page-encounter-error"
                    >
                      <h2 className="text-sm font-semibold text-foreground">Encounters unavailable</h2>
                      <p className="mt-1 text-sm text-foreground">
                        The child&apos;s encounters could not be read, so the assessment cannot be
                        anchored to one. Do not read this as there being no open encounter.
                      </p>
                    </section>
                  ) : !encounterId ? (
                    <section
                      className="rounded-lg border border-slate-400 border-dashed bg-slate-50 p-4 dark:border-slate-500 dark:bg-slate-900/60"
                      data-testid="imnci-page-no-encounter"
                    >
                      <h2 className="text-sm font-semibold text-foreground">No open encounter</h2>
                      <p className="mt-1 text-sm text-foreground">
                        An IMNCI assessment is a clinical record and has to belong to a visit, so it
                        cannot be captured without an open encounter. Open one and return here.
                      </p>
                      <Link
                        href={`/ehr/${patientId}/encounters`}
                        className="mt-3 inline-block min-h-[44px] rounded-md border border-border px-3 py-2 text-sm hover:bg-muted"
                      >
                        Go to encounters
                      </Link>
                    </section>
                  ) : formPatient ? (
                    <GovernedEncounterFormPanel
                      testId="imnci-capture-panel"
                      formKey={formKey}
                      title={isYoungInfant ? "Sick young infant assessment" : "Sick child assessment"}
                      description={
                        isYoungInfant
                          ? "Birth to two months. The young infant chart asks different questions from the child chart."
                          : "Two months to five years."
                      }
                      encounterId={encounterId}
                      patientId={patientId}
                      patient={formPatient}
                      providerRoles={["NURSE"]}
                      encounterType="OUTPATIENT"
                      onSubmitted={(answers) => setFindings(answers)}
                    />
                  ) : null}

                  {findings ? (
                    <ImnciClassificationPanel
                      patientId={patientId}
                      ageDays={age.ageDays}
                      findings={findings}
                    />
                  ) : (
                    <section
                      className="rounded-lg border border-slate-400 border-dashed bg-slate-50 p-4 dark:border-slate-500 dark:bg-slate-900/60"
                      data-testid="imnci-not-yet-classified"
                    >
                      <h2 className="text-sm font-semibold text-foreground">Not yet classified</h2>
                      <p className="mt-1 text-sm text-foreground">
                        Record the assessment above to classify. Nothing has been classified for
                        this child at this contact — which is not the same as nothing being wrong.
                      </p>
                    </section>
                  )}
                </>
              )}
            </>
          )}
        </div>
      </PageShell>
    </EHRLayout>
  );
}

function ChartBanner({
  ageDisplay,
  ageDays,
  isYoungInfant,
}: {
  ageDisplay: string;
  ageDays: number | null;
  isYoungInfant: boolean;
}) {
  if (ageDays === null) {
    return (
      <section
        className="rounded-lg border border-slate-400 border-dashed bg-slate-50 p-4 dark:border-slate-500 dark:bg-slate-900/60"
        data-testid="imnci-page-no-age"
      >
        <h2 className="text-sm font-semibold text-foreground">No date of birth on the record</h2>
        <p className="mt-1 text-sm text-foreground">
          IMNCI is age-routed, so without a date of birth neither chart can be selected. Record the
          child&apos;s date of birth before assessing.
        </p>
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-border bg-background p-4" data-testid="imnci-page-chart">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground">
          {isYoungInfant ? "Sick young infant chart" : "Sick child chart"}
        </h2>
        <span className="text-xs text-muted-foreground">
          {ageDisplay} old · {ageDays} days
        </span>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">
        {isYoungInfant
          ? "Selected because this infant is under two months. The young infant chart looks for possible serious bacterial infection, which the child chart does not."
          : "Selected because this child is two months or older."}
      </p>
    </section>
  );
}

/**
 * The order the guideline intends. Shown because the sequence is most of what turns a form into an
 * assessment — emergency signs before anything else, general danger signs before the complaint,
 * and nutrition and immunisation in every child whatever brought them in.
 */
function StepGuide() {
  return (
    <details className="rounded-lg border border-border bg-background p-4" data-testid="imnci-step-guide">
      <summary className="cursor-pointer text-sm font-semibold text-foreground">
        The IMNCI sequence
      </summary>
      <ol className="mt-2 space-y-2">
        {IMNCI_STEPS.map((step) => (
          <li key={step.sectionId} className="text-sm">
            <span className="font-medium text-foreground">{step.title}</span>
            {step.universal && (
              <span className="ml-2 rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground">
                every child
              </span>
            )}
            <span className="block text-xs text-muted-foreground">{step.intent}</span>
          </li>
        ))}
      </ol>
    </details>
  );
}
