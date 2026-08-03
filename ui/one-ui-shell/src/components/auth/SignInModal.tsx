"use client";

/**
 * In-place sign-in: the credential step runs in a separate popup WINDOW while the shell stays
 * where it was. No full-page navigation away and back.
 *
 * WHY A POPUP WINDOW AND NOT AN IN-PAGE FRAME. Framing would work technically — Keycloak
 * serves `frame-ancestors 'self'` and is same-origin with the shell here, so no other site
 * can frame it and there is no clickjacking vector. The objection is not technical, it is
 * behavioural: a modal teaches people that typing a password into a box with NO VISIBLE
 * ORIGIN is normal, and that habit travels to sites where the box is fake and no CSP is
 * protecting them. We would be making this page safe by making our users marginally easier to
 * phish elsewhere. A popup keeps its own address bar, so the origin stays checkable and the
 * habit stays a good one. Google, Apple and Microsoft all made the same call for the same
 * reason.
 *
 * The trust boundary is unchanged either way: Keycloak hosts and verifies every credential,
 * the shell never sees a password, and denyAll() on POST /internal/v1/auth/login still stands.
 *
 * Popup blockers are the real cost, and the reason the fallback below is not optional. This
 * opens synchronously inside the click handler, which browsers allow; if it is blocked anyway
 * window.open returns null and we degrade to a full-page redirect rather than leaving someone
 * looking at a dialog that will never resolve.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ExternalLink, Loader2 } from "lucide-react";
import { SIGN_IN_COMPLETE_MESSAGE } from "@/app/auth/complete/page";

const POPUP_W = 480;
const POPUP_H = 640;

export function SignInModal({
  authorizeUrl,
  onClose,
}: {
  /** Fully-formed authorize URL, returnTo already pointing at /auth/complete. */
  authorizeUrl: string;
  onClose: () => void;
}) {
  const router = useRouter();
  const [blocked, setBlocked] = useState(false);
  const popupRef = useRef<Window | null>(null);
  const doneRef = useRef(false);

  const fallbackToFullPage = useCallback(() => {
    window.location.assign(authorizeUrl);
  }, [authorizeUrl]);

  // Open once, on mount — this component is rendered from the submit handler, so it is still
  // within the user-gesture window that browsers require.
  useEffect(() => {
    const left = Math.max(0, window.screenX + (window.outerWidth - POPUP_W) / 2);
    const top = Math.max(0, window.screenY + (window.outerHeight - POPUP_H) / 3);
    const win = window.open(
      authorizeUrl,
      "impilo-sign-in",
      `popup=yes,width=${POPUP_W},height=${POPUP_H},left=${Math.round(left)},top=${Math.round(top)}`,
    );
    if (!win) { setBlocked(true); return; }
    popupRef.current = win;
    win.focus();
    return () => { if (!doneRef.current) { try { win.close(); } catch { /* already gone */ } } };
  }, [authorizeUrl]);

  // Completion signal from /auth/complete inside the popup.
  useEffect(() => {
    function onMessage(event: MessageEvent) {
      // The origin check is the whole security of this listener: without it any document
      // could claim a sign-in completed and drive this shell wherever it liked.
      if (event.origin !== window.location.origin) return;
      const data = event.data as { type?: string; destination?: string } | null;
      if (!data || data.type !== SIGN_IN_COMPLETE_MESSAGE) return;
      doneRef.current = true;
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

  // Someone closing the popup without finishing is a cancellation, not a hang — dismiss so
  // they are returned to a usable form rather than a permanent overlay.
  useEffect(() => {
    const t = setInterval(() => {
      if (popupRef.current?.closed && !doneRef.current) { clearInterval(t); onClose(); }
    }, 700);
    return () => clearInterval(t);
  }, [onClose]);

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
      aria-label="Signing in"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="w-full max-w-[380px] rounded-2xl border border-border bg-card p-6 text-center shadow-2xl">
        {blocked ? (
          <>
            <p className="text-sm font-medium text-foreground">Your browser blocked the sign-in window</p>
            <p className="mt-1 text-xs text-muted-foreground">
              Nothing is wrong with your account — continue on a full page instead.
            </p>
            <button
              type="button"
              onClick={fallbackToFullPage}
              className="mx-auto mt-4 flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-white"
            >
              Continue to sign-in <ExternalLink className="h-3.5 w-3.5" />
            </button>
          </>
        ) : (
          <>
            <Loader2 className="mx-auto h-5 w-5 animate-spin text-primary" aria-hidden />
            <p className="mt-3 text-sm font-medium text-foreground">Continue in the sign-in window</p>
            <p className="mt-1 text-xs text-muted-foreground">
              Check the address bar there before entering your password. This page stays as you left it.
            </p>
            <div className="mt-4 flex items-center justify-center gap-4 text-xs">
              <button type="button" onClick={() => popupRef.current?.focus()} className="font-medium text-primary hover:underline">
                Bring it to the front
              </button>
              <button type="button" onClick={onClose} className="text-muted-foreground hover:text-foreground">
                Cancel
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
