import type { ClinicalFormDefinition, FormValues } from "../types";

export interface QuestionnaireResponseItem {
  linkId: string;
  answer?: Array<{ valueString?: string; valueInteger?: number; valueBoolean?: boolean; valueCoding?: { system: string; code: string; display?: string } }>;
}

/**
 * Minimal QuestionnaireResponse-shaped bundle for interoperability tests and future FHIR export.
 */
export function formValuesToQuestionnaireResponseItems(
  form: ClinicalFormDefinition,
  values: FormValues,
): QuestionnaireResponseItem[] {
  const items: QuestionnaireResponseItem[] = [];
  for (const sec of form.sections) {
    for (const f of sec.fields) {
      const v = values[f.id];
      if (v === undefined || v === null || v === "") continue;
      if (
        (f.kind === "coded_single" || f.kind === "segmented") &&
        typeof v === "string" &&
        f.options?.length
      ) {
        const opt = f.options.find((o) => o.code === v);
        items.push({
          linkId: f.linkId,
          answer: [
            {
              valueCoding: opt
                ? {
                    system: opt.terminologySystem ?? "http://snomed.info/sct",
                    code: opt.terminologyCode ?? opt.code,
                    display: opt.display,
                  }
                : { system: "urn:oid:local", code: v },
            },
          ],
        });
        continue;
      }
      if (Array.isArray(v)) {
        items.push({
          linkId: f.linkId,
          answer: v.map((code) => {
            const opt = f.options?.find((o) => o.code === code);
            return {
              valueCoding: opt
                ? { system: opt.terminologySystem ?? "http://snomed.info/sct", code: opt.terminologyCode ?? opt.code, display: opt.display }
                : { system: "urn:oid:local", code: String(code) },
            };
          }),
        });
        continue;
      }
      if (typeof v === "boolean") {
        items.push({ linkId: f.linkId, answer: [{ valueBoolean: v }] });
        continue;
      }
      if (typeof v === "number") {
        items.push({ linkId: f.linkId, answer: [{ valueInteger: Math.round(v) }] });
        continue;
      }
      items.push({ linkId: f.linkId, answer: [{ valueString: String(v) }] });
    }
  }
  return items;
}
