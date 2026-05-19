/**
 * @impilo/mobile-nompilo — Nompilo assistant SDK for Impilo mobile apps.
 *
 * Entry points:
 *   import { nompilo, useNompilo } from "@impilo/mobile-nompilo";
 *
 * The SDK never silently fails. If the LLM gateway is unavailable, it returns
 * a deterministic role-aware fallback and clearly marks `usedFallback = true`.
 */

export type {
  NompiloRole,
  NompiloRiskLevel,
  NompiloMessage,
  NompiloMessageRole,
  NompiloContext,
  NompiloAskRequest,
  NompiloAskResult,
} from "./types";

export { configureNompilo, askNompilo, nompilo } from "./nompiloClient";
export type { Nompilo } from "./nompiloClient";

export { useNompilo } from "./hooks";
export type { UseNompiloOptions, UseNompiloState } from "./hooks";

export { NOMPILO_DISCLAIMER, buildFallbackResponse } from "./fallback";
