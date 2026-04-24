import { NextResponse } from "next/server";

import {
  OIDC_COOKIE_STATE,
  OIDC_COOKIE_VERIFIER,
  getKeycloakClientId,
  getKeycloakIssuerUrl,
  getOidcScope,
  getShellPublicUrl,
} from "@/lib/auth/shellOidcEnv";
import {
  codeChallengeS256,
  generateCodeVerifier,
  generateOidcState,
} from "@/lib/auth/pkce";

const pkceCookieOpts = {
  httpOnly: true,
  sameSite: "lax" as const,
  path: "/",
  maxAge: 600,
  secure: process.env.NODE_ENV === "production",
};

export async function GET() {
  const verifier = generateCodeVerifier();
  const state = generateOidcState();
  const challenge = codeChallengeS256(verifier);
  const redirectUri = `${getShellPublicUrl()}/auth/callback`;
  const clientId = getKeycloakClientId();
  const issuer = getKeycloakIssuerUrl();

  const authUrl = new URL(`${issuer}/protocol/openid-connect/auth`);
  authUrl.searchParams.set("client_id", clientId);
  authUrl.searchParams.set("redirect_uri", redirectUri);
  authUrl.searchParams.set("response_type", "code");
  authUrl.searchParams.set("scope", getOidcScope());
  authUrl.searchParams.set("state", state);
  authUrl.searchParams.set("code_challenge", challenge);
  authUrl.searchParams.set("code_challenge_method", "S256");

  const res = NextResponse.redirect(authUrl.toString());
  res.cookies.set(OIDC_COOKIE_VERIFIER, verifier, pkceCookieOpts);
  res.cookies.set(OIDC_COOKIE_STATE, state, pkceCookieOpts);
  return res;
}
