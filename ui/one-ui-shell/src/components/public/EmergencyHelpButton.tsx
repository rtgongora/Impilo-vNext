"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Siren } from "lucide-react";

/**
 * Persistent Emergency Help control — gateway doctrine §7.
 *
 * Always visible, before and after login, on every citizen-facing surface. Clearly
 * labelled with text + icon (never icon-only), large touch target, links straight to
 * /welcome/emergency. There are deliberately NO login, coverage, or payment checks
 * anywhere in this path: emergency access is never gated (§7.2).
 *
 * Fixed to the lower-left corner (doctrine §7: fixed lower corner on mobile) so it
 * never obstructs forms; the shell taskbar is bottom-centre and NompiloHint is
 * bottom-centre, so the corner stays clear. Pass `raised` where bottom chrome (the
 * authenticated taskbar) occupies the lower edge.
 */
export function EmergencyHelpButton({
  raised = false,
  className = "",
}: {
  /** Lift above bottom chrome such as the authenticated shell taskbar. */
  raised?: boolean;
  className?: string;
}) {
  // On the emergency journey itself the page IS the emergency surface (with its
  // own permanent call actions) — a floating link back to it is pure noise.
  const pathname = usePathname();
  if (pathname?.startsWith("/welcome/emergency")) return null;

  return (
    <Link
      href="/welcome/emergency"
      data-testid="emergency-help-button"
      aria-label="Emergency Help — always available, no sign-in needed"
      className={[
        "fixed left-3 z-[9500] inline-flex min-h-[44px] items-center gap-2 rounded-full",
        raised ? "bottom-16" : "bottom-3",
        "bg-red-600 px-4 py-2.5 text-sm font-semibold text-white shadow-lg",
        "hover:bg-red-700 active:bg-red-800",
        "focus:outline-none focus-visible:ring-2 focus-visible:ring-red-400 focus-visible:ring-offset-2",
        className,
      ].join(" ")}
    >
      <Siren className="h-4 w-4 shrink-0" aria-hidden />
      <span>Emergency Help</span>
    </Link>
  );
}
