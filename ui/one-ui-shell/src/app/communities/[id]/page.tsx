"use client";

/** /communities/[id] — community detail + community feed + composer. */

import { use } from "react";
import { Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useJoinCommunity,
  useLeaveCommunity,
  useSocialCommunity,
  useSocialFeed,
} from "@/hooks/queries/useSocial";
import { TimelineComposer } from "@/components/social/TimelineComposer";
import { FeedList } from "@/components/social/FeedList";

export default function CommunityDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const commQ = useSocialCommunity(id);
  const feedQ = useSocialFeed("community", id);
  const join = useJoinCommunity();
  const leave = useLeaveCommunity();
  const c = commQ.data;
  const isMember = c?.viewerMembership && c.viewerMembership !== "none";

  return (
    <AppLayout>
      <PageShell
        title={c?.name ?? "Community"}
        subtitle={c?.description ?? "Community feed"}
        icon={<Users className="h-6 w-6" />}
      >
        {c && (
          <div className="impilo-surface-card mb-4 flex flex-wrap items-center justify-between gap-3 p-4">
            <div>
              <p className="text-xs uppercase tracking-wide text-[color:var(--text-muted)]">
                {c.category} · {c.kind} · {c.memberCount.toLocaleString()} members
              </p>
            </div>
            {isMember ? (
              <button
                onClick={() => leave.mutate({ id })}
                disabled={leave.isPending}
                className="rounded-full border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-3 py-1.5 text-xs font-medium text-[color:var(--text-secondary)] hover:text-red-600 disabled:opacity-50"
              >
                {leave.isPending ? "Leaving…" : "Leave community"}
              </button>
            ) : (
              <button
                onClick={() => join.mutate({ id })}
                disabled={join.isPending}
                className="rounded-full bg-[color:var(--primary)] px-4 py-1.5 text-xs font-semibold text-white hover:bg-[color:var(--primary-hover)] disabled:opacity-50"
              >
                {join.isPending ? "Joining…" : "Join community"}
              </button>
            )}
          </div>
        )}

        {c && isMember && (
          <div className="mb-4">
            <TimelineComposer defaultScope={{ communityId: id }} communities={c ? [c] : []} />
          </div>
        )}

        <FeedList
          posts={feedQ.data?.items}
          isLoading={feedQ.isLoading}
          error={feedQ.error}
          emptyTitle="No posts yet"
          emptyHint={isMember ? "Be the first to share something with this community." : "Join the community to start posting."}
        />
      </PageShell>
    </AppLayout>
  );
}
