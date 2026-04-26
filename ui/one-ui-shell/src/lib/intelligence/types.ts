/**
 * Health Intelligence plane (BFF) — client types.
 * Mirrors {@code HealthIntelligenceService} query contracts; outputs remain human-governed suggestions.
 */

export type IntelligenceQueryType =
  | "SEARCH_FUSED"
  | "SUMMARY_QUEUE"
  | "SUMMARY_PROVIDER"
  | "SUMMARY_CLIENT"
  | "HELP_DESK_ASSIST"
  | "DATA_QUALITY_HINTS"
  | "ANOMALY_SCAN_OPERATIONS"
  | "WORKFLOW_ASSIST"
  | "LEARNING_NUDGE";

export interface IntelligenceContext {
  subjectType?: string;
  subjectId?: string;
  facilityId?: string;
  workflowCode?: string;
  domain?: string;
  entityType?: string;
  visibilityTier?: string;
  [key: string]: unknown;
}

export interface IntelligenceQueryRequest {
  queryType: IntelligenceQueryType;
  context?: IntelligenceContext;
  input?: Record<string, unknown>;
}

/** BFF wraps payload under {@code data}. */
export interface IntelligenceQueryResponseEnvelope {
  data: Record<string, unknown>;
}

export interface ShellFusionHint {
  id: string;
  source: string;
  title: string;
  subtitle: string;
  href?: string;
}
