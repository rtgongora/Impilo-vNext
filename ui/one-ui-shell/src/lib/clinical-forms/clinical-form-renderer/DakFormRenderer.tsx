"use client";

import { useMemo, useState } from "react";
import { AlertTriangle, CheckCircle2, ClipboardList } from "lucide-react";
import type { ClinicalFormDefinition, ClinicalFormRuntimeContext, FormValues } from "../types";
import { visibleFieldsForSection } from "../evaluate-visibility";
import { validateClinicalForm } from "../validate-form";
import { useClinicalFormDraft } from "./useClinicalFormDraft";
import { SegmentedControl } from "shared-ui";
import { optionsForKind, tonesForKind } from "../canonical-option-sets";
import { BodyMapField } from "@/features/body-map/BodyMapField";
import type { FormBodyMapFinding } from "../types";
import { runAncContact1DecisionSupport, type DecisionSupportAlert } from "../decision-support-hooks/anc-decision-support";
import { ANTENATAL_CONTACT_1_FORM } from "../clinical-form-definitions/antenatal-contact-1-exemplar";

function renderField(
  field: ClinicalFormDefinition["sections"][number]["fields"][number],
  values: FormValues,
  setField: (id: string, v: FormValues[string]) => void,
  errors: Record<string, string>,
) {
  const err = errors[field.id];
  const base = "mt-2 block w-full rounded-lg border px-3 py-2 text-sm";
  const border = err ? "border-red-400" : "border-border";

  switch (field.kind) {
    case "segmented":
    case "clinical_assertion":
    case "exam_finding": {
      // Options for the clinical kinds come from the canonical sets rather than the form
      // author, so "not assessed" means the same thing on every form in the platform.
      const canonical = optionsForKind(field.kind);
      const options = (canonical ?? field.options ?? []).map((o) => ({
        value: o.code,
        label: o.display,
        tone: tonesForKind(field.kind)[o.code],
      }));
      return (
        <div key={field.id}>
          <span id={`${field.id}-label`} className="text-xs font-medium text-muted-foreground">
            {field.label}
          </span>
          <div className="mt-1">
            <SegmentedControl
              options={options}
              value={values[field.id] === undefined || values[field.id] === null ? "" : String(values[field.id])}
              onChange={(v) => setField(field.id, v)}
              aria-labelledby={`${field.id}-label`}
              data-testid={`field-${field.id}`}
            />
          </div>
          {field.allowAuxiliaryNarrative && (
            <textarea
              className={`${base} ${border} mt-2`}
              rows={2}
              placeholder={field.auxiliaryNarrativeLabel ?? "Optional notes"}
              value={String(values[`${field.id}__narrative`] ?? "")}
              onChange={(e) => setField(`${field.id}__narrative`, e.target.value)}
            />
          )}
          {err && <p className="mt-1 text-xs text-red-600">{err}</p>}
        </div>
      );
    }
    case "coded_single":
    case "terminology_bound":
      return (
        <div key={field.id}>
          <span className="text-xs font-medium text-muted-foreground">{field.label}</span>
          <select
            className={`${base} ${border} mt-1`}
            value={String(values[field.id] ?? "")}
            onChange={(e) => setField(field.id, e.target.value)}
          >
            <option value="">Select…</option>
            {(field.options ?? []).map((o) => (
              <option key={o.code} value={o.code}>
                {o.display}
              </option>
            ))}
          </select>
          {field.allowAuxiliaryNarrative && (
            <textarea
              className={`${base} ${border} mt-2`}
              rows={2}
              placeholder={field.auxiliaryNarrativeLabel ?? "Optional notes"}
              value={String(values[`${field.id}__narrative`] ?? "")}
              onChange={(e) => setField(`${field.id}__narrative`, e.target.value)}
            />
          )}
          {err && <p className="mt-1 text-xs text-red-600">{err}</p>}
        </div>
      );
    case "coded_multi": {
      const cur = (values[field.id] as string[] | undefined) ?? [];
      const toggle = (code: string) => {
        const set = new Set(cur);
        if (set.has(code)) set.delete(code);
        else set.add(code);
        setField(field.id, Array.from(set));
      };
      return (
        <div key={field.id}>
          <span className="text-xs font-medium text-muted-foreground">{field.label}</span>
          <div className="mt-1 grid grid-cols-1 sm:grid-cols-2 gap-1">
            {(field.options ?? []).map((o) => (
              <label key={o.code} className="flex items-center gap-2 text-sm text-foreground cursor-pointer">
                <input type="checkbox" checked={cur.includes(o.code)} onChange={() => toggle(o.code)} className="rounded border-border" />
                {o.display}
              </label>
            ))}
          </div>
          {err && <p className="mt-1 text-xs text-red-600">{err}</p>}
        </div>
      );
    }
    case "numeric_with_unit":
      return (
        <label key={field.id} className="block">
          <span className="text-xs font-medium text-muted-foreground">{field.label}</span>
          <input
            type="number"
            className={`${base} ${border} mt-1`}
            value={values[field.id] === undefined || values[field.id] === null ? "" : String(values[field.id])}
            onChange={(e) => setField(field.id, e.target.value === "" ? "" : Number(e.target.value))}
          />
          {field.unit && <span className="ml-2 text-xs text-muted-foreground">{field.unit}</span>}
          {err && <p className="mt-1 text-xs text-red-600">{err}</p>}
        </label>
      );
    case "date":
      return (
        <label key={field.id} className="block">
          <span className="text-xs font-medium text-muted-foreground">{field.label}</span>
          <input
            type="date"
            className={`${base} ${border} mt-1`}
            value={String(values[field.id] ?? "")}
            onChange={(e) => setField(field.id, e.target.value)}
          />
          {err && <p className="mt-1 text-xs text-red-600">{err}</p>}
        </label>
      );
    case "text":
      return (
        <label key={field.id} className="block">
          <span className="text-xs font-medium text-muted-foreground">{field.label}</span>
          <textarea
            className={`${base} ${border} mt-1`}
            rows={3}
            value={String(values[field.id] ?? "")}
            onChange={(e) => setField(field.id, e.target.value)}
          />
          {err && <p className="mt-1 text-xs text-red-600">{err}</p>}
        </label>
      );
    case "body_map": {
      const findings = Array.isArray(values[field.id]) ? (values[field.id] as FormBodyMapFinding[]) : [];
      return (
        <div key={field.id}>
          <span className="text-xs font-medium text-muted-foreground">{field.label}</span>
          <div className="mt-2">
            <BodyMapField
              instrumentId={field.bodyMapInstrument ?? "general-body"}
              value={findings}
              onChange={(next) => setField(field.id, next)}
              data-testid={`field-${field.id}`}
            />
          </div>
          {err && <p className="mt-1 text-xs text-red-600">{err}</p>}
        </div>
      );
    }
    case "boolean":
      // Rendered as an explicit two-option choice rather than a checkbox: an unticked
      // checkbox is indistinguishable from an unanswered question, which is exactly the
      // ambiguity clinical data entry cannot afford.
      return (
        <div key={field.id}>
          <span id={`${field.id}-label`} className="text-xs font-medium text-muted-foreground">
            {field.label}
          </span>
          <div className="mt-1">
            <SegmentedControl
              options={[
                { value: "true", label: "Yes", tone: "danger" },
                { value: "false", label: "No", tone: "positive" },
              ]}
              value={values[field.id] === undefined || values[field.id] === null ? "" : String(values[field.id])}
              onChange={(v) => setField(field.id, v === "true")}
              aria-labelledby={`${field.id}-label`}
              data-testid={`field-${field.id}`}
            />
          </div>
          {err && <p className="mt-1 text-xs text-red-600">{err}</p>}
        </div>
      );
    case "datetime":
      return (
        <label key={field.id} className="block">
          <span className="text-xs font-medium text-muted-foreground">{field.label}</span>
          <input
            type="datetime-local"
            className={`${base} ${border} mt-1`}
            data-testid={`field-${field.id}`}
            value={String(values[field.id] ?? "")}
            onChange={(e) => setField(field.id, e.target.value)}
          />
          {err && <p className="mt-1 text-xs text-red-600">{err}</p>}
        </label>
      );
    case "fhir_mapped":
      // The FHIR link is provenance, not a widget: render by the underlying shape so the
      // clinician sees a normal field and the mapping stays metadata.
      return (
        <label key={field.id} className="block">
          <span className="text-xs font-medium text-muted-foreground">{field.label}</span>
          <input
            type={field.unit ? "number" : "text"}
            className={`${base} ${border} mt-1`}
            data-testid={`field-${field.id}`}
            value={values[field.id] === undefined || values[field.id] === null ? "" : String(values[field.id])}
            onChange={(e) =>
              setField(field.id, field.unit ? (e.target.value === "" ? "" : Number(e.target.value)) : e.target.value)
            }
          />
          {field.unit && <span className="ml-2 text-xs text-muted-foreground">{field.unit}</span>}
          {err && <p className="mt-1 text-xs text-red-600">{err}</p>}
        </label>
      );
    default:
      return (
        <div key={field.id} className="text-xs text-muted-foreground">
          Unsupported field kind: {field.kind}
        </div>
      );
  }
}

