"use client";

/**
 * Landing point for a sign-in that ran in the separate sign-in window.
 *
 * The OIDC round trip ends by redirecting to this route inside that window. Left alone it
 * would load the entire shell there — a second, orphaned copy of the app. Instead this page
 * tells the window that opened it that the session is live, lets THAT window do the
 * navigating, and closes itself. All anyone sees here is a brief confirmation.
 *
 * If it is ever reached as an ordinary top-level page — popup blocked, someone opening the
 * URL directly, a redirect chain that lost the opener — it falls back to a normal navigation,
 * so the route is never a dead end.
 */

import { useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { CheckCircle2 } from "lucide-react";

/** Same-origin only. The listener checks this too; both ends must agree. */
export const SIGN_IN_COMPLETE_MESSAGE = "impilo:sign-in-complete";

export default function AuthCompletePage() {
  const router = useRouter();
  const params = useSearchParams();
  const destination = params?.get("to") || "/home";

  useEffect(() => {
    if (typeof window === "undefined") return;
    // A popup reaches this page as window.opener's child; a frame as window.parent's. Handle
    // both so the completion signal does not depend on which presentation the shell chose.
    const target = window.opener && window.opener !== window
      ? (window.opener as Window)
      : window.parent !== window
        ? window.parent
        : null;
    if (target) {
      // Targeted at our own origin — never "*", which would leak the fact and timing of a
      // successful sign-in to any other document that happened to be listening.
      target.postMessage({ type: SIGN_IN_COMPLETE_MESSAGE, destination }, window.location.origin);
      // Close only what we opened. A directly-visited tab is not ours to close.
      if (window.opener && window.opener !== window) {
        setTimeout(() => window.close(), 400);
      }
      return;
    }
    router.replace(destination);
  }, [router, destination]);

  return (
    <div className="flex min-h-[200px] flex-col items-center justify-center gap-3 p-8 text-center">
      <CheckCircle2 className="h-8 w-8 text-primary" aria-hidden />
      <p className="text-sm font-medium text-foreground">You&apos;re signed in</p>
      <p className="text-xs text-muted-foreground">Taking you back to Impilo…</p>
    </div>
  );
}
