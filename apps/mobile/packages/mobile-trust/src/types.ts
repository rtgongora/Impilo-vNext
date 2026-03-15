/**
 * Impilo vNext — Mobile Trust Types
 *
 * Shared type definitions used across all mobile packages.
 * Mirrors ui/shared-ui/lib/contracts.ts types.
 */

export type PurposeOfUse =
  | "TREATMENT"
  | "PAYMENT"
  | "OPERATIONS"
  | "RESEARCH"
  | "PUBLIC_HEALTH"
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
  accessToken: string;
  refreshToken?: string;
  expiresAt: number;
  facilityId?: string;
  facilityName?: string;
  workspaceId?: string;
  workspaceName?: string;
  shiftId?: string;
  purposeOfUse: PurposeOfUse;
  deviceFingerprint?: string;
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
