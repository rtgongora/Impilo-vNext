"use client";

/**
 * Compact live service-status strip for the hero's lower-left zone.
 *
 * Same public lane as the full board (`GET /internal/v1/public/gateway/service-status`),
 * summarised to one line. The whole point of this surface is that it must never trade
 * honesty for a reassuring green dot, so it keeps the product states distinct
 * (CLAUDE_GOVERNANCE §9: EMPTY, UNAVAILABLE, DEGRADED, STALE and LOADING are different
 * things) and reports exactly one of:
 *
 *   LOADING     — the check is in flight; claims nothing.
 *   MONITORED   — the feed is live AND groups are actually monitored → worst live state wins,
 *                 so a single degraded group is never hidden behind five healthy ones.
 *   UNMONITORED — the feed answered but monitors nothing (the estate's current truth). Says so
 *                 in plain words rather than inventing "operational" from an absence of alarms.
 *   UNAVAILABLE — the feed itself could not be reached. Distinct from "nothing is wrong".
 *
 * Desktop-only by design: it fills space the wide hero genuinely has, and must not push the
 * discovery panel down on a stacked mobile viewport. The full board at /status is the
 * canonical surface on every viewport, and this strip always links to it.
 */

import { useEffect, useState } from "react";
import Link from "next/link";
import { Activity, ArrowRight } from "lucide-react";
import { apiClient } from "@/lib/api-client";

export interface LiveGroup {
  group: string;
  state: string;
  monitored: boolean;
}

export interface LiveStatus {
  updatedAt: string;
  live: boolean;
  groups: LiveGroup[];
}

type Phase = "loading" | "monitored" | "unmonitored" | "unavailable";

export interface Summary {
  phase: Phase;
  /** Headline sentence. Never claims health that was not measured. */
  label: string;
  /** Supporting clause: counts, or why there is no state to report. */
  detail: string;
  dot: string;
  text: string;
}

/** Worst-first: one degraded group must not be averaged away by healthy ones. */
const SEVERITY: Record<string, number> = {
  UNAVAILABLE: 4,
  DEGRADED: 3,
  PARTIAL: 3,
  MAINTENANCE: 2,
  OPERATIONAL: 1,
};

export function summarise(feed: LiveStatus | null, failed: boolean): Summary {
  if (failed) {
    return {
      phase: "unavailable",
      label: "Service status unavailable",
      detail: "We could not reach the status feed — this does not mean services are down.",
      dot: "bg-slate-300",
      text: "text-emerald-50/80",
    };
  }

  const groups = feed?.groups ?? [];
  const monitored = groups.filter((g) => g.monitored);

  // The feed answered, but nothing behind it is actually being watched. Saying
  // "operational" here would be inventing a measurement that was never taken.
  if (!feed?.live || monitored.length === 0) {
    return {
      phase: "unmonitored",
      label: "Live monitoring not reporting yet",
      detail: `${groups.length} citizen service ${groups.length === 1 ? "group" : "groups"} listed · none currently monitored`,
      dot: "bg-slate-300",
      text: "text-emerald-50/80",
    };
  }

  const worst = monitored.reduce((acc, g) => {
    const rank = SEVERITY[g.state] ?? 0;
    return rank > acc.rank ? { rank, state: g.state } : acc;
  }, { rank: 0, state: "UNKNOWN" });

  const affected = monitored.filter((g) => (SEVERITY[g.state] ?? 0) >= 3).length;

  if (worst.state === "UNAVAILABLE") {
    return {
      phase: "monitored",
      label: `${affected} of ${monitored.length} services unavailable`,
      detail: "Emergency help stays reachable at all times.",
      dot: "bg-red-400",
      text: "text-red-50",
    };
  }

  if (worst.rank >= 3) {
    return {
      phase: "monitored",
      label: `${affected} of ${monitored.length} services degraded`,
      detail: "Other services are operating normally.",
      dot: "bg-amber-300",
      text: "text-amber-50",
    };
  }

  if (worst.state === "MAINTENANCE") {
    return {
      phase: "monitored",
      label: "Planned maintenance in progress",
      detail: `${monitored.length} monitored service ${monitored.length === 1 ? "group" : "groups"}`,
      dot: "bg-sky-300",
      text: "text-sky-50",
    };
  }

  return {
    phase: "monitored",
    label: "All monitored services operational",
    detail: `${monitored.length} of ${groups.length} service groups monitored`,
    dot: "bg-emerald-400",
    text: "text-emerald-50",
  };
}

/** Rendered only after mount — a server-rendered "2 minutes ago" would hydrate stale. */
function checkedAgo(iso: string | undefined): string | null {
  if (!iso) return null;
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return null;
  const mins = Math.floor((Date.now() - then) / 60000);
  if (mins < 1) return "checked just now";
  if (mins < 60) return `checked ${mins} min ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `checked ${hours}h ago`;
  return `checked ${Math.floor(hours / 24)}d ago`;
}

export function HeroServiceStatus() {
  const [feed, setFeed] = useState<LiveStatus | null>(null);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [ago, setAgo] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const res = await apiClient.get<LiveStatus>(
          "/internal/v1/public/gateway/service-status",
        );
        if (!active) return;
        setFeed(res);
        setAgo(checkedAgo(res?.updatedAt));
      } catch {
        if (active) setFailed(true);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  const summary = loading
    ? {
        phase: "loading" as const,
        label: "Checking service status…",
        detail: "Live status for citizen services",
        dot: "bg-white/40",
        text: "text-emerald-50/70",
      }
    : summarise(feed, failed);

  return (
    <Link
      href="/status"
      data-testid="hero-service-status"
      aria-label={`Service status: ${summary.label}. Open the full service status page.`}
      className="group mt-6 hidden items-center gap-3 rounded-2xl border border-white/15 bg-white/10 px-4 py-3 backdrop-blur-md transition hover:border-teal-300/50 hover:bg-white/15 focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-300 supports-[not(backdrop-filter:blur(0px))]:bg-emerald-950/70 lg:flex [.low-blur_&]:bg-emerald-950/80 [.low-blur_&]:backdrop-blur-none"
    >
      <span className="relative flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-white/10 ring-1 ring-white/20">
        <Activity className="h-4 w-4 text-teal-200" aria-hidden />
        <span
          className={`absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full ring-2 ring-emerald-950/60 ${summary.dot} ${
            summary.phase === "monitored" ? "motion-safe:animate-pulse" : ""
          }`}
          aria-hidden
        />
      </span>

      <span className="min-w-0 flex-1">
        <span className={`block truncate text-[13px] font-bold ${summary.text}`}>
          {summary.label}
        </span>
        <span className="block truncate text-[11px] leading-4 text-emerald-100/65">
          {summary.detail}
          {ago ? ` · ${ago}` : ""}
        </span>
      </span>

      <ArrowRight
        className="h-4 w-4 shrink-0 text-emerald-100/60 transition group-hover:translate-x-0.5 group-hover:text-white"
        aria-hidden
      />
    </Link>
  );
}
