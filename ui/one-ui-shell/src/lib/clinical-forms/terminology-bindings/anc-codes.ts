/**
 * Illustrative terminology bindings for ANC exemplar (LOINC / SNOMED style).
 * Production should load from terminology-service / ValueSet expansion.
 */

export const ANC_LOINC = {
  gravida: "11977-6", // pregnancies
  para: "11996-6", // live births
  lmp: "8665-2", // LMP
  edd: "11778-8", // EDD
  syphilisScreen: "31147-2", // RPR / syphilis screen — illustrative
  hivStatus: "75622-1", // HIV status
} as const;

export const ANC_SNOMED = {
  antenatalFirstContact: "424441002", // First antenatal visit (illustrative)
  referralReasonShortnessOfBreath: "267036007", // illustrative
} as const;
