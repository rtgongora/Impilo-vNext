/** True if the string looks like a compact JWS (e.g. Keycloak access_token). */
export function isLikelyJwt(accessToken: string): boolean {
  return accessToken.split(".").length === 3 && accessToken.startsWith("ey");
}
