"use client";

/** /groups/[id] — Group detail + group feed. */

import { use } from "react";
import { Layers } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useSocialFeed, useSocialGroup } from "@/hooks/queries/useSocial";
import { TimelineComposer } from "@/components/social/TimelineComposer";
import { FeedList } from "@/components/social/FeedList";

export default function GroupDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const groupQ = useSocialGroup(id);
  const feedQ = useSocialFeed("group", id);
  const g = groupQ.data;
  const isMember = g?.viewerMembership && g.viewerMembership !== "none";

  return (
    <AppLayout>
      <PageShell title={g?.name ?? "Group"} subtitle={g?.description ?? "Group feed"} icon={<Layers className="h-6 w-6" />}>
        {g && isMember && (
          <div className="mb-4">
            <TimelineComposer defaultScope={{ groupId: id }} groups={g ? [g] : []} />
          </div>
        )}
        <FeedList
          posts={feedQ.data?.items}
          isLoading={feedQ.isLoading}
          error={feedQ.error}
          emptyTitle="No posts yet"
          emptyHint={isMember ? "Share an update with your group." : "Join the group to start posting."}
        />
      </PageShell>
    </AppLayout>
  );
}
