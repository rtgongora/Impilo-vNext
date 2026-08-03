import { NextResponse } from "next/server";

/**
 * What commit this shell is actually running.
 *
 * The estate's /health/version is served by experience-bff, which reported `shellCommit` from an
 * environment value baked in at Helm-release time. Roll the shell on its own — a targeted
 * `kubectl set image`, which is the normal way a UI fix lands — and that value keeps naming the
 * commit from the last full release. It does not go missing or error; it confidently reports the
 * wrong commit, which is worse, because the no-stale deploy checklist reads it.
 *
 * Only this process knows the answer, so it is the one that has to answer. IMPILO_GIT_COMMIT is
 * stamped into the image at build time next to the bundle it describes, so the two cannot drift.
 */

// Must never be prerendered: a build-time snapshot of the commit is the exact failure this
// endpoint exists to correct.
export const dynamic = "force-dynamic";
export const revalidate = 0;

// NOTE ON THE /api REWRITE. next.config.mjs rewrites /api/:path* to the API gateway. That rewrite
// is returned as a plain array, which Next applies as `afterFiles` — i.e. only when no filesystem
// route matched — so this handler takes precedence. Verified against the built image rather than
// assumed, because if it were `beforeFiles` this endpoint would silently proxy to the gateway and
// answer 401 forever. The failure would at least be safe: the BFF treats any non-200 as "no
// answer" and falls back to the labelled-unverified value rather than reporting a wrong commit.

export function GET() {
  return NextResponse.json(
    {
      service: "one-ui-shell",
      // "unknown" rather than a fallback that looks like a real commit — an honest gap is
      // recoverable, a plausible wrong SHA is not.
      commit: process.env.IMPILO_GIT_COMMIT || "unknown",
      buildId: process.env.NEXT_BUILD_ID || "",
      environment: process.env.IMPILO_ENV || "local",
    },
    { headers: { "cache-control": "no-store" } },
  );
}
