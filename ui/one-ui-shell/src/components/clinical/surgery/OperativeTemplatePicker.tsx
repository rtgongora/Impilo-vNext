"use client";

/**
 * Operative template picker for the theatre operative note. Selecting a template fills
 * operativeTemplateRef/Code and the SB-5 depth fields from the SB-4 catalogue. A failed
 * catalogue read must never look like "no templates".
 */

import { AlertTriangle, Loader2 } from "lucide-react";
import {
  useSpecialtyTemplates,
  type OperativeTemplate,
} from "@/hooks/queries/useSurgeryEpisodes";

const SURGICAL_SPECIALTIES = [
  "GENERAL_SURGERY",
  "ORTHOPAEDICS",
  "UROLOGY",
  "ENT",
  "OPHTHALMOLOGY",
  "COLORECTAL",
  "UPPER_GI_HPB",
  "BREAST_ENDOCRINE",
  "VASCULAR",
  "NEUROSURGERY",
  "CARDIOTHORACIC",
  "MAXILLOFACIAL",
  "PLASTICS",
  "PAEDIATRIC_SURGERY",
  "SURGICAL_ONCOLOGY",
] as const;

export type OperativeNoteTemplateFill = {
  operativeTemplateRef: string;
  operativeTemplateCode: string;
  patientPosition: string;
  skinPreparation: string;
  incision: string;
  operativeSteps: string;
  operativeTechnique: string;
  closureMethod: string;
  woundClassification: string;
  postoperativeInstructions: string;
  performedProcedure?: string;
};

function fillFromTemplate(t: OperativeTemplate): OperativeNoteTemplateFill {
  return {
    operativeTemplateRef: t.id ?? "",
    operativeTemplateCode: t.templateCode,
    patientPosition: t.position ?? "",
    skinPreparation: t.preparation ?? "",
    incision: t.incision ?? "",
    operativeSteps: t.keySteps ?? "",
    operativeTechnique: t.techniqueOptions ?? "",
    closureMethod: t.closurePlan ?? "",
    woundClassification: t.woundClassification ?? "",
    postoperativeInstructions: t.postopInstructions ?? "",
    performedProcedure: t.typicalProcedure ?? undefined,
  };
}

export function OperativeTemplatePicker({
  specialty,
  onSpecialtyChange,
  onPick,
}: {
  specialty: string;
  onSpecialtyChange: (specialty: string) => void;
  onPick: (fill: OperativeNoteTemplateFill) => void;
}) {
  const templatesQ = useSpecialtyTemplates(specialty || null);

  return (
    <div className="mb-3 space-y-2" data-testid="operative-template-picker">
      <label className="block text-sm">
        <span className="mb-1 block text-xs font-medium text-muted-foreground">
          Specialty (for operative template)
        </span>
        <select
          value={specialty}
          onChange={(e) => onSpecialtyChange(e.target.value)}
          className="w-full rounded-lg border border-border bg-background px-3 py-1.5"
          data-testid="note-template-specialty"
        >
          <option value="">Choose specialty…</option>
          {SURGICAL_SPECIALTIES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>

      {!specialty ? (
        <p className="text-xs text-muted-foreground">
          Choose a specialty to load its operative templates.
        </p>
      ) : templatesQ.isLoading ? (
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading templates…
        </div>
      ) : templatesQ.isError ? (
        <div
          className="flex items-center gap-2 text-xs text-danger"
          role="alert"
          data-testid="operative-template-picker-unavailable"
        >
          <AlertTriangle className="h-4 w-4" />
          Could not read operative templates. This is not the same as there being none.
        </div>
      ) : !Array.isArray(templatesQ.data) || templatesQ.data.length === 0 ? (
        <p className="text-xs text-muted-foreground" data-testid="operative-template-picker-empty">
          No catalogue templates for this specialty.
        </p>
      ) : (
        <label className="block text-sm">
          <span className="mb-1 block text-xs font-medium text-muted-foreground">
            Operative template (fills SB-5 fields)
          </span>
          <select
            className="w-full rounded-lg border border-border bg-background px-3 py-1.5"
            defaultValue=""
            onChange={(e) => {
              const code = e.target.value;
              const hit = templatesQ.data?.find((t) => t.templateCode === code);
              if (hit) onPick(fillFromTemplate(hit));
            }}
            data-testid="note-template-select"
          >
            <option value="">Pick a template…</option>
            {templatesQ.data.map((t) => (
              <option key={t.templateCode} value={t.templateCode}>
                {t.templateCode} — {t.templateName}
              </option>
            ))}
          </select>
        </label>
      )}
    </div>
  );
}
