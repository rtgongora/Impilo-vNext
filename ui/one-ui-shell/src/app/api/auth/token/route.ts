import { NextResponse } from "next/server";
import { cookies } from "next/headers";

import { safeKeycloakTokenErrorDetail } from "@/lib/auth/keycloakTokenError";
import {
  buildSessionFromKeycloakTokens,
  getSessionBuildFailureReason,
} from "@/lib/auth/sessionFromToken";
import { buildTokenBuildFailureDiagnostics } from "@/lib/auth/tokenSessionDiagnostics";
import {
  OIDC_COOKIE_STATE,
  OIDC_COOKIE_VERIFIER,
  getKeycloakClientId,
  getKeycloakIssuerUrl,
  getShellPublicUrl,
} from "@/lib/auth/shellOidcEnv";

type TokenBody = {
  code?: string;
  state?: string;
};

const clearPkceCookie = {
  httpOnly: true,
  sameSite: "lax" as const,
  path: "/",
  maxAge: 0,
  secure: process.env.NODE_ENV === "production",
};

function clearPkceOnResponse(res: NextResponse): NextResponse {
  res.cookies.set(OIDC_COOKIE_STATE, "", clearPkceCookie);
  res.cookies.set(OIDC_COOKIE_VERIFIER, "", clearPkceCookie);
  return res;
}

export async function POST(request: Request) {
  let body: TokenBody;
  try {
    body = (await request.json()) as TokenBody;
  } catch {
    return clearPkceOnResponse(
      NextResponse.json({ error: "invalid_json" }, { status: 400 })
    );
  }

  const code = typeof body.code === "string" ? body.code : "";
  const state = typeof body.state === "string" ? body.state : "";
  if (!code || !state) {
    return clearPkceOnResponse(
      NextResponse.json({ error: "missing_code_or_state" }, { status: 400 })
    );
  }

  const jar = cookies();
  const expectedState = jar.get(OIDC_COOKIE_STATE)?.value;
  const verifier = jar.get(OIDC_COOKIE_VERIFIER)?.value;
  if (!expectedState || !verifier || state !== expectedState) {
    const res = NextResponse.json({ error: "invalid_state" }, { status: 400 });
    return clearPkceOnResponse(res);
  }

  const redirectUri = `${getShellPublicUrl()}/auth/callback`;
  const tokenUrl = `${getKeycloakIssuerUrl()}/protocol/openid-connect/token`;
  const clientId = getKeycloakClientId();

  const form = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: clientId,
    code,
    redirect_uri: redirectUri,
    code_verifier: verifier,
  });

  const tokenRes = await fetch(tokenUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });

  if (!tokenRes.ok) {
    const text = await tokenRes.text();
    const detail = safeKeycloakTokenErrorDetail(tokenRes.status, text);
    const res = NextResponse.json(
      { error: "token_exchange_failed", detail },
      { status: 401 }
    );
    return clearPkceOnResponse(res);
  }

  const tokens = (await tokenRes.json()) as {
    access_token?: string;
    refresh_token?: string;
    expires_in?: number;
    token_type?: string;
  };

  const expiresInFallback =
    typeof tokens.expires_in === "number" && Number.isFinite(tokens.expires_in)
      ? tokens.expires_in
      : undefined;

  if (!tokens.access_token) {
    const res = NextResponse.json(
      {
        error: "missing_access_token",
        diagnostics: buildTokenBuildFailureDiagnostics({
          tokenEndpointHttp: tokenRes.status,
          accessToken: undefined,
          tokenType: tokens.token_type,
          expiresIn: expiresInFallback ?? null,
          rejectReason: "unknown",
          missingRequiredField: "access_token",
        }),
      },
      { status: 502 }
    );
    return clearPkceOnResponse(res);
  }

  const built = buildSessionFromKeycloakTokens({
    accessToken: tokens.access_token,
    refreshToken:
      typeof tokens.refresh_token === "string"
        ? tokens.refresh_token
        : undefined,
    expiresInFallback,
  });

  if (!built) {
    const { rejectReason, missingRequiredField } = getSessionBuildFailureReason(
      tokens.access_token,
      expiresInFallback
    );
    const res = NextResponse.json(
      {
        error: "unusable_access_token",
        diagnostics: buildTokenBuildFailureDiagnostics({
          tokenEndpointHttp: tokenRes.status,
          accessToken: tokens.access_token,
          tokenType: tokens.token_type,
          expiresIn: expiresInFallback ?? null,
          rejectReason,
          missingRequiredField,
        }),
      },
      { status: 502 }
    );
    return clearPkceOnResponse(res);
  }

  const session = { ...built.session, refreshToken: undefined };

  const res = NextResponse.json({
    session,
    tenantId: built.tenantId,
    facilityId: built.facilityId,
  });
  return clearPkceOnResponse(res);
}
