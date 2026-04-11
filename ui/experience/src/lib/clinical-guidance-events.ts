/** Dispatched from EDLIZ / clinical guidance UI for orders and other shells to consume (opt-in). */
export const CLINICAL_ORDER_DRAFT_EVENT = "impilo:clinical-order-draft" as const;

export type ClinicalOrderDraftDetail = {
  source: "edliz-prescribing";
  patientId?: string;
  encounterId?: string;
  generic?: string;
  doseMg?: string;
  alerts?: Array<Record<string, unknown>>;
  therapyEvaluation?: unknown;
};
