/**
 * Experience UI — Clinical Specialty ValueSet Hook (TM-B2 / TM-B3)
 *
 * Binds the teleconsult referral specialty picker to the governed ZIBO
 * CodeSystem instead of a hardcoded list. Resolves the canonical artifact via
 * the Experience-BFF read-through proxy and extracts its `concept` entries.
 *
 * Governance-safe by construction: the picker MUST never break. If the fetch
 * fails, returns empty, or the artifact cannot be parsed, the hook falls back
 * to the previously-hardcoded specialty labels so the form always renders.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/** Canonical URL of the governed clinical-specialty CodeSystem (seeded in ZIBO). */
export const CLINICAL_SPECIALTY_CANONICAL_URL =
  "https://impilo.gov.zw/fhir/CodeSystem/clinical-specialty";

/** A single resolved specialty option. */
export interface ClinicalSpecialty {
  code: string;
  display: string;
}

/**
 * Fallback specialty list — the labels previously hardcoded in the teleconsult
 * referral composer. Used when the governed ValueSet is unreachable/empty so the
 * picker never renders blank. Code === display here (the PCT contract keys on the
 * display label), which keeps the submit value identical to the pre-binding form.
 */
export const FALLBACK_SPECIALTY_LABELS: readonly string[] = [
  "Internal Medicine", "Surgery", "Paediatrics", "Obstetrics & Gynaecology",
  "Psychiatry", "Orthopaedics", "ENT", "Ophthalmology", "Dermatology",
  "Cardiology", "Neurology", "Oncology", "Radiology", "Pathology",
  "Anaesthesia", "Emergency Medicine", "Family Medicine", "Public Health",
] as const;

export const FALLBACK_SPECIALTIES: ClinicalSpecialty[] = FALLBACK_SPECIALTY_LABELS.map(
  (label) => ({ code: label, display: label }),
);

type UnknownRecord = Record<string, unknown>;

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === "object" && value !== null;
}

/** Parse a value that may already be an object or a JSON string; null on failure. */
function coerceToObject(value: unknown): UnknownRecord | null {
  if (isRecord(value)) return value;
  if (typeof value === "string") {
    try {
      const parsed = JSON.parse(value);
      return isRecord(parsed) ? parsed : null;
    } catch {
      return null;
    }
  }
  return null;
}

/** Pull a FHIR CodeSystem `concept[]` out of the possibly-wrapped artifact node. */
function extractConceptArray(node: unknown): unknown[] | null {
  const obj = coerceToObject(node);
  if (!obj) return null;

  // Direct: the node IS (or contains) a FHIR CodeSystem with a concept array.
  if (Array.isArray(obj.concept)) return obj.concept;

  // Common artifact-wrapping fields observed across ZIBO shapes. `contentJson` is
  // the ArtifactEntity column (a stringified FHIR resource); `content`/`resource`
  // cover alternate/normalised envelopes. Each may be an object or a JSON string.
  for (const key of ["contentJson", "content", "resource", "artifactJson"]) {
    if (key in obj) {
      const nested = extractConceptArray(obj[key]);
      if (nested) return nested;
    }
  }
  return null;
}

/**
 * Extract `{ code, display }[]` from a resolved ZIBO CodeSystem artifact.
 * Exported for unit testing. Returns [] when nothing usable is present so the
 * hook can decide to fall back.
 */
export function parseSpecialtyConcepts(artifact: unknown): ClinicalSpecialty[] {
  const concepts = extractConceptArray(artifact);
  if (!concepts) return [];
  const out: ClinicalSpecialty[] = [];
  for (const c of concepts) {
    if (!isRecord(c)) continue;
    const code = typeof c.code === "string" ? c.code : undefined;
    const displayRaw = typeof c.display === "string" ? c.display : undefined;
    if (!code) continue;
    // Governed CodeSystems always carry a code; display is preferred but degrade to code.
    out.push({ code, display: displayRaw || code });
  }
  return out;
}

interface ZiboResolveResponse {
  data?: unknown;
  meta?: unknown;
}

/**
 * Resolve the governed clinical-specialty CodeSystem and expose its concepts as
 * `{ code, display }[]`. Always resolves to a non-empty list: the governed
 * concepts when available, otherwise {@link FALLBACK_SPECIALTIES}.
 */
export function useClinicalSpecialties() {
  const query = useQuery<ClinicalSpecialty[]>({
    queryKey: ["clinical-specialties", CLINICAL_SPECIALTY_CANONICAL_URL],
    staleTime: 60 * 60 * 1000, // Governed terminology is stable; cache for an hour.
    queryFn: async () => {
      const res = await apiClient.get<ZiboResolveResponse>(
        `/internal/v1/registry/zibo/artifacts/resolve?canonicalUrl=${encodeURIComponent(
          CLINICAL_SPECIALTY_CANONICAL_URL,
        )}`,
      );
      const parsed = parseSpecialtyConcepts(res?.data);
      // Empty resolve (artifact missing / not yet seeded) → fall back, never blank.
      return parsed.length > 0 ? parsed : FALLBACK_SPECIALTIES;
    },
  });

  // On loading OR error, hand callers the fallback so the picker is never empty.
  const specialties =
    query.data && query.data.length > 0 ? query.data : FALLBACK_SPECIALTIES;

  return { ...query, specialties };
}
