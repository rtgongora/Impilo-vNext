/**
 * Quick service chips for the citizen "find care" lane.
 *
 * Each chip maps a plain-language need to an explicit TUSO capability token
 * (the `service` search param, which bypasses free-text interpretation). The
 * tokens are the SAME canonical tokens the backend taxonomy emits
 * (FindCareServiceTaxonomy) — no facility/provider truth is hard-coded here,
 * only the vocabulary of services a person can ask for.
 */

export interface ServiceChip {
  /** Human label shown on the chip. */
  label: string;
  /** Canonical TUSO capability token sent as `service`. */
  token: string;
  /** Optional emoji/glyph hint (decorative only). */
  hint?: string;
}

export const SERVICE_CHIPS: ServiceChip[] = [
  { label: "Maternity", token: "MATERNITY", hint: "🤰" },
  { label: "Antenatal care", token: "ANTENATAL" },
  { label: "Child health", token: "PAEDIATRICS", hint: "🧒" },
  { label: "Immunisation", token: "IMMUNIZATION", hint: "💉" },
  { label: "X-ray / imaging", token: "RADIOLOGY", hint: "🩻" },
  { label: "Ultrasound", token: "ULTRASOUND" },
  { label: "Laboratory", token: "LABORATORY", hint: "🧪" },
  { label: "Pharmacy", token: "PHARMACY", hint: "💊" },
  { label: "HIV medicines (ART)", token: "ART" },
  { label: "TB care", token: "TB" },
  { label: "Dialysis", token: "DIALYSIS" },
  { label: "Diabetes care", token: "DIABETES" },
  { label: "Mental health", token: "MENTAL_HEALTH" },
  { label: "Dentist", token: "DENTAL", hint: "🦷" },
  { label: "Eye care", token: "OPHTHALMOLOGY", hint: "👁️" },
  { label: "Surgery", token: "SURGERY" },
  { label: "Family planning", token: "FAMILY_PLANNING" },
  { label: "General / outpatient", token: "OPD" },
];

/** Zimbabwe provinces (for the manual location fallback). */
export const PROVINCES: string[] = [
  "Bulawayo",
  "Harare",
  "Manicaland",
  "Mashonaland Central",
  "Mashonaland East",
  "Mashonaland West",
  "Masvingo",
  "Matabeleland North",
  "Matabeleland South",
  "Midlands",
];

/**
 * Present a capability token as friendly text. Falls back to a title-cased
 * version of the token so an unknown token still reads sensibly.
 */
export function serviceTokenLabel(token: string | null | undefined): string | null {
  if (!token) return null;
  const chip = SERVICE_CHIPS.find((c) => c.token === token);
  if (chip) return chip.label;
  return token
    .toLowerCase()
    .split(/[_\s]+/)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
