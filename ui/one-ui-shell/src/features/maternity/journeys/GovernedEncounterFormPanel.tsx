"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, CheckCircle2, Loader2 } from "lucide-react";
import { DakFormRenderer } from "@/lib/clinical-forms/clinical-form-renderer/DakFormRenderer";
import type {
  ClinicalFormPatientContext,
  ClinicalFormRuntimeContext,
  FormValues,
} from "@/lib/clinical-forms/types";
import { useSubmitEncounterForm } from "@/hooks/queries/useEncounterForms";
import { useGovernedFormDefinition } from "@/hooks/queries/useGovernedFormDefinition";

export interface GovernedEncounterFormPanelProps {
  formKey: string;
  title: string;
  description?: string;
  encounterId: string;
  patientId: string;
  patient: ClinicalFormPatientContext;
  providerRoles: string[];
  encounterType?: string;
  headerSlot?: React.ReactNode;
  onSubmitted?: (answers: Record<string, unknown>) => void;
  outcomeSlot?: React.ReactNode;
  testId?: string;
}

export function GovernedEncounterFormPanel({
  formKey,
  title,
  description,
  encounterId,
  patientId,
  patient,
  providerRoles,
  encounterType = "OUTPATIENT",
  headerSlot,
  onSubmitted,
  outcomeSlot,
  testId,
}: GovernedEncounterFormPanelProps) {
  const { isLoading, isError, definition, catalogEntry } = useGovernedFormDefinition(formKey);
  const submit = useSubmitEncounterForm(encounterId);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  const runtime: ClinicalFormRuntimeContext = useMemo(
    () => ({ patient, encounterType, providerRoles }),
    [patient, encounterType, providerRoles],
  );

  return (
    <div className="mt-4 rounded-xl border border-pink-200/80 bg-gradient-to-b from-pink-50/30 to-card p-4" data-testid={testId}>
      <h3 className="text-sm font-semibold text-pink-950">{title}</h3>
      <p className="mt-1 text-xs text-muted-foreground">
        {description ?? (
          <>
            Form <code className="rounded bg-neutral-100 px-1">{formKey}</code>
            {catalogEntry ? ` · v${catalogEntry.version}` : ""} — forced catalog key, not resolver.
          </>
        )}
      </p>
      {headerSlot && <div className="mt-3">{headerSlot}</div>}
      {!encounterId && (
        <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm" data-testid={`${testId}-no-encounter`}>
          <AlertTriangle className="h-4 w-4 shrink-0" />
          <p>An active encounter is required to record this form.</p>
        </div>
      )}
      {isLoading && (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading form…
        </div>
      )}
      {isError && <p className="mt-3 text-xs text-red-700">Form catalog unavailable.</p>}
      {!isLoading && !isError && !definition && (
        <p className="mt-3 text-xs text-red-700">Form {formKey} not found in catalog.</p>
      )}
      {definition && encounterId && (
        <div className="mt-3">
          {submitted && (
            <p className="mb-2 flex items-center gap-1 text-xs text-green-700">
              <CheckCircle2 className="h-3.5 w-3.5" /> Submitted on encounter.
            </p>
          )}
          {submitError && <p className="mb-2 text-xs text-red-700">{submitError}</p>}
          <DakFormRenderer
            form={definition}
            runtime={runtime}
            patientId={patientId}
            encounterId={encounterId}
            submitting={submit.isPending}
            submitLabel="Submit & record"
            onSubmit={async (values: FormValues) => {
              setSubmitError(null);
              try {
                await submit.mutateAsync({ formKey, answers: values as Record<string, unknown> });
                setSubmitted(true);
                onSubmitted?.(values as Record<string, unknown>);
              } catch (e) {
                setSubmitError(e instanceof Error ? e.message : "Submission failed");
              }
            }}
          />
        </div>
      )}
      {outcomeSlot && <div className="mt-3">{outcomeSlot}</div>}
    </div>
  );
}

export function YoungInfantPathLink({ patientId }: { patientId: string }) {
  return (
    <div className="rounded-lg border border-sky-200/80 bg-sky-50/60 px-3 py-2 text-xs text-sky-950" data-testid="young-infant-path-link">
      <p className="font-medium">Systemic PSBI signs are not captured here</p>
      <p className="mt-1">
        Use the{" "}
        <Link href={`/ehr/${encodeURIComponent(patientId)}/paediatrics`} className="font-semibold underline">
          sick young infant pathway
        </Link>{" "}
        (<code className="rounded bg-white/70 px-1">impilo.young-infant.imci.v1</code>) for feeding, breathing,
        convulsions and temperature — the single PSBI definition.
      </p>
    </div>
  );
}
