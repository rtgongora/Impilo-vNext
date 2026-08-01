/**
 * Canonical Tshepo trust-plane contracts — TypeScript mirror of
 * `libs/tshepo-contracts/.../v1` (Checkpoint 2).
 *
 * Version: tshepo.trust.v1
 * Do not populate authoritative fields from unvalidated client headers.
 */

export const TRUST_CONTRACT_VERSION_V1 = "tshepo.trust.v1" as const;

export type TrustChallengeDecision =
  | "ALLOW"
  | "DENY"
  | "AUTHENTICATION_REQUIRED"
  | "STEP_UP_REQUIRED"
  | "CONTEXT_REQUIRED"
  | "AUTHORITY_REQUIRED"
  | "CONSENT_REQUIRED"
  | "APPROVAL_REQUIRED"
  | "BREAK_GLASS_AVAILABLE"
  | "REAUTHENTICATION_REQUIRED"
  | "RECOVERY_REQUIRED"
  | "TEMPORARILY_UNAVAILABLE";

export type RecoveryAuthenticationState = "NONE" | "CONSTRAINED_RECOVERY";

export type LawfulBasisType =
  | "EXPLICIT_CONSENT"
  | "DIRECT_CARE_RELATIONSHIP"
  | "STATUTORY_AUTHORITY"
  | "PUBLIC_HEALTH_AUTHORITY"
  | "EMERGENCY_BREAK_GLASS"
  | "OTHER_GOVERNED";

export type OperatingMode =
  | "NORMAL"
  | "DEGRADED"
  | "OFFLINE"
  | "RECOVERY"
  | "BREAK_GLASS"
  | "READ_ONLY_EMERGENCY";

export interface IdentityAssurance {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  ial: number;
  proofingMethod?: string;
  evidenceReference?: string;
}

export interface AuthenticationAssurance {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  aal: number;
  amr: string[];
  authenticationTime?: string;
  stepUpTime?: string;
  phishingResistant: boolean;
  sessionId?: string;
  flowId?: string;
  recoveryState: RecoveryAuthenticationState;
}

export interface WorkloadSubject {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  workloadId: string;
  serviceName?: string;
  kubernetesServiceAccount?: string;
  keycloakClientId?: string;
  audience?: string;
}

export interface WorkloadAssurance {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  credentialType: string;
  issuedAt?: string;
  expiresAt?: string;
  mTlsBound: boolean;
  issuer?: string;
}

export interface HumanSubjectRef {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  healthId: string;
  actorType?: string;
  providerId?: string;
}

export interface DelegatedHumanContext {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  humanSubject: HumanSubjectRef;
  activeContext?: unknown;
  authority?: unknown;
  purposeOfUse?: string;
  audience?: string;
  allowedActions: string[];
  allowedScopes: string[];
  authenticationAssurance?: AuthenticationAssurance;
  delegationDepth: number;
  expiresAt?: string;
  correlationId?: string;
  requestId?: string;
}

export interface DelegationChain {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  hops: DelegatedHumanContext[];
  maxDepth: number;
}

export interface ServiceRequestContext {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  destinationServiceId: string;
  method?: string;
  path?: string;
  action?: string;
  resourceType?: string;
  resourceId?: string;
  sensitivityClass?: string;
}

export interface ConsentDecision {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  permitted: boolean;
  consentId?: string;
  provision?: string;
  allowedScopes: string[];
  deniedScopes: string[];
  reasonCode?: string;
  subjectRef?: string;
  purpose?: string;
}

export interface LawfulBasisDecision {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  permitted: boolean;
  basisType: Exclude<LawfulBasisType, "EXPLICIT_CONSENT">;
  basisReference?: string;
  reasonCode?: string;
  subjectRef?: string;
  purpose?: string;
}

export interface AsyncExecutionReference {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  referenceId: string;
  workerWorkload?: WorkloadSubject;
  originalHumanSubject?: HumanSubjectRef;
  allowedActions: string[];
  allowedScopes: string[];
  audience?: string;
  purposeOfUse?: string;
  delegationDepth: number;
  expiresAt?: string;
  revoked: boolean;
  correlationId?: string;
}

export interface TrustAuditEvent {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  eventId: string;
  timestamp: string;
  requestId?: string;
  traceId?: string;
  decisionId?: string;
  callingWorkloadId?: string;
  destinationWorkloadId?: string;
  humanActorId?: string;
  activeContextId?: string;
  delegationReference?: string;
  purposeOfUse?: string;
  action?: string;
  resourceReference?: string;
  credentialType?: string;
  identityAssuranceLevel?: number;
  authenticationAal?: number;
  recoveryState?: RecoveryAuthenticationState;
  policyVersion?: string;
  enforcementPoint?: string;
  result?: string;
  attributes?: Record<string, string>;
}

/** Safe challenge fields only — no policy expressions, tokens, or clinical data. */
export interface TrustChallengeOutcome {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  decision: TrustChallengeDecision;
  reason_code?: string;
  user_message_key?: string;
  required_action?: string;
  required_assurance?: number;
  allowed_authentication_methods?: string[];
  context_options?: string[];
  consent_request_reference?: string;
  approval_reference?: string;
  continuation_reference?: string;
  decision_id?: string;
  policy_version?: string;
  expires_at?: string;
  support_reference?: string;
}

export interface TrustDecisionInput {
  contractVersion: typeof TRUST_CONTRACT_VERSION_V1;
  humanSubject?: HumanSubjectRef;
  callingWorkload: WorkloadSubject;
  originalClientId?: string;
  destination: ServiceRequestContext;
  sessionId?: string;
  flowId?: string;
  identityAssurance?: IdentityAssurance;
  authenticationAssurance?: AuthenticationAssurance;
  workloadAssurance?: WorkloadAssurance;
  activeContext?: unknown;
  authority?: unknown;
  tenantId?: string;
  facilityId?: string;
  councilId?: string;
  action?: string;
  resourceType?: string;
  resourceId?: string;
  sensitivityClass?: string;
  purposeOfUse?: string;
  consentDecision?: ConsentDecision;
  lawfulBasisDecision?: LawfulBasisDecision;
  delegationChain?: DelegationChain;
  emergencyBreakGlass?: boolean;
  operatingMode: OperatingMode;
  policyVersion?: string;
  requestId?: string;
  traceId?: string;
  decisionId?: string;
  correlationId?: string;
}

/** Legacy AuthzVerdict remains for proven UI callers until TrustChallenge UX lands. */
export type LegacyAuthzVerdict = "ALLOW" | "DENY" | "STEP_UP_REQUIRED";

export function grantsOrdinaryWorkforceAal2(a: AuthenticationAssurance): boolean {
  return a.aal >= 2 && a.recoveryState === "NONE";
}
