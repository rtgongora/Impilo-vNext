/**
 * @impilo/mobile-trust — Trust Header Contract for Mobile
 *
 * Single source of truth for trust header names, types, and builder on mobile.
 * Mirrors ui/shared-ui/lib/contracts.ts for header names.
 * Mirrors services/tshepo-service/.../core/TrustHeaders.java for the canonical contract.
 */

export { TRUST_HEADERS, HARD_REQUIRED_HEADERS, COMMAND_METHODS } from "./headers";
export type { TrustHeaderKey, TrustHeaderValue, CommandMethod } from "./headers";

export type {
  PurposeOfUse,
  ActorType,
  ApiEnvelope,
  ApiErrorDetail,
  PagedResponse,
  SessionContext,
  AuthzVerdict,
  Obligations,
  StepUpChallenge,
} from "./types";

export {
  buildTrustHeaders,
  validateRequiredHeaders,
  generateId,
  MissingHeaderError,
} from "./headerBuilder";
