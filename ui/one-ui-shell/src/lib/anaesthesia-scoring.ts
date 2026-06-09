export const SCORE_TOOL_LABELS: Record<string, string> = {
  ASA: "ASA physical status (I–V)",
  MALLAMPATI: "Mallampati airway class",
  CORMACK_LEHANE: "Cormack-Lehane laryngoscopy grade",
  STOP_BANG: "STOP-BANG (OSA screening)",
  APFEL: "Apfel score (PONV risk)",
  RCRI: "Revised Cardiac Risk Index (Lee)",
  INTRAOP_VITALS: "Intra-operative vitals snapshot",
  ALDRETE: "Aldrete recovery score",
  PAIN: "Pain score (0–10)",
};

export type ScoreSuggestion = {
  score_type: string;
  reason: string;
  recommended: boolean;
  priority: number;
};

export type AnaesthesiaScore = {
  id: string;
  score_type: string;
  phase: string;
  total_score?: number | null;
  interpretation?: string;
  risk_band?: string;
  recorded_at?: string;
};
