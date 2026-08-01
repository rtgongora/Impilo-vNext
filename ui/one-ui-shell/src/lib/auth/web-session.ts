import type { AuthUser } from "@/hooks/useAuthStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface WebSessionResponse {
  data: {
    authenticated: true;
    user: {
      id: string;
      email?: string;
      displayName?: string;
      roles?: string[];
      identityAssuranceLevel?: string;
    };
    acr?: string;
    amr?: string[];
    authTime?: string;
    stepUpTime?: string | null;
    expiresAt?: string;
    /** True when this session was established with recovery codes (constrained recovery). */
    recovery?: boolean;
    /** Absolute expiry of a constrained recovery session (ISO-8601), when applicable. */
    recoveryExpiresAt?: string;
  };
}

/**
 * A constrained recovery session may only reach account-recovery surfaces. The shell uses
 * this to route the user into factor re-enrollment instead of the interrupted journey, and
 * to keep the recovery banner visible until a fresh ordinary sign-in replaces the session.
 */
export function isConstrainedRecoverySession(session: WebSessionResponse["data"]): boolean {
  return session.recovery === true;
}

function identityAssurance(value?: string): AuthUser["assuranceLevel"] {
  const normalized = value?.trim().toUpperCase();
  if (normalized === "VERIFIED" || normalized === "IAL2" || normalized === "IAL3") return "VERIFIED";
  if (normalized === "TEMPORARY" || normalized === "PROVISIONAL") return "TEMPORARY";
  return "UNVERIFIED";
}

function actorType(roles: string[]): AuthUser["actorType"] {
  if (roles.some((role) => ["SYSTEM_ADMIN", "SUPER_ADMIN", "DEVELOPER"].includes(role))) return "SYSTEM";
  if (roles.some((role) => ["CITIZEN", "CAREGIVER", "CARE_PARTNER"].includes(role)) &&
      !roles.some((role) => !["CITIZEN", "CAREGIVER", "CARE_PARTNER"].includes(role))) {
    return roles.includes("CITIZEN") ? "CITIZEN" : "CAREGIVER";
  }
  return "OPERATOR";
}

export function authUserFromWebSession(session: WebSessionResponse["data"]): AuthUser {
  const roles = session.user.roles ?? [];
  return {
    id: session.user.id,
    healthId: session.user.id,
    email: session.user.email ?? "",
    displayName: session.user.displayName ?? session.user.email ?? "",
    roles,
    actorType: actorType(roles),
    assuranceLevel: identityAssurance(session.user.identityAssuranceLevel),
    providerActivated: false,
    loginMethod: "email",
  };
}

export function beginOidcLogin(input: {
  returnTo?: string | null;
  loginHint?: string | null;
  requiredAcr?: "urn:impilo:aal1" | "urn:impilo:aal2" | "urn:impilo:aal3" | null;
}): void {
  const query = new URLSearchParams();
  query.set("returnTo", input.returnTo || "/home");
  if (input.loginHint?.trim()) query.set("loginHint", input.loginHint.trim());
  if (input.requiredAcr) query.set("acr", input.requiredAcr);
  window.location.assign(`/internal/v1/auth/oidc/authorize?${query.toString()}`);
}

export async function beginKeycloakAction(
  action: "CONFIGURE_TOTP" | "webauthn-register" | "webauthn-register-passwordless" |
    "CONFIGURE_RECOVERY_AUTHN_CODES" | "UPDATE_PASSWORD",
  returnTo: string,
): Promise<void> {
  const response = await apiClient.post<ApiResponse<{ authorizeUrl: string }>>(
    "/internal/v1/auth/oidc/action",
    { action, returnTo },
  );
  window.location.assign(response.data.authorizeUrl);
}
