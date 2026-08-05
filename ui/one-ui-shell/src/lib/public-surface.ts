/**
 * The shell's public surface — the paths that may render without a route-registry entry.
 *
 * These are the same prefixes that bypass the consent gate: legal pages, the consent page itself,
 * the auth pathways, and the anonymous claim/verify lanes. Everywhere else, a pathname that
 * `matchRouteDefinition` does not resolve is denied by `AuthGuardProvider` (item 73) rather than
 * rendered unguarded.
 *
 * This lives in its own module because two things must agree about it and a copy would drift:
 * the runtime guard, and the coverage test that asserts every app-router page resolves to either
 * a registry entry or this surface.
 */
export const CONSENT_EXEMPT_PREFIXES = [
  "/",
  "/welcome",
  "/auth",
  "/consent",
  "/privacy",
  "/terms",
  "/account-deletion",
  "/kiosk",
  "/verify",
  "/share",
  "/collaboration",
] as const;

/**
 * Whether a pathname sits on a deliberately public surface (anonymous lane).
 * Bare "/" means the landing page exactly — never a prefix.
 */
export function isPublicSurface(pathname: string): boolean {
  return CONSENT_EXEMPT_PREFIXES.some((p) =>
    p === "/" ? pathname === "/" : pathname.startsWith(p),
  );
}
