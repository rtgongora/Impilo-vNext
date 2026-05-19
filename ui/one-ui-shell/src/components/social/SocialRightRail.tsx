"use client";

/**
 * SocialRightRail — Right column with contextual widgets:
 *   - Suggested communities/pages/groups (from BFF suggestions)
 *   - Trending topics (deterministic, derived from feed topics)
 *   - Public health alerts placeholder (TODO: wire to Public Health Ops feed)
 *   - Upcoming events placeholder (TODO: wire to events service)
 */

import Link from "next/link";
import { AlertTriangle, CalendarClock, Sparkles, TrendingUp, Users } from "lucide-react";
import { useSocialSuggestions, type SocialPost } from "@/hooks/queries/useSocial";

interface Props {
  /** Posts currently rendered in the centre column — used to compute trending tags. */
  recentPosts?: SocialPost[];
}

export function SocialRightRail({ recentPosts = [] }: Props) {
  const suggestionsQ = useSocialSuggestions();
  const suggestions = suggestionsQ.data;

  const trending = computeTrendingTopics(recentPosts);

  return (
    <div className="space-y-4">
      {/* Suggested communities */}
      <section className="impilo-surface-card p-4">
        <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-[color:var(--text-primary)]">
          <Sparkles className="h-4 w-4 text-[color:var(--primary)]" />
          Suggested for you
        </h3>
        {suggestionsQ.isLoading ? (
          <SkeletonRows count={3} />
        ) : (
          <ul className="space-y-2.5">
            {(suggestions?.communities ?? []).slice(0, 3).map((c) => (
              <li key={c.id} className="flex items-center gap-2.5">
                <span
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-sm font-semibold text-white"
                  style={{ background: c.coverColor || "var(--primary)" }}
                >
                  {c.name.charAt(0)}
                </span>
                <div className="min-w-0 flex-1">
                  <Link
                    href={`/communities/${c.id}`}
                    className="block truncate text-sm font-medium text-[color:var(--text-primary)] hover:text-[color:var(--primary)]"
                  >
                    {c.name}
                  </Link>
                  <p className="truncate text-xs text-[color:var(--text-muted)]">
                    {c.memberCount.toLocaleString()} members · {c.category}
                  </p>
                </div>
              </li>
            ))}
            {(suggestions?.pages ?? []).slice(0, 2).map((p) => (
              <li key={p.id} className="flex items-center gap-2.5">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[color:var(--surface-soft)] text-[color:var(--text-secondary)]">
                  <Users className="h-4 w-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <Link
                    href={`/pages/${p.id}`}
                    className="block truncate text-sm font-medium text-[color:var(--text-primary)] hover:text-[color:var(--primary)]"
                  >
                    {p.name}
                  </Link>
                  <p className="truncate text-xs text-[color:var(--text-muted)]">
                    {p.followerCount.toLocaleString()} followers · {p.kind}
                  </p>
                </div>
              </li>
            ))}
            {!suggestions?.communities?.length && !suggestions?.pages?.length && (
              <li className="text-xs text-[color:var(--text-muted)]">
                No suggestions yet — explore <Link className="text-[color:var(--primary)]" href="/communities">communities</Link>.
              </li>
            )}
          </ul>
        )}
      </section>

      {/* Trending */}
      {trending.length > 0 && (
        <section className="impilo-surface-card p-4">
          <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-[color:var(--text-primary)]">
            <TrendingUp className="h-4 w-4 text-[color:var(--primary)]" />
            Trending topics
          </h3>
          <div className="flex flex-wrap gap-1.5">
            {trending.map((t) => (
              <Link
                key={t.tag}
                href={`/social?topic=${encodeURIComponent(t.tag)}`}
                className="rounded-full border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-2.5 py-1 text-xs text-[color:var(--text-secondary)] hover:border-[color:var(--primary-muted)] hover:bg-[color:var(--primary-soft)] hover:text-[color:var(--primary-hover)]"
              >
                #{t.tag} · {t.count}
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* Public health alerts (deterministic placeholder, clearly labelled) */}
      <section className="impilo-surface-card p-4">
        <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-[color:var(--text-primary)]">
          <AlertTriangle className="h-4 w-4 text-amber-500" />
          Public health updates
        </h3>
        <p className="text-xs text-[color:var(--text-muted)]">
          Live alerts will surface here from Public Health Operations and the Surveillance plane.
          <Link href="/wellness/public-health" className="ml-1 text-[color:var(--primary)]">
            Open Public Health
          </Link>
          .
        </p>
      </section>

      {/* Upcoming events */}
      <section className="impilo-surface-card p-4">
        <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-[color:var(--text-primary)]">
          <CalendarClock className="h-4 w-4 text-[color:var(--primary)]" />
          Upcoming
        </h3>
        <p className="text-xs text-[color:var(--text-muted)]">
          Outreaches, webinars, and training cohorts will appear here once you join a community.
        </p>
      </section>
    </div>
  );
}

function SkeletonRows({ count }: { count: number }) {
  return (
    <ul className="space-y-2.5">
      {Array.from({ length: count }).map((_, i) => (
        <li key={i} className="flex items-center gap-2.5">
          <span className="h-9 w-9 animate-pulse rounded-lg bg-[color:var(--surface-soft)]" />
          <div className="flex-1 space-y-1.5">
            <span className="block h-3 w-3/5 animate-pulse rounded bg-[color:var(--surface-soft)]" />
            <span className="block h-2.5 w-2/5 animate-pulse rounded bg-[color:var(--surface-soft)]" />
          </div>
        </li>
      ))}
    </ul>
  );
}

function computeTrendingTopics(posts: SocialPost[]): Array<{ tag: string; count: number }> {
  const map = new Map<string, number>();
  for (const p of posts) {
    for (const t of p.topics ?? []) {
      map.set(t, (map.get(t) ?? 0) + 1);
    }
  }
  return Array.from(map.entries())
    .map(([tag, count]) => ({ tag, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 8);
}
