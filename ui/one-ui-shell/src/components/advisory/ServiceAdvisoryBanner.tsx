"use client";

/**
 * Nompilo Service Advisory surface for public gateway pages.
 *
 * Renders the highest-priority active advisory for the current route context, resolved as
 * DATA from the backend (never hard-coded here). Doctrine guardrails enforced in this
 * component:
 *
 *   • NEVER blocks access to care. On emergency / urgent routes (emergency, find-care,
 *     report) any advisory — whatever its authored variant — is downgraded to a small,
 *     non-blocking inline notice. A MODAL is never shown there.
 *   • Every advisory is dismissible, and the citizen can always proceed. Dismissal persists
 *     anonymously on-device (no account) via the advisory-dismissal store.
 *   • Severity drives visual prominence but the tone stays calm; EMERGENCY is prominent yet
 *     still non-blocking (a banner, never an access-blocking overlay).
 *   • Primary actions render as ordinary links to their href.
 *
 * Mounted ONCE in {@link PublicShell} so it appears across public pages under a single owner.
 */

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { classifyRouteJourney } from "@/lib/ui-route-journey-map";
import {
  useServiceAdvisory,
  recordAdvisoryView,
  type ServiceAdvisory,
} from "@/hooks/useServiceAdvisory";

/** Routes where an advisory must never block or interrupt — care comes first. */
const NEVER_BLOCK_PREFIXES = ["/welcome/emergency", "/welcome/find-care", "/welcome/report"];

function isNeverBlockRoute(pathname: string): boolean {
  return NEVER_BLOCK_PREFIXES.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

interface Palette {
  border: string;
  bg: string;
  title: string;
  body: string;
  badge: string;
  badgeLabel: string;
}

function paletteFor(severity: ServiceAdvisory["severity"]): Palette {
  switch (severity) {
    case "EMERGENCY_ALERT":
      return {
        border: "border-red-300",
        bg: "bg-red-50",
        title: "text-red-900",
        body: "text-red-800",
        badge: "bg-red-100 text-red-800",
        badgeLabel: "Emergency notice",
      };
    case "IMPORTANT_DISRUPTION":
      return {
        border: "border-amber-300",
        bg: "bg-amber-50",
        title: "text-amber-900",
        body: "text-amber-800",
        badge: "bg-amber-100 text-amber-800",
        badgeLabel: "Service disruption",
      };
    case "SERVICE_NOTICE":
      return {
        border: "border-sky-300",
        bg: "bg-sky-50",
        title: "text-sky-900",
        body: "text-sky-800",
        badge: "bg-sky-100 text-sky-800",
        badgeLabel: "Service notice",
      };
    default:
      return {
        border: "border-emerald-300",
        bg: "bg-emerald-50",
        title: "text-emerald-900",
        body: "text-emerald-800",
        badge: "bg-emerald-100 text-emerald-800",
        badgeLabel: "Update",
      };
  }
}

function ActionLinks({ advisory, palette }: { advisory: ServiceAdvisory; palette: Palette }) {
  if (advisory.primaryActions.length === 0) return null;
  return (
    <div className="mt-3 flex flex-wrap gap-2">
      {advisory.primaryActions.map((action) => (
        <Link
          key={`${action.label}-${action.href}`}
          href={action.href}
          className={`rounded-lg border ${palette.border} bg-white px-3 py-1.5 text-sm font-semibold ${palette.title} hover:bg-white/70`}
        >
          {action.label}
        </Link>
      ))}
    </div>
  );
}

function DismissButton({ onDismiss, tone }: { onDismiss: () => void; tone: string }) {
  return (
    <button
      type="button"
      onClick={onDismiss}
      aria-label="Dismiss this notice"
      className={`shrink-0 rounded-md px-2 py-1 text-sm font-medium ${tone} hover:opacity-70`}
    >
      Dismiss
    </button>
  );
}

/** Small, non-blocking inline notice — the only form allowed on never-block routes. */
function InlineNotice({
  advisory,
  palette,
  onDismiss,
}: {
  advisory: ServiceAdvisory;
  palette: Palette;
  onDismiss: () => void;
}) {
  return (
    <div
      className={`mx-auto mt-3 max-w-5xl rounded-xl border ${palette.border} ${palette.bg} px-4 py-3`}
      data-testid="advisory-inline"
      role="status"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className={`text-sm font-semibold ${palette.title}`}>{advisory.title}</p>
          {advisory.body && <p className={`mt-1 text-sm ${palette.body}`}>{advisory.body}</p>}
          <ActionLinks advisory={advisory} palette={palette} />
        </div>
        <DismissButton onDismiss={onDismiss} tone={palette.body} />
      </div>
    </div>
  );
}

/** Full-width top banner (dismissible). */
function BannerNotice({
  advisory,
  palette,
  onDismiss,
}: {
  advisory: ServiceAdvisory;
  palette: Palette;
  onDismiss: () => void;
}) {
  return (
    <div className={`border-b ${palette.border} ${palette.bg}`} data-testid="advisory-banner" role="status">
      <div className="mx-auto flex max-w-5xl items-start justify-between gap-3 px-4 py-3">
        <div>
          <div className="flex items-center gap-2">
            <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${palette.badge}`}>
              {palette.badgeLabel}
            </span>
            <p className={`text-sm font-semibold ${palette.title}`}>{advisory.title}</p>
          </div>
          {advisory.body && <p className={`mt-1 text-sm ${palette.body}`}>{advisory.body}</p>}
          <ActionLinks advisory={advisory} palette={palette} />
        </div>
        <DismissButton onDismiss={onDismiss} tone={palette.body} />
      </div>
    </div>
  );
}

/** Small floating Nompilo bubble (bottom-left, clear of the bottom-right Emergency Help). */
function BubbleNotice({
  advisory,
  palette,
  onDismiss,
}: {
  advisory: ServiceAdvisory;
  palette: Palette;
  onDismiss: () => void;
}) {
  return (
    <div
      className={`fixed bottom-4 left-4 z-40 max-w-xs rounded-2xl border ${palette.border} ${palette.bg} p-4 shadow-lg`}
      data-testid="advisory-bubble"
      role="status"
    >
      <div className="flex items-start justify-between gap-2">
        <p className={`text-sm font-semibold ${palette.title}`}>{advisory.title}</p>
        <DismissButton onDismiss={onDismiss} tone={palette.body} />
      </div>
      {advisory.body && <p className={`mt-1 text-sm ${palette.body}`}>{advisory.body}</p>}
      <ActionLinks advisory={advisory} palette={palette} />
    </div>
  );
}

/**
 * First-entry modal — dismissible, and deliberately NON-blocking: the backdrop is a scrim the
 * citizen can dismiss, and the modal is only ever rendered off the never-block routes.
 */
function ModalNotice({
  advisory,
  palette,
  onDismiss,
}: {
  advisory: ServiceAdvisory;
  palette: Palette;
  onDismiss: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" data-testid="advisory-modal">
      <button
        type="button"
        aria-label="Dismiss this notice"
        className="absolute inset-0 bg-slate-900/30"
        onClick={onDismiss}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="advisory-modal-title"
        className={`relative w-full max-w-lg rounded-2xl border ${palette.border} ${palette.bg} p-6 shadow-xl`}
      >
        <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${palette.badge}`}>
          {palette.badgeLabel}
        </span>
        <h2 id="advisory-modal-title" className={`mt-3 text-xl font-bold ${palette.title}`}>
          {advisory.title}
        </h2>
        {advisory.body && <p className={`mt-2 text-sm ${palette.body}`}>{advisory.body}</p>}
        <ActionLinks advisory={advisory} palette={palette} />
        <button
          type="button"
          onClick={onDismiss}
          className={`mt-5 text-sm font-medium ${palette.body} hover:opacity-70`}
        >
          Not now — continue
        </button>
      </div>
    </div>
  );
}

