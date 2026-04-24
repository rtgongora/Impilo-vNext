import type { ActorType, SessionInfo } from "@/lib/contracts";
import { decodeJwtPayload, readJwtExpSeconds } from "./jwt";
import type { TokenBuildRejectReason } from "./tokenSessionDiagnostics";

function pickNonEmptyString(v: unknown): string | null {
  if (typeof v !== "string") return null;
  const t = v.trim();
  return t.length > 0 ? t : null;
}

/**
 * Stable person/client identifier for trust headers. Order matches Keycloak
 * access-token variability (some tokens omit `sub`).
 */
export function resolveKeycloakActorId(
  payload: Record<string, unknown>
): string | null {
  const hi = pickNonEmptyString(payload.health_id);
  if (hi) return hi;
  const sub = pickNonEmptyString(payload.sub);
  if (sub) return sub;
  const pu = pickNonEmptyString(payload.preferred_username);
  if (pu) return pu;
  const em = pickNonEmptyString(payload.email);
  if (em) return em;
  const azp = pickNonEmptyString(payload.azp);
  const ss = pickNonEmptyString(payload.session_state);
  if (azp && ss) return `${azp}:${ss}`;
  const iat = payload.iat;
  if (azp && (typeof iat === "number" || (typeof iat === "string" && /^\d+$/.test(iat)))) {
    return `${azp}:${iat}`;
  }
  if (azp) return azp;
  return null;
}

export function resolveKeycloakDisplayName(
  payload: Record<string, unknown>,
  actorId: string
): string {
  const name = pickNonEmptyString(payload.name);
  if (name) return name;
  const gn = pickNonEmptyString(payload.given_name);
  const fn = pickNonEmptyString(payload.family_name);
  const combined = [gn, fn].filter(Boolean).join(" ").trim();
  if (combined) return combined;
  const pu = pickNonEmptyString(payload.preferred_username);
  if (pu) return pu;
  const em = pickNonEmptyString(payload.email);
  if (em) return em;
  return actorId;
}

function collectKeycloakRoles(payload: Record<string, unknown>): string[] {
  const out: string[] = [];
  const ra = payload.realm_access as { roles?: unknown } | undefined;
  if (ra && Array.isArray(ra.roles)) {
    for (const r of ra.roles) {
      if (typeof r === "string" && r.trim()) out.push(r);
    }
  }
  const res = payload.resource_access as
    | Record<string, { roles?: unknown }>
    | undefined;
  if (res && typeof res === "object") {
    for (const v of Object.values(res)) {
      if (v && Array.isArray(v.roles)) {
        for (const r of v.roles) {
          if (typeof r === "string" && r.trim()) out.push(r);
        }
      }
    }
  }
  return out;
}

function inferActorType(roleNames: string[]): ActorType {
  const upper = roleNames.map((r) => r.toUpperCase());
  if (upper.some((r) => r.includes("CITIZEN"))) return "CITIZEN";
  if (upper.some((r) => r.includes("OPERATOR") || r === "ADMIN"))
    return "OPERATOR";
  if (
    upper.some((r) =>
      ["CLINICIAN", "NURSE", "DOCTOR", "PHYSICIAN", "PROVIDER"].some((k) =>
        r.includes(k)
      )
    )
  )
    return "PROVIDER";
  if (upper.some((r) => r.includes("SYSTEM"))) return "SYSTEM";
  return "PROVIDER";
}

export interface OidcSessionBuild {
  session: SessionInfo;
  tenantId: string | null;
  facilityId: string | null;
}

export function getSessionBuildFailureReason(
  accessToken: string,
  expiresInFallback?: number
): { rejectReason: TokenBuildRejectReason; missingRequiredField: string | null } {
  const payload = decodeJwtPayload<Record<string, unknown>>(accessToken);
  if (!payload) {
    return { rejectReason: "decode_failed", missingRequiredField: "jwt_payload" };
  }

  let expSec = readJwtExpSeconds(payload);
  if (expSec === null) {
    const fb = expiresInFallback;
    if (typeof fb === "number" && Number.isFinite(fb) && fb > 0) {
      expSec = Math.floor(Date.now() / 1000) + Math.floor(fb);
    }
  }
  if (expSec === null) {
    return {
      rejectReason: "missing_exp_and_expires_in",
      missingRequiredField: "exp",
    };
  }

  const actorId = resolveKeycloakActorId(payload);
  if (!actorId) {
    return {
      rejectReason: "missing_identity",
      missingRequiredField: "actorId",
    };
  }

  return { rejectReason: "unknown", missingRequiredField: null };
}

export function buildSessionFromKeycloakTokens(params: {
  accessToken: string;
  refreshToken?: string;
  /** From token endpoint `expires_in` when JWT has no usable `exp` claim */
  expiresInFallback?: number;
}): OidcSessionBuild | null {
  const payload = decodeJwtPayload<Record<string, unknown>>(
    params.accessToken
  );
  if (!payload) return null;

  const asRecord = payload;

  let expSec = readJwtExpSeconds(asRecord);
  if (expSec === null) {
    const fb = params.expiresInFallback;
    if (typeof fb === "number" && Number.isFinite(fb) && fb > 0) {
      expSec = Math.floor(Date.now() / 1000) + Math.floor(fb);
    }
  }
  if (expSec === null) return null;

  const actorId = resolveKeycloakActorId(asRecord);
  if (!actorId) return null;

  const roles = collectKeycloakRoles(asRecord);
  const actorTypeSource =
    roles.length > 0 ? ("realm_roles" as const) : ("default_no_roles" as const);
  const actorType = roles.length > 0 ? inferActorType(roles) : "PROVIDER";

  const displayName = resolveKeycloakDisplayName(asRecord, actorId);

  const session: SessionInfo = {
    actorId,
    actorType,
    actorTypeSource,
    displayName,
    email: pickNonEmptyString(payload.email) ?? undefined,
    roles,
    accessToken: params.accessToken,
    refreshToken: params.refreshToken,
    expiresAt: expSec * 1000,
  };

  const tenantId = pickNonEmptyString(payload.tenant_id);
  const facilityId = pickNonEmptyString(payload.facility_id);

  return { session, tenantId, facilityId };
}
