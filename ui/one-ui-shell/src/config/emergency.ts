/**
 * Emergency contact configuration — SINGLE SOURCE OF TRUTH for the numbers shown
 * on every public surface. Do not hard-code emergency numbers in components.
 *
 * Provenance: values carried over from the previously approved public emergency
 * page (999 national emergency, 112 mobile alternative, 2019 MoHCC toll-free
 * health hotline). OPERATIONAL TRUTH GATE: before production go-live these MUST
 * be re-verified against the current official MoHCC publication; record the
 * verification date here when done.
 *
 * lastVerified: PENDING MINISTRY VERIFICATION (see
 * docs/roadmaps/identity-plane-external-prerequisites.md pattern for tracking).
 */

export interface EmergencyContact {
  /** Short label shown above the number. */
  label: string;
  /** Display form of the number. */
  number: string;
  /** Dialable value for tel: links. */
  tel: string;
  /** Optional qualifier, e.g. "toll-free". */
  note?: string;
}

export const EMERGENCY_CONTACTS: EmergencyContact[] = [
  { label: "Ambulance / Police / Fire", number: "999", tel: "999" },
  { label: "Alternative emergency", number: "112", tel: "112" },
  { label: "National health hotline", number: "2019", tel: "2019", note: "toll-free" },
];

/** Zimbabwe's ten provinces — used for manual emergency location capture. */
export const ZW_PROVINCES = [
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
] as const;
