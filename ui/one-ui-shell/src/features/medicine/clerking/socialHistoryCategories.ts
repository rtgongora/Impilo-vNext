/**
 * Governed pct_social_history categories (V106). Mirror of SocialHistoryVocabulary.java.
 * Simba wellness remains OUT_OF_PACK — DIET/PHYSICAL_ACTIVITY/SLEEP here are clinician-documented.
 */

export interface SocialHistoryCategoryDef {
  code: string;
  label: string;
  group: "substance" | "occupation_exposure" | "lifestyle" | "sdoh";
}

export const GOVERNED_SOCIAL_HISTORY_CATEGORIES: SocialHistoryCategoryDef[] = [
  { code: "TOBACCO", label: "Tobacco", group: "substance" },
  { code: "ALCOHOL", label: "Alcohol", group: "substance" },
  { code: "SUBSTANCE", label: "Substance use", group: "substance" },
  { code: "OCCUPATION", label: "Occupation", group: "occupation_exposure" },
  { code: "ENVIRONMENTAL_EXPOSURE", label: "Environmental exposure", group: "occupation_exposure" },
  { code: "DIET", label: "Diet (clinician-documented)", group: "lifestyle" },
  { code: "PHYSICAL_ACTIVITY", label: "Physical activity (clinician-documented)", group: "lifestyle" },
  { code: "SLEEP", label: "Sleep (clinician-documented)", group: "lifestyle" },
  { code: "SOCIAL_SUPPORT", label: "Social support", group: "sdoh" },
  { code: "HOUSING", label: "Housing", group: "sdoh" },
  { code: "FOOD_SECURITY", label: "Food security", group: "sdoh" },
];

export const GOVERNED_SOCIAL_HISTORY_CODES = GOVERNED_SOCIAL_HISTORY_CATEGORIES.map((c) => c.code);

export function isGovernedSocialCategory(code: string): boolean {
  return GOVERNED_SOCIAL_HISTORY_CODES.includes(code.trim().toUpperCase());
}

export function labelForSocialCategory(code: string): string {
  const found = GOVERNED_SOCIAL_HISTORY_CATEGORIES.find(
    (c) => c.code === code.trim().toUpperCase()
  );
  return found?.label ?? code;
}