export function ServiceAdvisoryBanner() {
  const pathname = usePathname() || "/";
  const journeyStage = useMemo(() => classifyRouteJourney(pathname), [pathname]);
  const { advisories, dismiss } = useServiceAdvisory({ journeyStage });

  // Show the single highest-priority active advisory (the resolve lane returns them
  // severity-ranked). One surface at a time keeps public pages calm and uncluttered.
  const advisory = advisories[0] ?? null;
  const neverBlock = isNeverBlockRoute(pathname);

  const [viewedId, setViewedId] = useState<string | null>(null);
  useEffect(() => {
    if (advisory && advisory.id !== viewedId) {
      recordAdvisoryView(advisory.id);
      setViewedId(advisory.id);
    }
  }, [advisory, viewedId]);

  if (!advisory) return null;

  const palette = paletteFor(advisory.severity);
  const onDismiss = () => dismiss(advisory.id);

  // Never-block routes: downgrade EVERYTHING to a small inline notice — never a modal.
  if (neverBlock) {
    return <InlineNotice advisory={advisory} palette={palette} onDismiss={onDismiss} />;
  }

  switch (advisory.variant) {
    case "MODAL":
      return <ModalNotice advisory={advisory} palette={palette} onDismiss={onDismiss} />;
    case "BANNER":
    case "EMERGENCY":
      // EMERGENCY is prominent (severity palette) but still a non-blocking banner.
      return <BannerNotice advisory={advisory} palette={palette} onDismiss={onDismiss} />;
    case "BUBBLE":
      return <BubbleNotice advisory={advisory} palette={palette} onDismiss={onDismiss} />;
    case "INLINE":
    default:
      return <InlineNotice advisory={advisory} palette={palette} onDismiss={onDismiss} />;
  }
}
