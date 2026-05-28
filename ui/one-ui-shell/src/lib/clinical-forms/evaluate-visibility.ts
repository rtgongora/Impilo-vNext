import type { ClinicalFormFieldDefinition, ClinicalFormRuntimeContext, FieldVisibilityRule } from "./types";

function hasAnyRole(userRoles: string[], required: string[]): boolean {
  const set = new Set(userRoles.map((r) => r.toUpperCase()));
  return required.some((r) => set.has(r.toUpperCase()));
}

function hasExcludedRole(userRoles: string[], excluded: string[]): boolean {
  const set = new Set(userRoles.map((r) => r.toUpperCase()));
  return excluded.some((r) => set.has(r.toUpperCase()));
}

export function fieldVisible(rule: FieldVisibilityRule | undefined, ctx: ClinicalFormRuntimeContext): boolean {
  if (!rule) return true;
  const { patient, encounterType, providerRoles } = ctx;

  if (rule.requireSex?.length && !rule.requireSex.includes(patient.sex)) return false;
  if (rule.excludeSex?.length && rule.excludeSex.includes(patient.sex)) return false;

  if (rule.requireAgeBands?.length && !rule.requireAgeBands.includes(patient.ageBand)) return false;
  if (rule.excludeAgeBands?.length && rule.excludeAgeBands.includes(patient.ageBand)) return false;

  if (rule.requirePregnant === true && patient.pregnant !== true) return false;
  if (rule.requirePregnant === false && patient.pregnant === true) return false;

  if (rule.requireProgrammes?.length) {
    const p = new Set(patient.programmes.map((x) => x.toUpperCase()));
    if (!rule.requireProgrammes.some((x) => p.has(x.toUpperCase()))) return false;
  }
  if (rule.excludeProgrammes?.length) {
    const p = new Set(patient.programmes.map((x) => x.toUpperCase()));
    if (rule.excludeProgrammes.some((x) => p.has(x.toUpperCase()))) return false;
  }

  if (rule.requireEncounterTypes?.length) {
    if (!rule.requireEncounterTypes.map((t) => t.toUpperCase()).includes(encounterType.toUpperCase())) return false;
  }
  if (rule.excludeEncounterTypes?.length) {
    if (rule.excludeEncounterTypes.map((t) => t.toUpperCase()).includes(encounterType.toUpperCase())) return false;
  }

  if (rule.requireAnyRole?.length && !hasAnyRole(providerRoles, rule.requireAnyRole)) return false;
  if (rule.excludeRoles?.length && hasExcludedRole(providerRoles, rule.excludeRoles)) return false;

  return true;
}

export function visibleFieldsForSection(
  fields: ClinicalFormFieldDefinition[],
  ctx: ClinicalFormRuntimeContext,
): ClinicalFormFieldDefinition[] {
  return fields.filter((f) => fieldVisible(f.visibility, ctx));
}
