import { NextRequest, NextResponse } from "next/server";

/**
 * Server-side auth gate. Complements the client-side AuthGuardProvider
 * by preventing unauthenticated users from receiving protected page bundles.
 *
 * Token presence is checked — validation happens at the BFF layer.
 */

const PUBLIC_PATHS = [
  "/auth",
  "/kiosk",
  "/verify",
  "/share",
  "/_next",
  "/favicon.ico",
  "/api",
];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Allow public paths
  if (PUBLIC_PATHS.some((p) => pathname.startsWith(p))) {
    return NextResponse.next();
  }

  // Check for auth token in cookie or session header
  const token =
    request.cookies.get("exp:auth_token")?.value ??
    request.headers.get("x-auth-token");

  // Also check for the session storage relay cookie that AuthGuardProvider sets
  const hasSession = request.cookies.get("exp:has_session")?.value === "1";

  if (!token && !hasSession) {
    const loginUrl = new URL("/auth/login", request.url);
    loginUrl.searchParams.set("returnTo", pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    /*
     * Match all paths except static files and Next.js internals.
     */
    "/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)",
  ],
};
