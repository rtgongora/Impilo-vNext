import { NextRequest, NextResponse } from "next/server";

/**
 * Server-side auth gate — Health OS Unified Experience Shell.
 *
 * Complements the client-side AuthGuardProvider by preventing unauthenticated
 * users from receiving protected page bundles. Token presence is checked via
 * the exp_has_session cookie (set by useAuthStore on login, cleared on logout).
 * Full token validation happens at the BFF layer via Envoy ext_authz.
 */

export const PUBLIC_PREFIXES = [
  "/welcome", // public L0 landing, find-care, emergency, accessibility (G-CZO-02)
  "/auth",
  "/kiosk",
  "/verify",
  "/share",
  "/privacy",
  "/terms",
  "/consent",
  "/account-deletion",
  "/get-involved", // public co-design / participation (anonymous — no account needed)
  "/status",       // public service-status board
  "/download",     // public get-app / app-discovery surface
  "/geo",          // public map base data (Zimbabwe admin/places GeoJSON) — the find-care
                   // map fetches these on the anonymous lane; without this the middleware
                   // 307-redirects them to /auth/login and the public map renders blank.
  "/_next",
  "/api",
  "/internal",
];

const PUBLIC_FILES = ["/favicon.ico", "/robots.txt", "/manifest.json"];

// Exact public paths under an otherwise-gated prefix (e.g. the provider-onboarding
// explainer lives under /provider but must be viewable before sign-in; the rest of
// /provider/* stays provider-gated).
const PUBLIC_EXACT_PATHS = ["/provider/get-access"];

export function isPublicPath(pathname: string): boolean {
  if (pathname === "/") return true; // root decides welcome-vs-home by session itself
  if (PUBLIC_FILES.includes(pathname)) return true;
  if (PUBLIC_EXACT_PATHS.includes(pathname)) return true;
  return PUBLIC_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Collapse duplicate slashes before anything else: with a live session the
  // request reaches the app, and Next's client router replaceState treats
  // "//path" as protocol-relative ("http://path") and throws SecurityError
  // on hydration (real-user report: pasted deep link with a doubled slash).
  if (pathname.includes("//")) {
    const url = request.nextUrl.clone();
    url.pathname = pathname.replace(/\/{2,}/g, "/");
    return NextResponse.redirect(url);
  }

  if (isPublicPath(pathname)) {
    return NextResponse.next();
  }

  // Check for session cookie set by useAuthStore.setAuth()
  // Cookie name: exp_has_session (underscore, not colon — cookies cannot contain colons)
  const hasSession = request.cookies.get("exp_has_session")?.value === "1";

  if (!hasSession) {
    const loginUrl = new URL("/auth/login", request.url);
    loginUrl.searchParams.set("returnTo", pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/((?!_next/static|_next/image|favicon\\.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)",
  ],
};
