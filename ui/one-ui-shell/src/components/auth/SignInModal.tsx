"use client";

/**
 * In-page sign-in.
 *
 * The credential step used to be a full-page navigation to Keycloak: the shell disappeared,
 * a differently-shaped page appeared, and coming back was another navigation. This keeps the
 * person where they were and runs the same governed flow inside a modal.
 *
 * WHY A FRAME IS LEGITIMATE HERE, when framing a login page usually is not: Keycloak serves
 * `frame-ancestors 'self'` and `X-Frame-Options: SAMEORIGIN`, and on this estate Keycloak is
 * served from the SAME ORIGIN as the shell (impilo.mohcc.gov.zw/realms/...). So this is not a
 * third-party IdP being embedded — there is no cross-origin clickjacking vector, and the
 * browser enforces that. Against a differently-hosted IdP the browser would simply refuse to
 * render, which is why the load-failure fallback below is not optional.
 *
 * WHAT DOES NOT CHANGE: Keycloak still hosts and verifies every credential. The shell never
 * sees a password — it cannot read into the frame, which is a different document. The
 * denyAll() on POST /internal/v1/auth/login still stands. This moves the WINDOW, not the
 * trust boundary.
 *
 * The one real cost, stated because it is easy to gloss: inside a modal a person cannot see
 * the address bar while typing a password. Same-origin makes that far weaker than the usual
 * argument, and `Open in a full page` below gives anyone who wants that check a way to take
 * it.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { X, ExternalLink, Loader2 } from "lucide-react";
import { SIGN_IN_COMPLETE_MESSAGE } from "@/app/auth/complete/page";

/** If the frame has not rendered by now, assume it was refused and offer the full page. */
const FRAME_LOAD_TIMEOUT_MS = 8000;

export function SignInModal({
  authorizeUrl,
  onClose,
}: {
  /** Fully-formed /internal/v1/auth/oidc/authorize URL, returnTo already pointing at /auth/complete. */
  authorizeUrl: string;
  onClose: () => void;
}) {
  const router = useRouter();
  const [loaded, setLoaded] = useState(false);
  const [blocked, setBlocked] = useState(false);
  const frameRef = useRef<HTMLIFrameElement | null>(null);

  const fallbackToFullPage = useCallback(() => {
    window.location.assign(authorizeUrl);
  }, [authorizeUrl]);

  // Completion signal from the framed /auth/complete page.
  useEffect(() => {
    function onMessage(event: MessageEvent) {
      // Origin check is the whole security of this listener: without it any frame could
      // claim a sign-in completed and drive this shell wherever it liked.
      if (event.origin !== window.location.origin) return;
      const data = event.data as { type?: string; destination?: string } | null;
      if (!data || data.type !== SIGN_IN_COMPLETE_MESSAGE) return;
      const to = typeof data.destination === "string" && data.destination.startsWith("/")
        ? data.destination
        : "/home";
      onClose();
      router.replace(to);
      router.refresh();
    }
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, [router, onClose]);

  // A frame that never loads means the browser refused it. Say so and offer the full page
  // rather than leaving someone looking at a spinner that will not resolve.
  useEffect(() => {
    const t = setTimeout(() => { if (!loaded) setBlocked(true); }, FRAME_LOAD_TIMEOUT_MS);
    return () => clearTimeout(t);
  }, [loaded]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) { if (e.key === "Escape") onClose(); }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-[11000] flex items-center justify-center bg-slate-900/70 p-4 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label="Sign in to Impilo"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="relative w-full max-w-[480px] overflow-hidden rounded-[1.75rem] border border-border bg-card shadow-2xl">
        <div className="flex items-center justify-between border-b border-border px-4 py-2.5">
          <span className="text-xs font-medium text-muted-foreground">
            Secure sign-in · Impilo identity service
          </span>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1.5 text-muted-foreground hover:bg-neutral-100 hover:text-foreground"
            aria-label="Close sign-in"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {blocked ? (
          <div className="space-y-3 p-6 text-center">
            <p className="text-sm font-medium text-foreground">
              This browser would not open sign-in here
            </p>
            <p className="text-xs text-muted-foreground">
              Nothing is wrong with your account — continue on a full page instead.
            </p>
            <button
              type="button"
              onClick={fallbackToFullPage}
              className="mx-auto flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-white"
            >
              Continue to sign-in <ExternalLink className="h-3.5 w-3.5" />
            </button>
          </div>
        ) : (
          <>
            {!loaded && (
              <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Opening secure sign-in…
              </div>
            )}
            <iframe
              ref={frameRef}
              src={authorizeUrl}
              title="Impilo secure sign-in"
              onLoad={() => setLoaded(true)}
              className={loaded ? "h-[460px] w-full border-0" : "h-0 w-full border-0"}
            />
          </>
        )}

        <div className="border-t border-border px-4 py-2 text-center">
          <button
            type="button"
            onClick={fallbackToFullPage}
            className="text-xs text-muted-foreground underline hover:text-foreground"
          >
            Open in a full page
          </button>
        </div>
      </div>
    </div>
  );
}
