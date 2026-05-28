import type { ClinicalFormRuntimeContext, FormValues } from "../types";

export interface DecisionSupportAlert {
  id: string;
  severity: "info" | "warning" | "critical";
  message: string;
  relatedFieldIds?: string[];
}

/**
 * Placeholder rules engine — swap for rules-service / vito CDS call with same contract.
 */
export function runAncContact1DecisionSupport(
  _ctx: ClinicalFormRuntimeContext,
  values: FormValues,
): DecisionSupportAlert[] {
  const alerts: DecisionSupportAlert[] = [];
  const gravida = Number(values.gravida);
  if (!Number.isNaN(gravida) && gravida > 12) {
    alerts.push({
      id: "gravida-implausible",
      severity: "warning",
      message: "Gravida >12 is unusual — verify parity/gravida with patient.",
      relatedFieldIds: ["gravida"],
    });
  }
  const referral = String(values.referral_need ?? "");
  if (referral === "emergency_obstetrics" || referral === "higher_level_anc") {
    alerts.push({
      id: "referral-escalation",
      severity: "critical",
      message: "Referral selected requires urgent obstetric assessment per local protocol.",
      relatedFieldIds: ["referral_need"],
    });
  }
  return alerts;
}
