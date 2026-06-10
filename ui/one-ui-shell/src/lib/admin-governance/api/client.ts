/**
 * Base Administration & Governance BFF client.
 */

import { apiClient } from "@/lib/api-client";
import type {
  AdminGovernanceActionResponse,
  AdminGovernanceEnvelope,
  ApiClientErrorLike,
} from "./types";

const BASE = "/internal/v1/admin-governance";

function isUnavailableError(error: unknown): boolean {
  if (!error || typeof error !== "object") return false;
  const e = error as ApiClientErrorLike & { statusCode?: number };
  const code = e.error?.code ?? "";
  const status = e.status ?? e.statusCode;
  return status === 404 || status === 502 || status === 503 || code === "SERVICE_UNAVAILABLE";
}

export function pendingBackendResponse(
  friendlyTitle: string,
  friendlyMessage: string,
  requestedAction?: string,
): AdminGovernanceActionResponse {
  return {
    status: "pending_backend",
    friendlyTitle,
    friendlyMessage,
    allowedNextActions: ["Submit access request", "Contact Support"],
    blockedReason: "pending_backend",
    integrationStatus: "pending_backend",
    policyDecision: {
      allowed: false,
      policyPackage: "impilo.organisation",
      policyVersion: "1.6.0",
      decisionId: "pending-backend",
      warnings: [friendlyMessage],
    },
    payload: requestedAction ? { requestedAction } : undefined,
  };
}

export function unwrapAttributes<T>(response: AdminGovernanceEnvelope<T>): T {
  return response.data.attributes;
}

export async function postAdminGovernance<TAttributes>(
  path: string,
  body: unknown,
  pendingMessage = "Administration & Governance backend integration is pending.",
): Promise<TAttributes | AdminGovernanceActionResponse> {
  try {
    const response = await apiClient.post<AdminGovernanceEnvelope<TAttributes>>(`${BASE}${path}`, body);
    return unwrapAttributes(response);
  } catch (error) {
    if (isUnavailableError(error)) {
      return pendingBackendResponse("Backend integration pending", pendingMessage);
    }
    throw error;
  }
}

export async function getAdminGovernance<TAttributes>(
  path: string,
  pendingMessage = "Administration & Governance backend integration is pending.",
): Promise<TAttributes | AdminGovernanceActionResponse> {
  try {
    const response = await apiClient.get<AdminGovernanceEnvelope<TAttributes>>(`${BASE}${path}`);
    return unwrapAttributes(response);
  } catch (error) {
    if (isUnavailableError(error)) {
      return pendingBackendResponse("Backend integration pending", pendingMessage);
    }
    throw error;
  }
}

export function isPendingBackend(
  value: AdminGovernanceActionResponse | unknown,
): value is AdminGovernanceActionResponse {
  return (
    typeof value === "object" &&
    value !== null &&
    "status" in value &&
    (value as AdminGovernanceActionResponse).status === "pending_backend"
  );
}

export function isActionResponse(value: unknown): value is AdminGovernanceActionResponse {
  return typeof value === "object" && value !== null && "friendlyTitle" in value && "status" in value;
}

export { BASE as ADMIN_GOVERNANCE_BASE_PATH };
