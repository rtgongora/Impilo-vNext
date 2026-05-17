"use client";

/** /pages/[id] — page detail + page feed. */

import { use } from "react";
import { Newspaper, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFollowPage, useSocialFeed, useSocialPage } from "@/hooks/queries/useSocial";
import { TimelineComposer } from "@/components/social/TimelineComposer";
import { FeedList } from "@/components/social/FeedList";

export default function PageDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const pageQ = useSocialPage(id);
  const feedQ = useSocialFeed("page", id);
  const follow = useFollowPage();
  const p = pageQ.data;

  return (
    <AppLayout>
      <PageShell
        title={p?.name ?? "Page"}
        subtitle={p?.bio ?? "Page timeline"}
        icon={<Newspaper className="h-6 w-6" />}
      >
        {p && (
          <div className="impilo-surface-card mb-4 flex flex-wrap items-center justify-between gap-3 p-4">
            <div className="flex items-center gap-2">
              {p.verified && <ShieldCheck className="h-4 w-4 text-[color:var(--primary)]" />}
              <p className="text-xs uppercase tracking-wide text-[color:var(--text-muted)]">
                {p.kind} · {p.followerCount.toLocaleString()} followers
              </p>
            </div>
            <button
              onClick={() => follow.mutate({ id })}
              disabled={follow.isPending}
              className={`rounded-full px-3 py-1.5 text-xs font-semibold ${
                p.viewerFollows
                  ? "border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] text-[color:var(--text-secondary)]"
                  : "bg-[color:var(--primary)] text-white hover:bg-[color:var(--primary-hover)]"
              } disabled:opacity-50`}
            >
              {p.viewerFollows ? "Following" : follow.isPending ? "…" : "Follow page"}
            </button>
          </div>
        )}

        {p && p.adminIds && p.adminIds.length > 0 && (
          <div className="mb-4">
            <TimelineComposer defaultScope={{ pageId: id }} pages={p ? [p] : []} />
          </div>
        )}

        <FeedList
          posts={feedQ.data?.items}
          isLoading={feedQ.isLoading}
          error={feedQ.error}
          emptyTitle="No posts yet"
          emptyHint="Posts published by this page will appear here."
        />
      </PageShell>
    </AppLayout>
  );
}
