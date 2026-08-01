import { NextRequest, NextResponse } from "next/server";

/**
 * Server-side auth gate — Health OS Unified Experience Shell.
 *
 * Complements the client-side AuthGuardProvider by preventing unauthenticated
 * users from receiving protected page bundles. Only the opaque HttpOnly BFF
 * session cookie is accepted in production; token validation stays server-side.
 */

export const PUBLIC_PREFIXES = [
  "/welcome", // public L0 landing, find-care, emergency, accessibility (G-CZO-02)
  "/about",
  "/contact",
  "/services",   // legacy public URLs redirect into the unified living canvas
  "/solutions",
  "/features",
  "/resources",
  "/docs",
  "/training",
  "/apps",
  "/community",
  "/technical",
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
  "/map",          // public map assets (self-hosted glyph PBFs for street labels) — same
                   // anonymous-lane rule as /geo; a 307 here means label-less maps.
  "/.well-known",  // mobile universal-link association files
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

  const hasSession = Boolean(request.cookies.get("__Host-impilo_session")?.value);
  // Existing compose tests use a non-Secure localhost fixture. It is accepted only
  // outside production and can never unlock the preview/production build.
  const hasDevelopmentFixture =
    process.env.NODE_ENV !== "production" && request.cookies.get("exp_has_session")?.value === "1";

  if (!hasSession && !hasDevelopmentFixture) {
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
