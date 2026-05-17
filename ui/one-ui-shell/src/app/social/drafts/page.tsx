"use client";

/** /social/drafts — drafts the actor saved. */
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FileEdit } from "lucide-react";
import { useSocialFeed } from "@/hooks/queries/useSocial";
import { FeedList } from "@/components/social/FeedList";

export default function DraftsPage() {
  const feedQ = useSocialFeed("drafts");
  return (
    <AppLayout>
      <PageShell title="Drafts" subtitle="Posts you started but haven’t published" icon={<FileEdit className="h-6 w-6" />}>
        <FeedList
          posts={feedQ.data?.items}
          isLoading={feedQ.isLoading}
          error={feedQ.error}
          emptyTitle="No drafts saved"
          emptyHint="Use the composer to save a draft."
        />
      </PageShell>
    </AppLayout>
  );
}
