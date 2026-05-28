import type { ClinicalFormDefinition, ClinicalFormRuntimeContext, FormValidationIssue, FormValues } from "./types";
import { fieldVisible } from "./evaluate-visibility";

export function validateClinicalForm(
  form: ClinicalFormDefinition,
  values: FormValues,
  ctx: ClinicalFormRuntimeContext,
): FormValidationIssue[] {
  const issues: FormValidationIssue[] = [];
  for (const sec of form.sections) {
    for (const f of sec.fields) {
      if (!fieldVisible(f.visibility, ctx)) continue;
      const v = values[f.id];
      const req = f.validation?.required;
      if (req && (v === undefined || v === null || v === "" || (Array.isArray(v) && v.length === 0))) {
        issues.push({ fieldId: f.id, message: `${f.label} is required` });
        continue;
      }
      if (f.kind === "numeric_with_unit" && v !== undefined && v !== null && v !== "") {
        const n = Number(v);
        if (Number.isNaN(n)) {
          issues.push({ fieldId: f.id, message: "Must be a number" });
        } else {
          if (f.validation?.min != null && n < f.validation.min) issues.push({ fieldId: f.id, message: `Minimum ${f.validation.min}` });
          if (f.validation?.max != null && n > f.validation.max) issues.push({ fieldId: f.id, message: `Maximum ${f.validation.max}` });
        }
      }
      if (f.kind === "text" && typeof v === "string" && f.validation?.maxLength && v.length > f.validation.maxLength) {
        issues.push({ fieldId: f.id, message: `Max length ${f.validation.maxLength}` });
      }
    }
  }
  return issues;
}
