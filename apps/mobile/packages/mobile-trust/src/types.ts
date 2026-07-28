/**
 * Impilo vNext — Mobile Trust Types
 *
 * Shared type definitions used across all mobile packages.
 * Mirrors ui/shared-ui/lib/contracts.ts types.
 */

// Mirrors zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse exactly. TSHEPO denies any
// request whose purpose is outside this set (INVALID_PURPOSE at PolicyEngine Step 2, before
// rule matching), so a code missing here is unreachable and a code invented here is a denial.
export type PurposeOfUse =
  | "TREATMENT"
  | "CARE_COORDINATION"
  | "PAYMENT"
  | "OPERATIONS"
  | "RESEARCH"
  | "PUBLIC_HEALTH"
  | "SELF_SERVICE"
  | "REGULATORY_DUTY"
  | "EMERGENCY"
  | "BREAK_GLASS"
  | "SYSTEM";

export type ActorType = "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM";

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  error?: ApiErrorDetail;
  correlationId: string;
  timestamp: string;
}

export interface ApiErrorDetail {
  code: string;
  message: string;
  status: number;
  details?: Record<string, unknown>;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface SessionContext {
  tenantId: string;
  podId: string;
  actorId: string;
  actorType: ActorType;
  /** Health OS §6: activated Provider ID for regulated work (optional). */
  providerId?: string;
  accessToken: string;
  refreshToken?: string;
  expiresAt: number;
  facilityId?: string;
  facilityName?: string;
  departmentId?: string;
  wardId?: string;
  workspaceId?: string;
  workspaceName?: string;
  programmeId?: string;
  shiftId?: string;
  accessMode?: "ONLINE" | "OFFLINE" | "BREAK_GLASS";
  purposeOfUse: PurposeOfUse;
  deviceFingerprint?: string;
  /** Identity assurance level for this session. */
  assuranceLevel?: "UNVERIFIED" | "TEMPORARY" | "VERIFIED";
  /**
   * Minted duty-scoped work-context token (Ed25519, 15-min TTL) from
   * `POST /internal/v1/work-context/session/mode`. Carried as the sole
   * `x-work-context-token` header — never trust `workContextId`/`workMode` alone,
   * the server re-derives authority by decoding this token.
   */
  workContextToken?: string;
  workContextJti?: string;
  workContextExpiresAt?: number;
  /** Informational only (not sent as a header) — the resolved context this token was minted for. */
  workContextId?: string;
  /** Informational only (not sent as a header) — mirrors contracts/trust WorkMode values. */
  workMode?: string;
}

export interface AuthzVerdict {
  decision: "ALLOW" | "DENY" | "STEP_UP_REQUIRED";
  obligations?: Obligations;
  errorCode?: string;
  errorMessage?: string;
  stepUpMethods?: string[];
}

export interface Obligations {
  maxScope?: string;
  maskFields?: string[];
  loggingLevel: string;
  consentScopeRef?: string;
}

export interface StepUpChallenge {
  methods: string[];
  correlationId: string;
  originalRequest?: {
    method: string;
    url: string;
    body?: unknown;
  };
}
