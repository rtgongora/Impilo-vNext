"use client";

/**
 * Landing point for a sign-in that ran inside the in-page modal.
 *
 * The OIDC round trip ends by redirecting to this route INSIDE the iframe. Left alone that
 * would load the whole shell inside a 460px box. Instead this page tells the opener that the
 * session is live and lets the opener do the navigating; the frame renders only a brief
 * confirmation, which is all anyone sees before the modal closes.
 *
 * If it is ever reached UNFRAMED — popup blocked, someone opening the URL directly, a
 * redirect chain that lost the frame — it falls back to a normal navigation, so the route is
 * never a dead end.
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
    const framed = typeof window !== "undefined" && window.parent !== window;
    if (framed) {
      // postMessage targeted at our own origin — never "*", which would leak the fact and
      // timing of a successful sign-in to any other frame that happened to be listening.
      window.parent.postMessage(
        { type: SIGN_IN_COMPLETE_MESSAGE, destination },
        window.location.origin,
      );
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
