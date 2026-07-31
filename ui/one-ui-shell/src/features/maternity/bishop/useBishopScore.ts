import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type BishopInterpretation = "UNFAVOURABLE" | "INTERMEDIATE" | "FAVOURABLE" | "INCOMPLETE";

export interface BishopScoreAssessment {
  score: number | null;
  interpretation: BishopInterpretation;
  components: Record<string, number>;
  missing: string[];
  content_version: string;
}

export interface BishopAssessInput {
  dilation?: string | number | null;
  effacement?: string | number | null;
  station?: string | number | null;
  consistency?: string | number | null;
  position?: string | number | null;
}

export interface BishopFormAssessInput {
  formKey: string;
  answers: Record<string, unknown>;
}

interface AssessResponse {
  data: BishopScoreAssessment;
  meta?: Record<string, unknown>;
}

const ASSESS_PATH = "/internal/v1/clinical/maternal/bishop/assess";
const CLASSIFY_FORM_PATH = "/internal/v1/clinical/maternal/bishop/classify-form";

function bodyFromInput(input: BishopAssessInput): Record<string, unknown> {
  const body: Record<string, unknown> = {};
  if (input.dilation != null && input.dilation !== "") body.dilation = input.dilation;
  if (input.effacement != null && input.effacement !== "") body.effacement = input.effacement;
  if (input.station != null && input.station !== "") body.station = input.station;
  if (input.consistency != null && input.consistency !== "") body.consistency = input.consistency;
  if (input.position != null && input.position !== "") body.position = input.position;
  return body;
}

export function useBishopScoreAssess() {
  return useMutation({
    mutationFn: async (input: BishopAssessInput) => {
      const response = await apiClient.post<AssessResponse>(ASSESS_PATH, bodyFromInput(input));
      return response.data;
    },
  });
}

export function useBishopScoreClassifyForm() {
  return useMutation({
    mutationFn: async (input: BishopFormAssessInput) => {
      const response = await apiClient.post<AssessResponse>(CLASSIFY_FORM_PATH, input);
      return response.data;
    },
  });
}

export function isBishopScoreUnavailable(error: unknown): boolean {
  if (typeof error !== "object" || error === null) return false;
  const err = error as { status?: number; error?: string };
  return err.status === 502 || err.error === "bishop_score_unavailable";
}

export const BISHOP_FORM_KEY = "impilo.maternal.bishop.v1";

export const BISHOP_FIELD_OPTIONS = {
  dilation: [
    { code: "CLOSED", label: "Closed (0 cm)" },
    { code: "DIL_1_2", label: "1–2 cm" },
    { code: "DIL_3_4", label: "3–4 cm" },
    { code: "DIL_5_PLUS", label: "≥ 5 cm" },
  ],
  effacement: [
    { code: "EFF_0_30", label: "0–30%" },
    { code: "EFF_40_50", label: "40–50%" },
    { code: "EFF_60_70", label: "60–70%" },
    { code: "EFF_80_PLUS", label: "≥ 80%" },
  ],
  station: [
    { code: "STATION_PLUS3", label: "+3" },
    { code: "STATION_PLUS2", label: "+2" },
    { code: "STATION_MID", label: "+1 / 0 / −1" },
    { code: "STATION_MINUS2_PLUS", label: "≤ −2" },
  ],
  consistency: [
    { code: "FIRM", label: "Firm" },
    { code: "MEDIUM", label: "Medium" },
    { code: "SOFT", label: "Soft" },
  ],
  position: [
    { code: "POSTERIOR", label: "Posterior" },
    { code: "MID", label: "Mid" },
    { code: "ANTERIOR", label: "Anterior" },
  ],
} as const;

export const BISHOP_FIELD_LABELS: Record<string, string> = {
  dilation: "Cervical dilation",
  effacement: "Cervical effacement",
  station: "Fetal station",
  consistency: "Cervical consistency",
  position: "Cervical position",
};
