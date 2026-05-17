"use client";

/**
 * FeedList — Renders posts using FeedCard. Supports skeleton, error, empty.
 */

import type { SocialPost } from "@/hooks/queries/useSocial";
import { FeedCard } from "./FeedCard";

interface Props {
  posts?: SocialPost[];
  isLoading?: boolean;
  error?: unknown;
  emptyTitle?: string;
  emptyHint?: string;
}

export function FeedList({ posts, isLoading, error, emptyTitle = "Nothing here yet", emptyHint }: Props) {
  if (isLoading) return <FeedSkeleton />;
  if (error) {
    return (
      <div className="impilo-surface-card p-6 text-center">
        <p className="text-sm font-medium text-[color:var(--text-primary)]">Couldn’t load the feed</p>
        <p className="mt-1 text-xs text-[color:var(--text-muted)]">
          Try again in a moment. If the problem persists, contact your administrator.
        </p>
      </div>
    );
  }
  if (!posts || posts.length === 0) {
    return (
      <div className="impilo-surface-card p-8 text-center">
        <p className="text-sm font-semibold text-[color:var(--text-primary)]">{emptyTitle}</p>
        {emptyHint && <p className="mt-1 text-xs text-[color:var(--text-muted)]">{emptyHint}</p>}
      </div>
    );
  }
  return (
    <div className="space-y-3">
      {posts.map((post) => (
        <FeedCard key={post.id} post={post} />
      ))}
    </div>
  );
}

export function FeedSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="impilo-surface-card animate-pulse space-y-2 p-4">
          <div className="flex items-center gap-3">
            <span className="h-10 w-10 rounded-full bg-[color:var(--surface-soft)]" />
            <div className="flex-1 space-y-1.5">
              <span className="block h-3 w-1/3 rounded bg-[color:var(--surface-soft)]" />
              <span className="block h-2.5 w-1/4 rounded bg-[color:var(--surface-soft)]" />
            </div>
          </div>
          <span className="block h-3 w-full rounded bg-[color:var(--surface-soft)]" />
          <span className="block h-3 w-5/6 rounded bg-[color:var(--surface-soft)]" />
          <span className="block h-3 w-2/3 rounded bg-[color:var(--surface-soft)]" />
        </div>
      ))}
    </div>
  );
}
