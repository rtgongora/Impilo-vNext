import { decodeJwtPayload, readJwtExpSeconds } from "./jwt";

export type TokenBuildRejectReason =
  | "decode_failed"
  | "missing_identity"
  | "missing_exp_and_expires_in"
  | "unknown";

export interface TokenBuildFailureDiagnostics {
  tokenEndpointHttp: number;
  accessTokenPresent: boolean;
  tokenType: string | null;
  expiresIn: number | null;
  accessTokenJwtShape: boolean;
  decodeSucceeded: boolean;
  claimKeysIfDecoded: string[] | null;
  hasNumericOrStringExpInPayload: boolean | null;
  rejectReason: TokenBuildRejectReason;
  missingRequiredField: string | null;
  hasSub: boolean | null;
  hasPreferredUsername: boolean | null;
  hasEmail: boolean | null;
  hasAzp: boolean | null;
  hasSessionState: boolean | null;
  hasIat: boolean | null;
  hasExpInJwt: boolean | null;
  hasExpiresInFromTokenEndpoint: boolean | null;
}

function hasNonEmptyString(
  payload: Record<string, unknown> | null,
  key: string
): boolean | null {
  if (!payload) return null;
  const v = payload[key];
  return typeof v === "string" && v.trim().length > 0;
}

export function buildTokenBuildFailureDiagnostics(params: {
  tokenEndpointHttp: number;
  accessToken: string | undefined;
  tokenType: unknown;
  expiresIn: unknown;
  rejectReason: TokenBuildRejectReason;
  missingRequiredField: string | null;
}): TokenBuildFailureDiagnostics {
  const at = params.accessToken ?? "";
  const payload = at ? decodeJwtPayload<Record<string, unknown>>(at) : null;
  const decodeSucceeded = payload !== null;
  const claimKeys =
    decodeSucceeded && payload ? Object.keys(payload).sort() : null;
  const expRead = payload ? readJwtExpSeconds(payload) : null;
  const expiresInNum =
    typeof params.expiresIn === "number" && Number.isFinite(params.expiresIn)
      ? params.expiresIn
      : null;

  return {
    tokenEndpointHttp: params.tokenEndpointHttp,
    accessTokenPresent: Boolean(at),
    tokenType: typeof params.tokenType === "string" ? params.tokenType : null,
    expiresIn: expiresInNum,
    accessTokenJwtShape: at.split(".").length === 3,
    decodeSucceeded,
    claimKeysIfDecoded: claimKeys,
    hasNumericOrStringExpInPayload: payload ? expRead !== null : null,
    rejectReason: params.rejectReason,
    missingRequiredField: params.missingRequiredField,
    hasSub: hasNonEmptyString(payload, "sub"),
    hasPreferredUsername: hasNonEmptyString(payload, "preferred_username"),
    hasEmail: hasNonEmptyString(payload, "email"),
    hasAzp: hasNonEmptyString(payload, "azp"),
    hasSessionState: hasNonEmptyString(payload, "session_state"),
    hasIat: payload
      ? typeof payload.iat === "number" ||
        (typeof payload.iat === "string" && /^\d+$/.test(payload.iat))
      : null,
    hasExpInJwt: payload ? expRead !== null : null,
    hasExpiresInFromTokenEndpoint:
      expiresInNum !== null && expiresInNum > 0 ? true : false,
  };
}
