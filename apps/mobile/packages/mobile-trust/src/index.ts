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
  buildPublicTrustHeaders,
  validateRequiredHeaders,
  generateId,
  MissingHeaderError,
} from "./headerBuilder";
export type { PublicHeaderContext } from "./headerBuilder";

export {
  WORK_MODES,
  WORK_MODE_DEFINITIONS,
  grantsIdentifiedClinicalRead,
} from "./workMode";
export type {
  WorkMode,
  WorkModeAnchorKind,
  WorkModeClinicalDataAccess,
  WorkModeDefinition,
  ResolvedWorkContextView,
  WorkContextSourceStatus,
  ResolvedWorkContextsAttributes,
  WorkModeSessionAttributes,
} from "./workMode";

export {
  PUSH_APP_NAME,
  PUSH_GENERIC_NEW_MESSAGE_BODY,
  PUSH_GENERIC_HEALTH_UPDATE_BODY,
  PUSH_APPOINTMENT_CHANGED_BODY,
  PUSH_CONSENT_ACTION_REQUIRED_BODY,
  PUSH_WALLET_OR_BILLING_UPDATE_BODY,
  PUSH_QUEUE_TURN_BODY,
  buildGenericHealthNotificationBody,
  genericNotificationTitle,
} from "./pushNotificationPrivacy";

export {
  isTrustChallengeStatus,
  parseTrustChallenge,
  TRUST_CHALLENGE_STATUSES,
  type TrustChallenge,
  type TrustChallengeDecisionName,
  type TrustChallengeKind,
} from "./challenge";
