"use client";

/** /social/saved — bookmarked posts. */
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { Bookmark } from "lucide-react";
import { useSocialFeed } from "@/hooks/queries/useSocial";
import { FeedList } from "@/components/social/FeedList";

export default function SavedPostsPage() {
  const feedQ = useSocialFeed("saved");
  return (
    <AppLayout>
      <PageShell title="Saved posts" subtitle="Posts you’ve bookmarked from across the timeline" icon={<Bookmark className="h-6 w-6" />}>
        <FeedList
          posts={feedQ.data?.items}
          isLoading={feedQ.isLoading}
          error={feedQ.error}
          emptyTitle="No saved posts"
          emptyHint="Bookmark a post to save it for later."
        />
      </PageShell>
    </AppLayout>
  );
}
