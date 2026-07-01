"use client";

/**
 * Patient Encounter Structured Forms panel.
 *
 * Renders the resolver output — mandatory / recommended / optional / prohibited / countersign-required —
 * and drives the selected form through the shared WHO-DAK renderer, persisting the response server-side
 * (draft → submit) via the canonical PCT path. Prohibited forms are shown greyed with their reason
 * (doctrine: no fake completions), never as live actions.
 */

import { useMemo, useState } from "react";
import { AlertTriangle, CheckCircle2, Lock, ShieldAlert } from "lucide-react";
import { DakFormRenderer } from "@/lib/clinical-forms/clinical-form-renderer/DakFormRenderer";
import type {
  ClinicalFormDefinition,
  ClinicalFormPatientContext,
  ClinicalFormRuntimeContext,
  FormValues,
} from "@/lib/clinical-forms/types";
import {
  useEncounterFormResolution,
  useEncounterFormResponses,
  useFormCatalog,
  useSubmitEncounterForm,
  type FormObligation,
  type ResolveParams,
} from "@/hooks/queries/useEncounterForms";

export interface EncounterFormsPanelProps {
  encounterId: string;
  patientId: string;
  patient: ClinicalFormPatientContext;
  resolveParams: ResolveParams;
  providerRoles: string[];
  encounterType: string;
}

function badgeClass(kind: string): string {
  switch (kind) {
    case "MANDATORY":
      return "bg-danger-soft text-red-900 border-red-300";
    case "RECOMMENDED":
      return "bg-warning-soft text-warning-foreground border-amber-300";
    case "COUNTERSIGN_REQUIRED":
      return "bg-background text-foreground border-primary/40";
    default:
      return "bg-background text-muted-foreground border-border";
  }
}

export function EncounterFormsPanel(props: EncounterFormsPanelProps) {
  const { encounterId, patientId, patient, resolveParams, providerRoles, encounterType } = props;

  const resolution = useEncounterFormResolution(encounterId, resolveParams);
  const catalog = useFormCatalog();
  const responses = useEncounterFormResponses(encounterId);
  const submit = useSubmitEncounterForm(encounterId);

  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submittedKey, setSubmittedKey] = useState<string | null>(null);

  const definitionsByKey = useMemo(() => {
    const map = new Map<string, ClinicalFormDefinition>();
    const entries = catalog.data?.data ?? [];
    for (const e of entries) {
      try {
        map.set(e.formKey, JSON.parse(e.definitionJson) as ClinicalFormDefinition);
      } catch {
        // skip malformed definition — never render a half form
      }
    }
    return map;
  }, [catalog.data]);

  const runtime: ClinicalFormRuntimeContext = useMemo(
    () => ({ patient, encounterType, providerRoles }),
    [patient, encounterType, providerRoles],
  );

  const res = resolution.data?.data;
  const selectedDefinition = selectedKey ? definitionsByKey.get(selectedKey) ?? null : null;
  const selectedObligation: FormObligation | undefined = useMemo(() => {
    if (!res || !selectedKey) return undefined;
    return [...res.mandatory, ...res.recommended, ...res.optional, ...res.countersignRequired].find(
      (o) => o.formKey === selectedKey,
    );
  }, [res, selectedKey]);

  if (resolution.isLoading) {
    return <p className="text-xs text-muted-foreground">Resolving encounter forms…</p>;
  }
  if (resolution.isError || !res) {
    return (
      <p className="text-xs text-red-700 flex items-center gap-1" role="alert">
        <AlertTriangle className="w-3.5 h-3.5" /> Could not resolve encounter forms.
      </p>
    );
  }

  const renderGroup = (title: string, kind: string, items: FormObligation[], selectable: boolean) => {
    if (!items.length) return null;
    return (
      <div className="space-y-1" data-testid={`forms-group-${kind}`}>
        <h5 className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">{title}</h5>
        <ul className="space-y-1">
          {items.map((o) => (
            <li key={o.formKey}>
              <button
                type="button"
                disabled={!selectable || !definitionsByKey.has(o.formKey)}
                onClick={() => selectable && setSelectedKey(o.formKey)}
                className={`w-full text-left flex items-center justify-between gap-2 rounded-md border px-2 py-1.5 text-xs ${badgeClass(
                  kind,
                )} ${!selectable ? "opacity-70 cursor-not-allowed" : "hover:brightness-95"} ${
                  selectedKey === o.formKey ? "ring-2 ring-primary/50" : ""
                }`}
              >
                <span className="flex items-center gap-1.5">
                  {kind === "PROHIBITED" && <Lock className="w-3 h-3" />}
                  {kind === "COUNTERSIGN_REQUIRED" && <ShieldAlert className="w-3 h-3" />}
                  {o.name}
                </span>
                <span className="text-[10px]">
                  {kind === "PROHIBITED" ? (o.reason ?? "Not permitted") : `v${o.formVersion}`}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    );
  };

  return (
    <div className="grid gap-4 md:grid-cols-2">
      <div className="space-y-3">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-foreground">Encounter forms</h4>
        {renderGroup("Mandatory", "MANDATORY", res.mandatory, true)}
        {renderGroup("Recommended", "RECOMMENDED", res.recommended, true)}
        {renderGroup("Optional", "OPTIONAL", res.optional, true)}
        {renderGroup("Requires countersignature", "COUNTERSIGN_REQUIRED", res.countersignRequired, true)}
        {renderGroup("Not permitted", "PROHIBITED", res.prohibited, false)}

        {(responses.data?.data?.length ?? 0) > 0 && (
          <div className="space-y-1 pt-2 border-t border-border" data-testid="forms-submitted">
            <h5 className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              Documented this encounter
            </h5>
            <ul className="space-y-0.5">
              {responses.data!.data.map((r) => (
                <li key={r.responseId} className="text-[11px] text-foreground flex items-center gap-1">
                  <CheckCircle2 className="w-3 h-3 text-green-700" /> {r.formKey} — {r.status}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      <div className="space-y-2">
        {selectedDefinition ? (
          <>
            {selectedObligation?.requiresCountersign && (
              <p className="flex items-center gap-1 rounded-md border border-primary/40 bg-background px-2 py-1.5 text-[11px] text-foreground">
                <ShieldAlert className="w-3.5 h-3.5" /> This form requires a supervisor countersignature before
                its data is extracted.
              </p>
            )}
            {submittedKey === selectedKey && (
              <p className="flex items-center gap-1 text-xs text-green-700" role="status">
                <CheckCircle2 className="w-3.5 h-3.5" /> Submitted and recorded on the encounter.
              </p>
            )}
            {submitError && (
              <p className="text-xs text-red-700 flex items-center gap-1" role="alert">
                <AlertTriangle className="w-3.5 h-3.5" /> {submitError}
              </p>
            )}
            <DakFormRenderer
              form={selectedDefinition}
              runtime={runtime}
              patientId={patientId}
              encounterId={encounterId}
              submitting={submit.isPending}
              submitLabel="Submit & record"
              onSubmit={async (values: FormValues) => {
                setSubmitError(null);
                try {
                  await submit.mutateAsync({
                    formKey: selectedKey!,
                    answers: values as Record<string, unknown>,
                  });
                  setSubmittedKey(selectedKey);
                } catch (e) {
                  setSubmitError(e instanceof Error ? e.message : "Submission failed");
                }
              }}
            />
          </>
        ) : (
          <p className="text-xs text-muted-foreground">Select a form to begin structured documentation.</p>
        )}
      </div>
    </div>
  );
}
