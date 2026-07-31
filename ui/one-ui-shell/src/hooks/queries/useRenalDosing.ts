"use client";

import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type RenalDosingLevel = "ADJUST" | "CAUTION" | "NO_CHANGE" | "UNKNOWN";

export interface RenalDosingAdvice {
  level: RenalDosingLevel;
  message: string;
  computed: boolean;
  drug_class: string | null;
  drug_class_display?: string | null;
  egfr?: number | null;
  content_version: string;
}

export interface RenalDosingRequest {
  egfr: number | null;
  drugClass: string;
}

export function useRenalDosingAdvise() {
  return useMutation<ApiResponse<RenalDosingAdvice>, unknown, RenalDosingRequest>({
    mutationFn: ({ egfr, drugClass }) =>
      apiClient.post<ApiResponse<RenalDosingAdvice>>("/internal/v1/medicine/renal-dosing/advise", {
        egfr,
        drugClass,
      }),
  });
}

/** Drug classes the renal-dosing advisor recognises (mirrors medicine-classification.json). */
export const RENAL_DOSING_DRUG_CLASSES = [
  { key: "BIGUANIDE", label: "Biguanide (metformin)" },
  { key: "NSAID", label: "NSAID" },
  { key: "ACE_INHIBITOR", label: "ACE inhibitor" },
  { key: "ARB", label: "ARB" },
  { key: "SULFONYLUREA", label: "Sulfonylurea" },
  { key: "AMINOGLYCOSIDE", label: "Aminoglycoside" },
  { key: "OPIOID", label: "Opioid" },
  { key: "ANTICOAGULANT", label: "Anticoagulant" },
  { key: "THIAZIDE", label: "Thiazide diuretic" },
  { key: "LOOP_DIURETIC", label: "Loop diuretic" },
  { key: "STATIN", label: "Statin" },
] as const;
