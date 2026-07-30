"use client";

/**
 * Safety plan — the patient's own plan, in their own words where possible.
 *
 * Recorded, never edited. A safety plan made in a crisis and a safety plan revised a week later are
 * two records, and the earlier one is what the patient was working from at the time.
 */

import { useState } from "react";

import {
  useMentalHealthSafetyPlans,
  useRecordMentalHealthSafetyPlan,
} from "@/hooks/queries/useMentalHealth";
import { bffErrorMessage } from "@/lib/bff-errors";

import { PanelShell } from "./PanelShell";

const FIELDS: { key: "warningSigns" | "copingStrategies" | "supportContacts" | "meansRestrictionNotes"; label: string; hint: string; row: string }[] = [
  {
    key: "warningSigns",
    label: "Warning signs",
    hint: "What the patient notices before things worsen",
    row: "warning_signs",
  },
  {
    key: "copingStrategies",
    label: "Coping strategies",
    hint: "What has helped this patient before",
    row: "coping_strategies",
  },
  {
    key: "supportContacts",
    label: "Support contacts",
    hint: "Who they will call, by name",
    row: "support_contacts",
  },
  {
    key: "meansRestrictionNotes",
    label: "Means restriction",
    hint: "What was agreed about access to means",
    row: "means_restriction_notes",
  },
];

function when(value: unknown): string {
  if (!value) return "—";
  const parsed = new Date(String(value));
  return Number.isNaN(parsed.getTime()) ? String(value) : parsed.toLocaleString();
}

export function SafetyPlanPanel({ referralId, actor }: { referralId: string; actor: string }) {
  const plans = useMentalHealthSafetyPlans(referralId);
  const record = useRecordMentalHealthSafetyPlan(referralId);

  const [draft, setDraft] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const rows = plans.data ?? [];
  const anyContent = FIELDS.some((field) => (draft[field.key] ?? "").trim().length > 0);

  return (
    <PanelShell
      title="Safety plan"
      subject="this patient's safety plans"
      description="mh_safety_plan — appended, not edited; an earlier plan is what the patient was working from at the time"
      isLoading={plans.isLoading}
      isError={plans.isError}
      error={plans.error}
      isEmpty={rows.length === 0}
      emptyMessage="No safety plan has been recorded with this patient."
      data-testid="mh-safety-plans"
      actions={
        <form
          className="space-y-3"
          data-testid="mh-safety-plan-form"
          onSubmit={async (event) => {
            event.preventDefault();
            setFormError(null);
            try {
              await record.mutateAsync({
                warningSigns: draft.warningSigns?.trim() || undefined,
                copingStrategies: draft.copingStrategies?.trim() || undefined,
                supportContacts: draft.supportContacts?.trim() || undefined,
                meansRestrictionNotes: draft.meansRestrictionNotes?.trim() || undefined,
                createdBy: actor || undefined,
              });
              setDraft({});
            } catch (error) {
              setFormError(bffErrorMessage(error, "this safety plan"));
            }
          }}
        >
          {FIELDS.map((field) => (
            <label key={field.key} className="block text-sm">
              <span className="font-medium text-foreground">{field.label}</span>
              <span className="ml-2 text-xs text-muted-foreground">{field.hint}</span>
              <textarea
                className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
                rows={2}
                value={draft[field.key] ?? ""}
                onChange={(event) => setDraft((prev) => ({ ...prev, [field.key]: event.target.value }))}
                data-testid={`mh-safety-${field.key}`}
              />
            </label>
          ))}

          {formError ? (
            <p className="text-sm text-danger" data-testid="mh-safety-plan-error">
              {formError}
            </p>
          ) : null}

          <button
            type="submit"
            className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
            disabled={record.isPending || !anyContent}
            data-testid="mh-safety-plan-submit"
          >
            {record.isPending ? "Recording…" : "Record safety plan"}
          </button>
        </form>
      }
    >
      <ul className="space-y-2">
        {rows.map((row, index) => (
          <li
            key={String(row.id ?? index)}
            className="rounded border border-border p-3 text-sm"
            data-testid="mh-safety-plan-row"
          >
            <p className="text-xs text-muted-foreground">
              {String(row.created_by ?? "—")} · {when(row.created_at)}
            </p>
            <dl className="mt-2 space-y-1">
              {FIELDS.map((field) =>
                row[field.row] ? (
                  <div key={field.key}>
                    <dt className="text-xs font-medium text-foreground">{field.label}</dt>
                    <dd className="text-sm text-muted-foreground">{String(row[field.row])}</dd>
                  </div>
                ) : null,
              )}
            </dl>
          </li>
        ))}
      </ul>
    </PanelShell>
  );
}
