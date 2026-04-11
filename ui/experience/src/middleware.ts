import { NextRequest, NextResponse } from "next/server";

/**
 * Server-side auth gate — Health OS Unified Experience Shell.
 *
 * Complements the client-side AuthGuardProvider by preventing unauthenticated
 * users from receiving protected page bundles. Token presence is checked via
 * the exp_has_session cookie (set by useAuthStore on login, cleared on logout).
 * Full token validation happens at the BFF layer via Envoy ext_authz.
 */

const PUBLIC_PREFIXES = [
  "/auth",
  "/kiosk",
  "/verify",
  "/share",
  "/privacy",
  "/terms",
  "/consent",
  "/_next",
  "/api",
];

const PUBLIC_FILES = ["/favicon.ico", "/robots.txt", "/manifest.json"];

function isPublicPath(pathname: string): boolean {
  if (PUBLIC_FILES.includes(pathname)) return true;
  return PUBLIC_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

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
