/**
 * Nompilo mobile SDK — type contracts.
 *
 * Nompilo is the warm, helpful Impilo assistant. On mobile it is *not* allowed
 * to:
 *   - generate clinical decisions
 *   - generate prescriptions, doses or treatments
 *   - generate definitive legal, financial or insurance verdicts
 *   - replace Tshepo authorization or Vito identity flows
 *
 * It *is* allowed to:
 *   - help users navigate the app
 *   - explain status messages in plain language
 *   - summarise dashboards, deliveries, integration states
 *   - suggest next actions the user can take inside the app
 *
 * If the LLM gateway is unavailable, the SDK falls back to deterministic
 * mobile-side responses so the assistant never silently fails.
 */

export type NompiloRole =
  | "PROVIDER"
  | "CITIZEN"
  | "COURIER"
  | "SUPERVISOR"
  | "OUTREACH"
  | "ADMIN";

export type NompiloRiskLevel = "LOW" | "MODERATE" | "HIGH";

export type NompiloMessageRole = "user" | "assistant" | "system";

export interface NompiloMessage {
  role: NompiloMessageRole;
  content: string;
  /** ISO timestamp for UI rendering. */
  at?: string;
}

export interface NompiloContext {
  /** Caller role — drives system prompt and guardrails. */
  role: NompiloRole;
  /** Screen / surface the assistant is being invoked from. */
  surface?: string;
  /** Free-form structured hints, e.g. { deliveryId, status, facility }. */
  hints?: Record<string, string | number | boolean | null | undefined>;
  /** Purpose-of-use override forwarded to trust headers. */
  purposeOfUse?: string;
}

export interface NompiloAskRequest {
  prompt: string;
  history?: NompiloMessage[];
  context?: NompiloContext;
  /** Risk tier of the prompt — MODERATE by default. */
  riskLevel?: NompiloRiskLevel;
}

export interface NompiloAskResult {
  /** The assistant's reply text. */
  reply: string;
  /** Optional follow-up suggestions the UI can show as chips. */
  suggestions?: string[];
  /** Whether the reply came from the real model. */
  modelAvailable: boolean;
  /** Whether the SDK fell back to the deterministic local responder. */
  usedFallback: boolean;
  /** Correlation id (when the request did reach the model). */
  correlationId?: string;
  /** Disclaimer string applied. */
  disclaimer: string;
}