function decisionAlertsForForm(form: ClinicalFormDefinition, ctx: ClinicalFormRuntimeContext, values: FormValues): DecisionSupportAlert[] {
  if (form.id === ANTENATAL_CONTACT_1_FORM.id) {
    return runAncContact1DecisionSupport(ctx, values);
  }
  return [];
}

export function DakFormRenderer(props: {
  form: ClinicalFormDefinition;
  runtime: ClinicalFormRuntimeContext;
  patientId: string;
  encounterId: string;
  showProgress?: boolean;
  /** When provided, a Submit button appears; called with the current answers once validation passes. */
  onSubmit?: (values: FormValues) => void | Promise<void>;
  submitting?: boolean;
  submitLabel?: string;
}) {
  const { form, runtime, patientId, encounterId, showProgress = true, onSubmit, submitting = false, submitLabel = "Submit" } = props;
  const initial: FormValues = {};
  const { values, setField, clearDraft, dirty } = useClinicalFormDraft(form.id, patientId, encounterId, initial);
  const [attempted, setAttempted] = useState(false);

  const issues = useMemo(() => validateClinicalForm(form, values, runtime), [form, values, runtime]);
  const errors = useMemo(() => {
    const m: Record<string, string> = {};
    for (const i of issues) m[i.fieldId] = i.message;
    return m;
  }, [issues]);

  const dsAlerts = useMemo(() => decisionAlertsForForm(form, runtime, values), [form, runtime, values]);

  const { totalRequired, completedRequired } = useMemo(() => {
    let total = 0;
    let done = 0;
    for (const sec of form.sections) {
      const vis = visibleFieldsForSection(sec.fields, runtime);
      for (const f of vis) {
        if (!f.validation?.required) continue;
        total += 1;
        const v = values[f.id];
        const ok =
          v !== undefined &&
          v !== null &&
          v !== "" &&
          !(Array.isArray(v) && v.length === 0);
        if (ok) done += 1;
      }
    }
    return { totalRequired: total, completedRequired: done };
  }, [form, runtime, values]);

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
            <ClipboardList className="w-4 h-4 text-primary" />
            {form.title}
          </h3>
          <p className="text-xs text-muted-foreground mt-0.5">{form.description}</p>
          <p className="text-[10px] text-muted-foreground mt-1">Form {form.id} · v{form.version}</p>
        </div>
        {showProgress && totalRequired > 0 && (
          <div className="text-right">
            <p className="text-xs font-medium text-foreground">
              {completedRequired}/{totalRequired} required
            </p>
            <div className="mt-1 h-1.5 w-28 rounded-full bg-neutral-100 overflow-hidden">
              <div
                className="h-full bg-primary transition-all"
                style={{ width: `${Math.round((100 * completedRequired) / totalRequired)}%` }}
              />
            </div>
          </div>
        )}
      </div>

      {dirty && (
        <p className="text-xs text-warning-foreground bg-warning-soft border border-warning/35 rounded px-2 py-1">
          Draft autosaved locally in this browser.
          <button type="button" className="ml-2 underline" onClick={clearDraft}>
            Reset draft
          </button>
        </p>
      )}

      {dsAlerts.length > 0 && (
        <div className="space-y-1">
          {dsAlerts.map((a) => (
            <div
              key={a.id}
              className={`flex items-start gap-2 rounded-lg border px-2 py-1.5 text-xs ${
                a.severity === "critical"
                  ? "border-red-300 bg-danger-soft text-red-900"
                  : a.severity === "warning"
                    ? "border-amber-300 bg-warning-soft text-warning-foreground"
                    : "border-border bg-background text-foreground"
              }`}
            >
              <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
              <span>{a.message}</span>
            </div>
          ))}
        </div>
      )}

      {form.sections.map((sec) => {
        const visFields = visibleFieldsForSection(sec.fields, runtime);
        if (!visFields.length) return null;
        return (
          <div key={sec.id} className="rounded-lg border border-border p-3 space-y-2">
            <h4 className="text-xs font-semibold text-foreground uppercase tracking-wide">{sec.title}</h4>
            {sec.description && <p className="text-[11px] text-muted-foreground">{sec.description}</p>}
            <div className="space-y-3">{visFields.map((f) => renderField(f, values, setField, errors))}</div>
          </div>
        );
      })}

      {attempted && issues.length === 0 && (
        <p className="text-xs text-green-700 flex items-center gap-1">
          <CheckCircle2 className="w-3.5 h-3.5" /> All required visible fields pass validation.
        </p>
      )}

      <div className="flex items-center gap-3 pt-1">
        <button
          type="button"
          className="text-xs text-muted-foreground underline"
          onClick={() => setAttempted(true)}
        >
          Check validation
        </button>
        {onSubmit && (
          <button
            type="button"
            disabled={submitting}
            className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-50"
            onClick={() => {
              setAttempted(true);
              if (issues.length === 0) void onSubmit(values);
            }}
          >
            {submitting ? "Submitting…" : submitLabel}
          </button>
        )}
      </div>
    </div>
  );
}
