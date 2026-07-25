import Link from "next/link";
import type { ReactNode } from "react";
import { MessageCircleMore } from "lucide-react";

/**
 * Honest Khuluma continuity card for guest receipts and active public journeys.
 * It never claims live guest messaging: the status link and reference are the
 * available continuity contract until guest chat is enabled.
 */
export function KhulumaJourneyUpdate({
  reference,
  statusHref,
  children,
}: {
  reference?: string | null;
  statusHref: string;
  children?: ReactNode;
}) {
  return (
    <aside
      aria-label="Khuluma journey update"
      className="mt-4 rounded-xl border border-violet-200 bg-violet-50 p-4"
      data-testid="khuluma-journey-update"
    >
      <div className="flex items-start gap-3">
        <MessageCircleMore className="mt-0.5 h-5 w-5 shrink-0 text-violet-700" aria-hidden />
        <div>
          <h3 className="font-semibold text-violet-950">Khuluma keeps this journey connected</h3>
          <p className="mt-1 text-sm leading-6 text-violet-900">
            {children ??
              "Keep your reference or claim code. You can return to the status page for updates without signing in."}
          </p>
          {reference && (
            <p className="mt-1 text-xs text-violet-800">
              Journey reference: <span className="font-mono font-semibold">{reference}</span>
            </p>
          )}
          <Link
            href={statusHref}
            className="mt-3 inline-flex min-h-10 items-center rounded-lg bg-violet-700 px-4 py-2 text-sm font-semibold text-white hover:bg-violet-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-700 focus-visible:ring-offset-2"
          >
            View journey status
          </Link>
          <p className="mt-2 text-xs text-violet-700">
            Live guest messaging is not enabled yet; this status route is the available update path.
          </p>
        </div>
      </div>
    </aside>
  );
}
